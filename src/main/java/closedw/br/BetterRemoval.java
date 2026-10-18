package closedw.br;

import closedw.br.behavior.InteractionModeManager;
import closedw.br.command.BetterRemovalCommand;
import closedw.br.config.BetterRemovalConfig;
import closedw.br.container.menu.MenuMapStore;
import closedw.br.container.rule.SlotRuleRegistry;
import closedw.br.container.support.ContainerRegistry;
import closedw.br.ftbultimine.FTBUltimineSupport;
import closedw.br.mapping.MappingSessionManager;
import closedw.br.networking.ExtractKeyStateManager;


import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

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

		// 容器注册表：注册所有容器类型
		ContainerRegistry.init();
		OutputSlotExtractor.register();
		// 槽位规则：读取声明式规则（内置 json / 其它模组自声明）并做自检
		SlotRuleRegistry.load();
		// 手动指认映射：config/better-removal/menu-maps.json + 各模组 jar 内同名文件
		MenuMapStore.load();
		// 规则里的容器开关由规则推导，所以要在规则读完之后再登记进配置
		BetterRemovalConfig.get().registerRuleContainers(
				SlotRuleRegistry.configKeys(), SlotRuleRegistry.configGroups());
		// 分组变化后，Configured 的界面缓存必须作废：每次规则重载都重新登记一遍容器开关
		SlotRuleRegistry.addReloadListener(count -> BetterRemovalConfig.get().registerRuleContainers(
				SlotRuleRegistry.configKeys(), SlotRuleRegistry.configGroups()));
		// 容器注册自检必须在这之后跑：配置项建好之前检查"配置键是否存在"会全部误报
		ContainerRegistry.validate();
		// 方块 ID 的存在性校验要等注册表冻结（onInitialize 阶段各模组的方块还在注册中途），
		// 所以放到服务器启动时再跑
		ServerLifecycleEvents.SERVER_STARTING.register(server -> SlotRuleRegistry.revalidateBlockIds());
		ExtractKeyStateManager.registerServerHandlers();
		InteractionModeManager.registerServerHandlers();
		MappingSessionManager.registerServerHandlers();
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
