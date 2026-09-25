package closedw.br.behavior;

import closedw.br.BetterRemoval;
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

	public static void setClientState(InteractionMode state, boolean probeAvailable) {
		CLIENT_STATE = state;
		CLIENT_PROBE_AVAILABLE = probeAvailable;
	}

	/**
	 * 主动探测是否可用：实验性总开关 + 主动探测开关（experimental_transfer_probe）都打开。
	 * 未安装 Configured 时读不到开关，按不可用处理。
	 */
	public static boolean isProbeAvailable() {
		return OutputSlotExtractor.isExperimentalEnabled()
				&& OutputSlotExtractor.isContainerEnabled("experimental_transfer_probe");
	}

	/** 重新向客户端同步模式与"主动探测是否可用"（配置改完后由 /br reload 调用）。 */
	public static void refreshClient(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player, new ExtractionModeSyncS2CPacket(getState(player), isProbeAvailable()));
	}

	/** 注册模式设置数据包的接收器，并处理玩家加入时的模式同步。 */
	public static void registerServerHandlers() {
		ServerPlayNetworking.registerGlobalReceiver(ExtractionModeSetC2SPacket.TYPE, (packet, player, responseSender) -> {
			InteractionMode state = setState(player, new InteractionMode(packet.behavior(), packet.slots()));
			player.sendMessage(getStateMessage(state), false);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayNetworking.send(handler.player, new ExtractionModeSyncS2CPacket(getState(handler.player), isProbeAvailable()));
		});
	}

	/** 防御非法组合：放入预设只允许 input/fuel；补货固定全部（槽位范围由补货行为决定） */
	private static InteractionMode sanitize(InteractionMode state) {
		if (state.behavior() == Behavior.PROBE) {
			// 主动探测不使用槽位模式（与补货同理，槽位名不显示）
			return new InteractionMode(Behavior.PROBE, SlotSet.ALL);
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
		ServerPlayNetworking.send(player, new ExtractionModeSyncS2CPacket(state, isProbeAvailable()));
		return state;
	}

	/**
	 * 生成模式提示文本，如：当前模式【取出 · 输出槽】；补货没有槽位预设，只显示行为名
	 */
	public static Text getStateMessage(InteractionMode state) {
		MutableText prefix = Text.literal(Text.translatable("better-removal.message.state_prefix").getString())
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
