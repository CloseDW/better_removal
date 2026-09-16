package closedw.br;

import closedw.br.command.BetterRemovalCommand;
import closedw.br.container.ContainerRegistry;
import closedw.br.experimental.SlotRules;
import closedw.br.ftbultimine.FTBUltimineSupport;
import closedw.br.networking.ExtractKeyStateManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterRemoval implements ModInitializer {
	public static final String MOD_ID = "better-removal";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Loading Better Removal");

		// 容器注册表：注册所有容器类型并做启动自检
		ContainerRegistry.init();
		OutputSlotExtractor.register();
		// 实验性：读取手写槽位规则
		SlotRules.load();
		ExtractKeyStateManager.registerServerHandlers();
		ExtractionModeManager.registerServerHandlers();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				BetterRemovalCommand.register(dispatcher));

		// FTB Ultimine 反射联动状态
		if (FTBUltimineSupport.isInstalled()) {
			if (FTBUltimineSupport.isLoaded()) {
				LOGGER.info("FTB Ultimine link ready");
			}
			else {
				LOGGER.warn("FTB Ultimine is installed but the reflection link is unavailable, chain extraction/deposit is disabled");
			}
		}
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
