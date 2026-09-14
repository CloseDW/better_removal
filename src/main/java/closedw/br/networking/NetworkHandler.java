package closedw.br.networking;

import closedw.br.CarryOnCompat;
import closedw.br.ExtractionModeManager;
import closedw.br.ModeState;
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

        registrar.playToServer(ExtractionModeSetPayload.TYPE, ExtractionModeSetPayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    ModeState state = ExtractionModeManager.setState(player, new ModeState(payload.action(), payload.mode()));
                    player.displayClientMessage(ExtractionModeManager.getStateMessage(state), false);
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
            context.enqueueWork(() -> ExtractionModeManager.setClientState(
                    new ModeState(payload.action(), payload.mode()), payload.probeAvailable()));
        });
    }
}
