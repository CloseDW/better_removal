package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.CarryOnCompat;
import closedw.br.ExtractionMode;
import closedw.br.ExtractionModeManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge 1.21.1 payload 网络注册。由 BetterRemoval 通过 modEventBus.addListener 注册。
 */
public final class NetworkHandler {

    private NetworkHandler() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(ExtractionModeCyclePayload.TYPE, ExtractionModeCyclePayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    ExtractionMode next = ExtractionModeManager.getMode(player).next();
                    ExtractionModeManager.setMode(player, next);
                    player.displayClientMessage(ExtractionModeManager.getModeMessage(next), false);
                }
            });
        });

        registrar.playToServer(AltKeyStatePayload.TYPE, AltKeyStatePayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    CarryOnCompat.setAltKeyDown(player.getUUID(), payload.pressed());
                }
            });
        });

        registrar.playToClient(ExtractionModeSyncPayload.TYPE, ExtractionModeSyncPayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> ExtractionModeManager.setClientMode(payload.mode()));
        });
    }
}