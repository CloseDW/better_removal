package closedw.br.mapping;

import closedw.br.BetterRemoval;
import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionModeManager;
import closedw.br.container.menu.MenuMap;
import closedw.br.container.menu.MenuMapStore;
import closedw.br.container.menu.MenuResolver;
import closedw.br.container.rule.SlotRules;
import closedw.br.networking.MappingFeedbackS2CPacket;
import closedw.br.networking.MappingUpdateC2SPacket;
import closedw.br.slot.SlotRole;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手动指认的服务端会话。
 * 流程：玩家在「手动指认」模式下按住修饰键右击容器 → 本类记下目标方块并放行原版交互（GUI 正常打开）
 * → 玩家在界面里按住修饰键滚轮给槽位打标签 → 客户端把「菜单槽位索引 + 角色」发过来
 * → 服务端用自己那份 {@code player.currentScreenHandler} 校验，跑一次离线重建自检，通过就落盘。
 * 每次改动都落盘（文件很小），所以客户端崩了也不会丢指认结果。
 */
public final class MappingSessionManager {

	public static final String ERROR_NO_SESSION = "better-removal.mapping.error.no_session";
	public static final String ERROR_NO_BLOCK = "better-removal.mapping.error.no_block";
	public static final String ERROR_NO_MENU = "better-removal.mapping.error.no_menu";
	public static final String OK = "better-removal.mapping.ok";
	public static final String CLEARED = "better-removal.mapping.cleared";

	private static final Logger LOGGER = BetterRemoval.LOGGER;
	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private static final class Session {
		final BlockPos pos;
		final String match;
		final int menuSize;
		/** 菜单槽位索引 → 角色 */
		final Map<Integer, SlotRole> roles = new LinkedHashMap<>();
		/** 离线重建自检是否已经跑过（每个会话只需一次）。 */
		boolean rebuildChecked;

		Session(BlockPos pos, String match, int menuSize) {
			this.pos = pos;
			this.match = match;
			this.menuSize = menuSize;
		}
	}

	private MappingSessionManager() {
	}

	/** 注册指认改动包的接收器（由 {@code BetterRemoval} 在初始化时调用）。 */
	public static void registerServerHandlers() {
		ServerPlayNetworking.registerGlobalReceiver(MappingUpdateC2SPacket.TYPE, (packet, player, responseSender) ->
				setRole(player, packet.menuIndex(), packet.role()));
	}

	/**
	 * 开始（或继续）对一个容器的指认。玩家右击容器时由 {@code OutputSlotExtractor} 调用。
	 * 这里会先把菜单构造一次：① 拿到菜单槽位总数用于校验；② 提前发现"这个容器没法在没有 GUI 的
	 * 情况下重建菜单"，让玩家立刻知道而不是白标一通。
	 */
	public static void begin(ServerPlayerEntity player, BlockPos pos) {
		if (player == null || pos == null) {
			return;
		}
		BlockEntity blockEntity = player.getWorld().getBlockEntity(pos);
		if (blockEntity == null) {
			// 压根不是容器方块（没有方块实体）：静默放过。
			// 指认模式下点任何方块都会走到这里，回一句聊天会变成刷屏。
			return;
		}
		if (!(blockEntity instanceof NamedScreenHandlerFactory)) {
			fail(player, blockEntity, MenuResolver.ERROR_NOT_PROVIDER);
			return;
		}
		ScreenHandler probe = MenuResolver.open(blockEntity, player);
		if (probe == null) {
			fail(player, blockEntity, MenuResolver.ERROR_MENU_FAILED);
			return;
		}
		int menuSize = probe.slots.size();
		MenuResolver.close(probe, player);

		String match = matchKey(blockEntity);
		Session current = SESSIONS.get(player.getUuid());
		if (current != null && current.match.equals(match)) {
			// 同一个容器再右击一次：继续已有点法，不重置
			sendFeedback(player, current, true, OK);
			return;
		}

		Session session = new Session(pos, match, menuSize);
		MenuMap stored = MenuMapStore.findExact(match);
		if (stored != null && stored.menuSize() == menuSize) {
			// 二次编辑：以已保存的映射为起点
			session.roles.putAll(stored.byMenuIndex());
		}
		SESSIONS.put(player.getUuid(), session);
		LOGGER.info("[menu-map] {} started mapping {} (menuSize={})",
				player.getName().getString(), match, menuSize);
		// 成功进入会话不往聊天框打任何东西：状态条（选区块 + 选区名）和光标本身就是提示
		sendFeedback(player, session, true, OK);
	}

	/**
	 * 指认失败：聊天框只回一行「方块 ID：原因」。
	 */
	private static void fail(ServerPlayerEntity player, BlockEntity blockEntity, String reasonKey) {
		Identifier id = SlotRules.blockId(blockEntity);
		String name = id == null ? blockEntity.getClass().getSimpleName() : id.toString();
		player.sendMessage(Text.translatable("better-removal.mapping.failed", name,
				Text.translatable(reasonKey)), false);
		LOGGER.info("[menu-map] cannot map {}: {}", name, reasonKey);
	}

