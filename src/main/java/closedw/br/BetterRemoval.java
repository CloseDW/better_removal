package closedw.br;

import closedw.br.command.BetterRemovalCommand;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(BetterRemoval.MODID)
public class BetterRemoval {
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
        LOGGER.info("Loading Better Removal (NeoForge)");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BetterRemovalCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 进服时同步当前取出模式到客户端（供 Jade 预览）
            ExtractionModeManager.setMode(player, ExtractionModeManager.getMode(player));
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CarryOnCompat.remove(player.getUUID());
        }
    }
}