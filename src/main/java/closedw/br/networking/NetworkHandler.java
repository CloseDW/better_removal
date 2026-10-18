package closedw.br.networking;

import closedw.br.behavior.InteractionMode;
import closedw.br.behavior.InteractionModeManager;
import closedw.br.client.MappingSession;
import closedw.br.mapping.MappingSessionManager;
import closedw.br.mapping.MappingSessionState;
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
                    InteractionMode state = InteractionModeManager.setState(player,
                            new InteractionMode(payload.behavior(), payload.slots()));
                    player.displayClientMessage(InteractionModeManager.getStateMessage(state), false);
                }
            });
        });

        registrar.playToServer(ExtractKeyStatePayload.TYPE, ExtractKeyStatePayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    ExtractKeyStateManager.setAltKeyDown(player, payload.pressed());
                }
            });
        });

        // 手动指认：客户端把"悬停槽位 + 角色"发过来，服务端用自己的菜单校验
        registrar.playToServer(MappingUpdatePayload.TYPE, MappingUpdatePayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    MappingSessionManager.setRole(player, payload.menuIndex(), payload.role());
                }
            });
        });

        registrar.playToClient(ExtractionModeSyncPayload.TYPE, ExtractionModeSyncPayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> InteractionModeManager.setClientState(
                    new InteractionMode(payload.behavior(), payload.slots()),
                    payload.probeAvailable(), payload.mappingAvailable()));
        });

        registrar.playToClient(MappingFeedbackPayload.TYPE, MappingFeedbackPayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                MappingSessionState.update(payload);
                if (!payload.active()) {
                    // 会话结束：必须在这里丢掉绑定的界面引用。
                    MappingSession.onSessionEnded();
                }
            });
        });
    }
}
