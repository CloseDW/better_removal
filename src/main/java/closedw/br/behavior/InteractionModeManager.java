package closedw.br.behavior;

import closedw.br.BetterRemoval;
import closedw.br.mapping.MappingSessionManager;
import closedw.br.networking.ExtractionModeSetC2SPacket;
import closedw.br.networking.ExtractionModeSyncS2CPacket;
import closedw.br.OutputSlotExtractor;
import closedw.br.slot.SlotSet;


import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按玩家记录交互模式（行为=取出/放入/补货 + 槽位），并持久化到配置文件（服务器重启后仍保留）。
 * 配置文件位于 config/better-removal-modes.properties，键为玩家UUID，值为"ACTION:MODE"
 */
public final class InteractionModeManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(BetterRemoval.MOD_ID);
	private static final String FILE_NAME = "better-removal-modes.properties";

	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	private static final Map<UUID, InteractionMode> STATES = new ConcurrentHashMap<>();

	/** 客户端缓存的当前模式（由服务端通过 S2C 包同步） */
	private static volatile InteractionMode CLIENT_STATE = InteractionMode.DEFAULT;

	/** 客户端缓存的"主动探测是否可用"，模式滚轮据此决定是否多显示一个预设 */
	private static volatile boolean CLIENT_PROBE_AVAILABLE = false;

	/** 客户端缓存的"手动指认是否可用"，模式滚轮据此决定是否多显示一个预设 */
	private static volatile boolean CLIENT_MAPPING_AVAILABLE = false;

	static {
		load();
	}

	private InteractionModeManager() {
	}

	private static void load() {
		Properties props = new Properties();
		if (Files.exists(PATH)) {
			try (InputStream in = Files.newInputStream(PATH)) {
				props.load(in);
			}
			catch (IOException e) {
				LOGGER.warn("Failed to load extraction modes from {}", PATH, e);
			}
		}
		props.forEach((key, value) -> {
			InteractionMode state = parseState((String) value);
			if (state == null) {
				LOGGER.warn("Skipping invalid extraction mode entry: {}={}", key, value);
				return;
			}
			try {
				STATES.put(UUID.fromString((String) key), state);
			}
			catch (IllegalArgumentException e) {
				LOGGER.warn("Skipping extraction mode entry with invalid player UUID: {}={}", key, value);
			}
		});
	}

	/** 解析"ACTION:MODE"，兼容旧格式（仅槽位名 = 取出行为） */
	private static InteractionMode parseState(String value) {
		try {
			int split = value.indexOf(':');
			if (split >= 0) {
				return new InteractionMode(Behavior.valueOf(value.substring(0, split)), SlotSet.valueOf(value.substring(split + 1)));
			}
			return new InteractionMode(Behavior.EXTRACT, SlotSet.valueOf(value));
		}
		catch (Exception e) {
			return null;
		}
	}

	private static void save() {
		Properties props = new Properties();
		STATES.forEach((uuid, state) -> props.setProperty(uuid.toString(), state.behavior().name() + ":" + state.slots().name()));
		try {
			Files.createDirectories(PATH.getParent());
			try (OutputStream out = Files.newOutputStream(PATH)) {
				props.store(out, "Better Removal per-player modes");
			}
		}
		catch (IOException e) {
			LOGGER.error("Failed to save extraction modes to {}", PATH, e);
		}
	}

	public static InteractionMode getState(PlayerEntity player) {
		return STATES.getOrDefault(player.getUuid(), InteractionMode.DEFAULT);
	}

	/** 客户端缓存的当前模式（供 Jade客户端功能读取）。 */
	public static InteractionMode getClientState() {
		return CLIENT_STATE;
	}

	/** 客户端缓存的"主动探测是否可用"（供模式滚轮读取）。 */
	public static boolean isClientProbeAvailable() {
		return CLIENT_PROBE_AVAILABLE;
	}

	/** 客户端缓存的"手动指认是否可用"（供模式滚轮读取）。 */
	public static boolean isClientMappingAvailable() {
		return CLIENT_MAPPING_AVAILABLE;
	}

	public static void setClientState(InteractionMode state, boolean probeAvailable, boolean mappingAvailable) {
		CLIENT_STATE = state;
		CLIENT_PROBE_AVAILABLE = probeAvailable;
		CLIENT_MAPPING_AVAILABLE = mappingAvailable;
	}

	/**
	 * 主动探测是否可用：只看 {@code experimental_transfer_probe} 这一个开关。
	 * （早期版本还要求实验性总开关 {@code experimental_auto_detect}，那个总开关连同白名单自动探测一起删掉了。）
	 * 用 {@link OutputSlotExtractor#isExperimentalSwitchOn} 而不是 isContainerEnabled：
	 * 读不到配置时按"关闭"处理，维持"默认关闭"的约定。
	 */
	public static boolean isProbeAvailable() {
		return OutputSlotExtractor.isExperimentalSwitchOn("experimental_transfer_probe");
	}

	/**
	 * 手动指认是否可用：只看 {@code menu_mapping} 开关（实验性，默认关闭）。
	 */
	public static boolean isMappingAvailable() {
		return OutputSlotExtractor.isExperimentalSwitchOn("menu_mapping");
	}

	/** 重新向客户端同步模式与两个"可用性"标志（配置改完后由 /br reload 调用）。 */
	public static void refreshClient(ServerPlayerEntity player) {
		sendSync(player, getState(player));
	}

	/** 统一的同步出口：模式 + 主动探测可用 + 手动指认可用，一次下发。 */
	public static void sendSync(ServerPlayerEntity player, InteractionMode state) {
		ServerPlayNetworking.send(player, new ExtractionModeSyncS2CPacket(state, isProbeAvailable(), isMappingAvailable()));
	}

	/** 注册模式设置数据包的接收器，并处理玩家加入时的模式同步。 */
	public static void registerServerHandlers() {
		ServerPlayNetworking.registerGlobalReceiver(ExtractionModeSetC2SPacket.TYPE, (packet, player, responseSender) -> {
			InteractionMode state = setState(player, new InteractionMode(packet.behavior(), packet.slots()));
			player.sendMessage(getStateMessage(state), false);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			// 服务端在玩家加入时把修饰键状态重置为"未按下"（见 ExtractKeyStateManager），并重新同步模式
			sendSync(handler.player, getState(handler.player));
		});
	}

	/** 防御非法组合：放入预设只允许 input/fuel；补货与主动探测/手动指认固定全部（槽位范围由行为自己决定） */
	private static InteractionMode sanitize(InteractionMode state) {
		if (state.behavior() == Behavior.PROBE || state.behavior() == Behavior.MAPPING) {
			// 这两个行为都不使用槽位模式（与补货同理，槽位名不显示）
			return new InteractionMode(state.behavior(), SlotSet.ALL);
		}
		if (state.behavior() == Behavior.DEPOSIT && state.slots() != SlotSet.INPUT && state.slots() != SlotSet.FUEL) {
			return new InteractionMode(Behavior.DEPOSIT, SlotSet.INPUT);
		}
		if (state.behavior() == Behavior.RESTOCK && state.slots() != SlotSet.ALL) {
			return new InteractionMode(Behavior.RESTOCK, SlotSet.ALL);
		}
		return state;
	}

	public static InteractionMode setState(ServerPlayerEntity player, InteractionMode state) {
		state = sanitize(state);
		STATES.put(player.getUuid(), state);
		save();
		if (state.behavior() != Behavior.MAPPING) {
			// 切走指认模式就结束当前会话，免得客户端还挂着叠层标签
			MappingSessionManager.end(player);
		}
		sendSync(player, state);
		return state;
	}

	/**
	 * 生成模式提示文本，如：当前模式【取出 · 输出槽】；补货/主动探测/手动指认没有槽位预设，只显示行为名
	 * 前缀直接下发 {@code Text.translatable}，由客户端按自己的语言渲染——
	 * 早期版本在服务端就 {@code getString()} 拍平成字符串，于是中文服配英文客户端也会显示中文。
	 */
	public static Text getStateMessage(InteractionMode state) {
		MutableText prefix = Text.translatable("better-removal.message.state_prefix")
				.setStyle(Style.EMPTY.withColor(Formatting.YELLOW));
		MutableText open = Text.literal("【").setStyle(Style.EMPTY.withColor(Formatting.AQUA));
		MutableText action = Text.translatable(state.behavior().getTranslationKey())
				.setStyle(Style.EMPTY.withColor(state.behavior().getAccentColor()));
		if (!state.behavior().hasSlotMode()) {
			return prefix.append(open).append(action)
					.append(Text.literal("】").setStyle(Style.EMPTY.withColor(Formatting.AQUA)));
		}
		MutableText middle = Text.literal(" · ").setStyle(Style.EMPTY.withColor(Formatting.AQUA));
		MutableText slot = Text.translatable(state.slots().getTranslationKey())
				.setStyle(Style.EMPTY.withColor(state.slots().getAccentColor()));
		MutableText close = Text.literal("】").setStyle(Style.EMPTY.withColor(Formatting.AQUA));
		return prefix.append(open).append(action).append(middle).append(slot).append(close);
	}
}