	/**
	 * 给一个菜单槽位打标签。菜单槽位索引以玩家当前打开的界面为准。
	 */
	public static void setRole(ServerPlayerEntity player, int menuIndex, SlotRole role) {
		if (player == null) {
			return;
		}
		Session session = SESSIONS.get(player.getUuid());
		if (session == null) {
			reject(player, null, ERROR_NO_SESSION);
			return;
		}
		if (InteractionModeManager.getState(player).behavior() != Behavior.MAPPING) {
			end(player);
			return;
		}
		if (!InteractionModeManager.isMappingAvailable()) {
			end(player);
			return;
		}
		BlockEntity blockEntity = player.getWorld().getBlockEntity(session.pos);
		if (blockEntity == null) {
			reject(player, session, ERROR_NO_BLOCK);
			return;
		}
		ScreenHandler menu = player.currentScreenHandler;
		if (menu == null || menu == player.playerScreenHandler) {
			reject(player, session, ERROR_NO_MENU);
			return;
		}
		if (menu.slots.size() != session.menuSize) {
			reject(player, session, MenuResolver.ERROR_SIZE_CHANGED);
			return;
		}

		if (!MenuResolver.isUsable(menu, player, menuIndex)) {
			reject(player, session, MenuResolver.ERROR_SLOT_UNUSABLE);
			return;
		}

		SlotRole previous = session.roles.get(menuIndex);
		if (role == null || role == SlotRole.NONE) {
			session.roles.remove(menuIndex);
		}
		else {
			session.roles.put(menuIndex, role);
		}

		// 离线重建自检每次会话只跑一遍：它验证的是"这个容器的菜单能不能在没有 GUI 的情况下重建"
		MenuMap candidate = new MenuMap(session.match, session.menuSize, MenuMap.toRoleLists(session.roles));
		if (!session.rebuildChecked) {
			String failure = MenuResolver.check(candidate, blockEntity, player);
			if (failure != null) {
				if (previous == null) {
					session.roles.remove(menuIndex);
				}
				else {
					session.roles.put(menuIndex, previous);
				}
				reject(player, session, failure);
				return;
			}
			session.rebuildChecked = true;
		}

		MenuMapStore.putOrReplace(candidate);
		LOGGER.info("[menu-map] {} set menu slot {} of {} to {}", player.getName().getString(),
				menuIndex, session.match, role == null ? SlotRole.NONE : role);
		sendFeedback(player, session, true, candidate.isEmpty() ? CLEARED : OK);
	}

	/** 结束会话并让客户端清掉叠层标签。 */
	public static void end(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		// 落盘是限流的，会话结束时把攒着的改动补写下去
		MenuMapStore.flush();
		// F20 靠 mixin 注入 closeHandledScreen()；如果这条日志在关界面时没出现，就是会话没结束的根因。
		Session removedSession = SESSIONS.remove(player.getUuid());
		if (removedSession == null) {
			return;
		}
		safeSend(player, new MappingFeedbackS2CPacket(false, true, "", "", 0, List.of()));
	}

	private static void reject(ServerPlayerEntity player, Session session, String messageKey) {
		if (session == null) {
			message(player, messageKey);
			safeSend(player, new MappingFeedbackS2CPacket(false, false, messageKey, "", 0, List.of()));
			return;
		}
		sendFeedback(player, session, false, messageKey);
	}

	private static void sendFeedback(ServerPlayerEntity player, Session session, boolean ok, String messageKey) {
		List<MappingFeedbackS2CPacket.RoleEntry> entries = new ArrayList<>();
		session.roles.forEach((slot, role) -> entries.add(new MappingFeedbackS2CPacket.RoleEntry(slot, role)));
		safeSend(player, new MappingFeedbackS2CPacket(true, ok, messageKey, session.match, session.menuSize, entries));
	}

	private static void safeSend(ServerPlayerEntity player, MappingFeedbackS2CPacket packet) {
		try {
			if (player.networkHandler != null) {
				ServerPlayNetworking.send(player, packet);
			}
		}
		catch (Throwable ignored) {
			// 玩家正在断开连接之类，忽略
		}
	}

	private static void message(ServerPlayerEntity player, String messageKey) {
		player.sendMessage(Text.translatable(messageKey), false);
	}

	/** 匹配串：优先方块 ID，取不到时退回"@方块实体类名"（与探测输出保持一致）。 */
	private static String matchKey(BlockEntity blockEntity) {
		Identifier id = SlotRules.blockId(blockEntity);
		return id != null ? id.toString() : "@" + blockEntity.getClass().getName().toLowerCase(Locale.ROOT);
	}
}
