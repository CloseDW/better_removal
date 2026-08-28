package common;

import common.networking.ExtractionModeCycleC2SPayload;
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
 * 按玩家记录取出模式，并持久化到配置文件（服务器重启后仍保留）。
 * 配置文件位于 config/better-removal-modes.properties，键为玩家UUID，值为模式名。
 */
public final class ExtractionModeManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(BetterRemoval.MOD_ID);
	private static final String FILE_NAME = "better-removal-modes.properties";

	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	private static final Map<UUID, ExtractionMode> MODES = new ConcurrentHashMap<>();

	/** 客户端缓存的当前模式（由服务端通过 S2C 包同步） */
	private static volatile ExtractionMode CLIENT_MODE = ExtractionMode.OUTPUT;

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
			try {
				MODES.put(UUID.fromString((String) key), ExtractionMode.valueOf((String) value));
			}
			catch (Exception e) {
				LOGGER.warn("Skipping invalid extraction mode entry: {}={}", key, value);
			}
		});
	}

	private static void save() {
		Properties props = new Properties();
		MODES.forEach((uuid, mode) -> props.setProperty(uuid.toString(), mode.name()));
		try {
			Files.createDirectories(PATH.getParent());
			try (OutputStream out = Files.newOutputStream(PATH)) {
				props.store(out, "Better Removal per-player extraction modes");
			}
		}
		catch (IOException e) {
			LOGGER.error("Failed to save extraction modes to {}", PATH, e);
		}
	}

	public static ExtractionMode getMode(PlayerEntity player) {
		return MODES.getOrDefault(player.getUuid(), ExtractionMode.OUTPUT);
	}

	/**
	 * 客户端缓存的当前模式（供 Jade 联动等客户端功能读取）。
	 */
	public static ExtractionMode getClientMode() {
		return CLIENT_MODE;
	}

	public static void setClientMode(ExtractionMode mode) {
		CLIENT_MODE = mode;
	}

	/**
	 * 注册按键切换数据包的接收器：循环切换到下一个模式并回发提示。
	 * 并处理玩家加入时的模式同步。
	 */
	public static void registerServerHandlers() {
		PayloadTypeRegistry.playC2S().register(ExtractionModeCycleC2SPayload.ID, ExtractionModeCycleC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ExtractionModeSyncS2CPayload.ID, ExtractionModeSyncS2CPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ExtractionModeCycleC2SPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			ExtractionMode next = getMode(player).next();
			setMode(player, next);
			player.sendMessage(getModeMessage(next), false);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerPlayNetworking.send(handler.player, new ExtractionModeSyncS2CPayload(getMode(handler.player))));
	}

	public static void setMode(ServerPlayerEntity player, ExtractionMode mode) {
		MODES.put(player.getUuid(), mode);
		save();
		ServerPlayNetworking.send(player, new ExtractionModeSyncS2CPayload(mode));
	}

	/**
	 * 生成切换提示文本
	 * 前缀与括号用一种颜色，模式名用另一种高亮颜色。
	 */
	public static Text getModeMessage(ExtractionMode mode) {
		MutableText prefix = Text.literal(Text.translatable("better-removal.message.mode_prefix").getString())
				.setStyle(Style.EMPTY.withColor(Formatting.YELLOW));
		MutableText open = Text.literal("【").setStyle(Style.EMPTY.withColor(Formatting.AQUA));
		MutableText name = Text.translatable(mode.getTranslationKey())
				.setStyle(Style.EMPTY.withColor(mode.getAccentColor()));
		MutableText close = Text.literal("】").setStyle(Style.EMPTY.withColor(Formatting.AQUA));
		return prefix.append(open).append(name).append(close);
	}

	/**
	 * 生成 /br now 提示：当前取出模式
	 */
	public static Text getCurrentModeMessage(ExtractionMode mode) {
		MutableText prefix = Text.literal(Text.translatable("better-removal.message.current_mode_prefix").getString())
				.setStyle(Style.EMPTY.withColor(Formatting.GRAY));
		MutableText open = Text.literal("【").setStyle(Style.EMPTY.withColor(Formatting.AQUA));
		MutableText name = Text.translatable(mode.getTranslationKey())
				.setStyle(Style.EMPTY.withColor(mode.getAccentColor()));
		MutableText close = Text.literal("】").setStyle(Style.EMPTY.withColor(Formatting.AQUA));
		return prefix.append(open).append(name).append(close);
	}
}