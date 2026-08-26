package common;

import common.command.BetterRemovalCommand;
import common.networking.ExtractKeyStateManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterRemoval implements ModInitializer {
	public static final String MOD_ID = "better-removal";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Loading Better Removal");

		OutputSlotExtractor.register();
		ExtractKeyStateManager.registerServerHandlers();
		ExtractionModeManager.registerServerHandlers();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				BetterRemovalCommand.register(dispatcher));
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}