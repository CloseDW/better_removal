package closedw.br.container.menu;

import closedw.br.BetterRemoval;
import closedw.br.slot.SlotRole;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 菜单槽位的定位与自检。
 */
public final class MenuResolver {

	/** 失败原因的翻译键（由服务端判定、客户端显示）。 */
	public static final String ERROR_NOT_PROVIDER = "better-removal.mapping.error.not_provider";
	public static final String ERROR_MENU_FAILED = "better-removal.mapping.error.menu_failed";
	public static final String ERROR_SIZE_CHANGED = "better-removal.mapping.error.size_changed";
	public static final String ERROR_SLOT_UNUSABLE = "better-removal.mapping.error.slot_unusable";

	private static final Logger LOGGER = BetterRemoval.LOGGER;
	private static final Map<String, String> LAST_LOGGED = new ConcurrentHashMap<>();

	private MenuResolver() {
	}

	/**
	 * 构造一个临时菜单。失败（不是 NamedScreenHandlerFactory、构造抛异常）返回 null。
	 * containerId 用 0：这个菜单从不注册给玩家，所以它和玩家自己的背包菜单不会冲突。
	 * <p>
	 * <b>客户端也允许构造</b>：Jade 的「放入预览」必须在客户端判断"这个物品放不放得进这些槽位"，
	 * 而菜单槽位（手动指认映射）只有拿到菜单才能问 {@code Slot#canInsert} 与剩余容量。
	 * 客户端只用它做只读与模拟（{@code insert(..., simulate = true)}），不会真写容器——
	 * 真正的写操作全在服务端（{@code UseBlockCallback} 在客户端第一行就返回了）。
	 */
	public static ScreenHandler open(BlockEntity blockEntity, PlayerEntity player) {
		if (blockEntity == null || player == null) {
			return null;
		}
		if (!(blockEntity instanceof NamedScreenHandlerFactory provider)) {
			return null;
		}
		try {
			return provider.createMenu(0, player.getInventory(), player);
		}
		catch (Throwable t) {
			logOnce(blockEntity, "menu construction failed: " + t);
			return null;
		}
	}

	/** 关闭临时菜单，与真正的开/关 GUI 对称。 */
	public static void close(ScreenHandler menu, PlayerEntity player) {
		if (menu == null) {
			return;
		}
		try {
			menu.onClosed(player);
		}
		catch (Throwable ignored) {
			// 关闭钩子失败不影响已经完成的物品移动
		}
	}

	/**
	 * 该菜单槽位能不能被本模组操作：索引在范围内、不是隐藏/假槽位、也不是玩家背包槽位。
	 */
	public static boolean isUsable(ScreenHandler menu, PlayerEntity player, int menuIndex) {
		if (menu == null || menuIndex < 0 || menuIndex >= menu.slots.size()) {
			return false;
		}
		try {
			Slot slot = menu.getSlot(menuIndex);
			if (slot == null || isFakeSlot(slot)) {
				return false;
			}
			return player == null || slot.inventory != player.getInventory();
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 1.20.1 没有 {@code Slot#isFake()}（1.21 才加入，用于"合成预览/模板"这类不承载真实物品的槽位）。
	 * 1.20.1 的原版容器菜单里不存在这种槽位——合成预览由 {@code CraftingResultSlot} 这类真实槽位承担，
	 * 隐藏槽位则靠 {@code isEnabled()} 表达。所以这里一律返回 false；
	 * 真正的"看不见的槽位"由客户端选择悬停槽位时的 {@code isEnabled()} 判定拦住（见 {@code client/MappingSession}）。
	 */
	private static boolean isFakeSlot(Slot slot) {
		return false;
	}

	/**
	 * 离线重建自检：拿真实容器把一条映射完整解析一遍（含槽位可用性与菜单槽位总数校验）。
	 * <p>
	 * 每次指认改动都会跑一遍，通过才落盘——这样"保存成功但用不了"不会发生：
	 * 不能在没有 GUI 的情况下重建菜单的容器，会在指认阶段就被拒绝。
	 *
	 * @return null 表示通过；否则是失败原因的翻译键
	 */
	public static String check(MenuMap map, BlockEntity blockEntity, PlayerEntity player) {
		if (map == null) {
			return ERROR_MENU_FAILED;
		}
		if (!(blockEntity instanceof NamedScreenHandlerFactory)) {
			return ERROR_NOT_PROVIDER;
		}
		ScreenHandler menu = open(blockEntity, player);
		if (menu == null) {
			return ERROR_MENU_FAILED;
		}
		try {
			if (menu.slots.size() != map.menuSize()) {
				return ERROR_SIZE_CHANGED;
			}
			for (SlotRole role : List.of(SlotRole.INPUT, SlotRole.FUEL, SlotRole.OUTPUT)) {
				for (int index : map.slots(role)) {
					if (!isUsable(menu, player, index)) {
						return ERROR_SLOT_UNUSABLE;
					}
				}
			}
			return null;
		}
		catch (Throwable t) {
			return ERROR_MENU_FAILED;
		}
		finally {
			close(menu, player);
		}
	}

	/**
	 * 取一个服务端玩家用来构造菜单代理。菜单构造需要一个玩家实例，语义上谁都可以
	 * （只用来构造菜单、不做任何写操作），取离方块最近的玩家最接近"正在操作这台机器的人"。
	 */
	public static PlayerEntity probePlayer(World world, BlockPos pos) {
		if (!(world instanceof ServerWorld serverWorld) || pos == null) {
			return null;
		}
		PlayerEntity best = null;
		int bestDistance = Integer.MAX_VALUE;
		try {
			for (ServerPlayerEntity candidate : serverWorld.getPlayers()) {
				BlockPos candidatePos = candidate.getBlockPos();
				int distance = Math.abs(candidatePos.getX() - pos.getX())
						+ Math.abs(candidatePos.getY() - pos.getY())
						+ Math.abs(candidatePos.getZ() - pos.getZ());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = candidate;
				}
			}
		}
		catch (Throwable t) {
			return null;
		}
		return best;
	}

	private static void logOnce(BlockEntity blockEntity, String message) {
		String key = blockEntity.getClass().getName();
		if (message.equals(LAST_LOGGED.put(key, message))) {
			return;
		}
		LOGGER.info("[menu-map] {}: {}", key, message);
	}
}
