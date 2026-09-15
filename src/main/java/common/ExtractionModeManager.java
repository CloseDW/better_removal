package common;

import common.networking.ExtractionModeSetC2SPayload;
import common.networking.ExtractionModeSyncS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
 * 按玩家记录交互模式（行为+槽位），并持久化到配置文件（服务器重启后仍保留）。
 * 兼容旧格式（仅槽位名，视为取出行为）。
 */
public final class ExtractionModeManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(BetterRemoval.MOD_ID);
	private static final String FILE_NAME = "better-removal-modes.properties";

	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	private static final Map<UUID, ModeState> STATES = new ConcurrentHashMap<>();

	/** 客户端缓存的当前模式（由服务端通过 S2C 包同步） */
	private static volatile ModeState CLIENT_STATE = ModeState.DEFAULT;

	/** 客户端缓存的"主动探测是否可用"（由服务端通过 S2C 包同步） */
	private static volatile boolean CLIENT_PROBE_AVAILABLE = false;

	static {
		load();
	}

	private ExtractionModeManager() {
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
			ModeState state = parseState((String) value);
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

	private static ModeState parseState(String value) {
		try {
			int split = value.indexOf(':');
			if (split >= 0) {
				return new ModeState(ExtractionAction.valueOf(value.substring(0, split)), ExtractionMode.valueOf(value.substring(split + 1)));
			}
			return new ModeState(ExtractionAction.EXTRACT, ExtractionMode.valueOf(value));
		}
		catch (Exception e) {
			return null;
		}
	}

	private static void save() {
		Properties props = new Properties();
		STATES.forEach((uuid, state) -> props.setProperty(uuid.toString(), state.action().name() + ":" + state.mode().name()));
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

	public static ModeState getState(PlayerEntity player) {
		return STATES.getOrDefault(player.getUuid(), ModeState.DEFAULT);
	}

	public static ModeState getClientState() {
		return CLIENT_STATE;
	}

	public static void setClientState(ModeState state) {
		CLIENT_STATE = state;
	}

	public static boolean isClientProbeAvailable() {
		return CLIENT_PROBE_AVAILABLE;
	}

	public static void setClientState(ModeState state, boolean probeAvailable) {
		CLIENT_STATE = state;
		CLIENT_PROBE_AVAILABLE = probeAvailable;
	}

	/** 主动探测是否可用：实验性总开关 + 主动探测开关都打开。 */
	public static boolean isProbeAvailable() {
		return OutputSlotExtractor.isExperimentalEnabled()
				&& OutputSlotExtractor.isContainerEnabled("experimental_transfer_probe");
	}

	/** 重新向客户端同步模式与"主动探测是否可用"。 */
	public static void refreshClient(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player,
				new ExtractionModeSyncS2CPayload(getState(player).action(), getState(player).mode(), isProbeAvailable()));
	}

	/** 防御非法组合：放入预设只允许 input/fuel；补货固定全部；主动探测不使用槽位模式 */
	private static ModeState sanitize(ModeState state) {
		if (state.action() == ExtractionAction.PROBE) {
			return new ModeState(ExtractionAction.PROBE, ExtractionMode.ALL);
		}
		if (state.action() == ExtractionAction.DEPOSIT && state.mode() != ExtractionMode.INPUT && state.mode() != ExtractionMode.FUEL) {
			return new ModeState(ExtractionAction.DEPOSIT, ExtractionMode.INPUT);
		}
		if (state.action() == ExtractionAction.RESTOCK && state.mode() != ExtractionMode.ALL) {
			return new ModeState(ExtractionAction.RESTOCK, ExtractionMode.ALL);
		}
		return state;
	}

	/** 注册网络包接收器，并处理玩家加入时的模式同步。 */
	public static void registerServerHandlers() {
		PayloadTypeRegistry.playC2S().register(ExtractionModeSetC2SPayload.ID, ExtractionModeSetC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ExtractionModeSyncS2CPayload.ID, ExtractionModeSyncS2CPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ExtractionModeSetC2SPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			ModeState state = setState(player, new ModeState(payload.action(), payload.mode()));
			player.sendMessage(getStateMessage(state), false);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				ServerPlayNetworking.send(handler.player,
						new ExtractionModeSyncS2CPayload(getState(handler.player).action(), getState(handler.player).mode(), isProbeAvailable())));
	}

	public static ModeState setState(ServerPlayerEntity player, ModeState state) {
		state = sanitize(state);
		STATES.put(player.getUuid(), state);
		save();
		ServerPlayNetworking.send(player,
				new ExtractionModeSyncS2CPayload(state.action(), state.mode(), isProbeAvailable()));
		return state;
	}

	/**
	 * 生成模式提示文本，如：当前模式【取出 · 输出槽】
	 */
	public static Text getStateMessage(ModeState state) {
		MutableText prefix = Text.translatable("better-removal.message.state_prefix")
				.setStyle(Style.EMPTY.withColor(Formatting.YELLOW));
		MutableText open = Text.literal("【").setStyle(Style.EMPTY.withColor(Formatting.AQUA));
		MutableText action = Text.translatable(state.action().getTranslationKey())
				.setStyle(Style.EMPTY.withColor(state.action().getAccentColor()));
		if (!state.action().hasSlotMode()) {
			return prefix.append(open).append(action)
					.append(Text.literal("】").setStyle(Style.EMPTY.withColor(Formatting.AQUA)));
		}
		MutableText middle = Text.literal(" · ").setStyle(Style.EMPTY.withColor(Formatting.AQUA));
		MutableText slot = Text.translatable(state.mode().getTranslationKey())
				.setStyle(Style.EMPTY.withColor(state.mode().getAccentColor()));
		MutableText close = Text.literal("】").setStyle(Style.EMPTY.withColor(Formatting.AQUA));
		return prefix.append(open).append(action).append(middle).append(slot).append(close);
	}
}
