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
import closedw.br.networking.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Better Removal（Forge 1.20.1）入口。
 * 启动顺序很重要：容器注册表 → 槽位规则 → 手动指认映射 → 由规则推导出容器配置开关 → 容器注册自检。
 * <p>
 * 与 NeoForge 侧的差别只在接线方式：Forge 1.20.1 的 {@code @Mod} 构造器<b>必须是无参的</b>
 * （{@code FMLModContainer.constructMod} 只找 {@code getDeclaredConstructor()}），
 * 所以 {@code IEventBus} 要从 {@link FMLJavaModLoadingContext} 取；
 * 网络层是 {@code SimpleChannel}（见 {@link NetworkHandler}）而不是 payload 注册事件。
 */
@Mod(BetterRemoval.MODID)
public final class BetterRemoval {
    public static final String MODID = "betterremoval";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /**
     * <b>构造器必须无参</b>：Forge 47.x 的 {@code FMLModContainer.constructMod}
     * （{@code javafmllanguage-1.20.1-47.4.22} 第 75 行）只调 {@code getDeclaredConstructor()}，
     * 没有"按参数类型注入"这一步。曾经试过改成 {@code BetterRemoval(IEventBus)}，
     * 结果启动直接崩：
     * <pre>
     * java.lang.NoSuchMethodException: closedw.br.BetterRemoval.&lt;init&gt;()
     *     at net.minecraftforge.fml.javafmlmod.FMLModContainer.constructMod(FMLModContainer.java:75)
     * </pre>
     * 所以事件总线只能从 {@link FMLJavaModLoadingContext} 取。
     * <p>
     * 那个 getter 在 47.4.x 上标了 {@code @Deprecated(forRemoval = true)}，会有编译警告，
     * 但它是<b>唯一可用</b>的写法——别为了消警告再改成注入。
     */
    public BetterRemoval() {
        // 游戏事件（玩家登录/登出、关容器、注册命令、服务器启动）
        MinecraftForge.EVENT_BUS.register(this);

        // 注册网络通道与全部数据包
        NetworkHandler.register();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        // 客户端专属接线（键位注册、滚轮/界面事件）。专用服务端不会进入这个分支，
        // 因此 BetterRemovalClient（引用了 net.minecraft.client.*）在服务端不会被加载。
        if (FMLEnvironment.dist == Dist.CLIENT) {
            BetterRemovalClient.init(modEventBus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
        {
            LOGGER.info("Loading Better Removal (Forge)");

            // 容器注册表：注册所有容器类型
            ContainerRegistry.init();
            // 槽位规则：读取声明式规则（内置 json + 各模组 jar）
            SlotRuleRegistry.load();
            // 手动指认映射：config/better-removal/menu-maps.json + 各模组 jar 内同名文件
            MenuMapStore.load();
            // 规则里的容器开关由规则推导，所以要在规则读完之后再登记进配置。
            // Configured 是必需依赖（mods.toml 里 mandatory=true），缺它时 FML 在加载期就会拦下，
            // 走不到这里，所以不需要 LinkageError 防护（与 NeoForge 侧一致）。
            BetterRemovalConfig.get().registerRuleContainers(
                    SlotRuleRegistry.configKeys(), SlotRuleRegistry.configGroups());
            // 分组变化后，Configured 的界面缓存必须作废
            SlotRuleRegistry.addReloadListener(count -> BetterRemovalConfig.get().registerRuleContainers(
                    SlotRuleRegistry.configKeys(), SlotRuleRegistry.configGroups()));
            // 容器注册自检必须在这之后跑：配置项建好之前检查"配置键是否存在"会全部误报
            ContainerRegistry.validate();

            // FTB Ultimine 反射联动状态：必须区分"没装"与"装了但反射接不上"——
            // 后者会让连锁取出/放入静默失效，不打日志的话用户只能靠猜
            if (FTBUltimineSupport.isInstalled()) {
                if (FTBUltimineSupport.isLoaded()) {
                    LOGGER.info("FTB Ultimine link ready");
                }
                else {
                    LOGGER.warn("FTB Ultimine is installed but the reflection link is unavailable, chain extraction/deposit is disabled");
                }
            }
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BetterRemovalCommand.register(event.getDispatcher());
    }

    /**
     * 方块 ID 的存在性校验要等注册表冻结（模组加载阶段各模组的方块还在注册中途），
     * 所以放到服务器启动时再跑。
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
     * <p>
     * Forge 的触发点在 {@code containerMenu.removed()/transferState()} 之后、菜单换回背包菜单之前，
     * 比 Fabric 侧注入 {@code closeHandledScreen()} 头部略晚；结束会话不读菜单内容，所以行为一致。
     */
    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MappingSessionManager.end(player);
        }
    }
}
