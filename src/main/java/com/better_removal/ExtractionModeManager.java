package com.better_removal;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

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

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String FILE_NAME = "better-removal-modes.properties";

	private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
	private static final Map<UUID, ExtractionMode> MODES = new ConcurrentHashMap<>();

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

	public static ExtractionMode getMode(Player player) {
		return MODES.getOrDefault(player.getUUID(), ExtractionMode.OUTPUT);
	}

	public static void setMode(ServerPlayer player, ExtractionMode mode) {
		MODES.put(player.getUUID(), mode);
		save();
	}

	/**
	 * 生成切换提示文本，如：当前取出模式【取出输入槽】
	 * 前缀与括号用一种颜色，模式名用另一种高亮颜色。
	 * 使用 translatable 组件，客户端按各自语言解析。
	 */
	public static Component getModeMessage(ExtractionMode mode) {
		MutableComponent prefix = Component.translatable("better_removal.message.mode_prefix").withStyle(net.minecraft.ChatFormatting.YELLOW);
		MutableComponent open = Component.literal("【").withStyle(net.minecraft.ChatFormatting.AQUA);
		MutableComponent name = Component.translatable(mode.getTranslationKey()).withStyle(mode.getAccentColor());
		MutableComponent close = Component.literal("】").withStyle(net.minecraft.ChatFormatting.AQUA);
		return prefix.append(open).append(name).append(close);
	}

	/**
	 * 生成 /br now 提示：当前取出模式
	 */
	public static Component getCurrentModeMessage(ExtractionMode mode) {
		MutableComponent prefix = Component.translatable("better_removal.message.current_mode_prefix").withStyle(net.minecraft.ChatFormatting.GRAY);
		MutableComponent open = Component.literal("【").withStyle(net.minecraft.ChatFormatting.AQUA);
		MutableComponent name = Component.translatable(mode.getTranslationKey()).withStyle(mode.getAccentColor());
		MutableComponent close = Component.literal("】").withStyle(net.minecraft.ChatFormatting.AQUA);
		return prefix.append(open).append(name).append(close);
	}
}