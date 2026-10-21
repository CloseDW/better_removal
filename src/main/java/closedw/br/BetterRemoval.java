package closedw.br;

import closedw.br.behavior.InteractionModeManager;
import closedw.br.command.BetterRemovalCommand;
import closedw.br.config.BetterRemovalConfig;
import closedw.br.container.menu.MenuMapStore;
import closedw.br.container.rule.SlotRuleRegistry;
import closedw.br.container.support.ContainerRegistry;
import closedw.br.mapping.MappingSessionManager;
import closedw.br.networking.ExtractKeyStateManager;
import closedw.br.networking.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

/**
 * Better Removal（NeoForge 1.21.1）入口。
 * 启动顺序很重要：容器注册表 → 槽位规则 → 由规则推导出容器配置开关 → 容器注册自检。
 */
@Mod(BetterRemoval.MODID)
public final class BetterRemoval {
    public static final String MODID = "betterremoval";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public BetterRemoval(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);

        // 注册网络 payload 处理器
        modEventBus.addListener(NetworkHandler::registerPayloads);

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
        {
            LOGGER.info("Loading Better Removal (NeoForge)");

            // 容器注册表：注册所有容器类型
            ContainerRegistry.init();
            // 槽位规则：读取声明式规则（内置 json）并做自检
            SlotRuleRegistry.load();
            // 手动指认映射：config/better-removal/menu-maps.json + 各模组 jar 内同名文件
            MenuMapStore.load();
            // 规则里的容器开关由规则推导，所以要在规则读完之后再登记进配置
            BetterRemovalConfig.get().registerRuleContainers(
                    SlotRuleRegistry.configKeys(), SlotRuleRegistry.configGroups());
            // 分组变化后，Configured 的界面缓存必须作废
            SlotRuleRegistry.addReloadListener(count -> BetterRemovalConfig.get().registerRuleContainers(
                    SlotRuleRegistry.configKeys(), SlotRuleRegistry.configGroups()));
            // 容器注册自检必须在这之后跑：配置项建好之前检查"配置键是否存在"会全部误报
            ContainerRegistry.validate();
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BetterRemovalCommand.register(event.getDispatcher());
    }

    /**
     * 方块 ID 的存在性校验要等注册表冻结
     */
    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        SlotRuleRegistry.revalidateBlockIds();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 服务端在玩家加入时把修饰键状态重置为"未按下"，并重新同步当前模式（供 Jade 预览）
            ExtractKeyStateManager.reset(player);
            InteractionModeManager.sendSync(player, InteractionModeManager.getState(player));
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ExtractKeyStateManager.remove(player.getUUID());
            MappingSessionManager.end(player);
        }
    }

    /**
     * 容器界面关掉就结束指认会话（并让客户端清掉叠层标签）。
     * 指认结果的落盘发生在每次改动时，所以这里不需要额外保存动作。
     */
    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MappingSessionManager.end(player);
        }
    }
}
