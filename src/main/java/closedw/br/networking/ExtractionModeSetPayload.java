package closedw.br.networking;

import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionMode;
import closedw.br.behavior.InteractionModeManager;
import closedw.br.slot.SlotSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：设置当前模式（行为+槽位）。
 */
public record ExtractionModeSetPayload(Behavior behavior, SlotSet slots) {

    public ExtractionModeSetPayload(FriendlyByteBuf buf) {
        this(buf.readEnum(Behavior.class), buf.readEnum(SlotSet.class));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(this.behavior);
        buf.writeEnum(this.slots);
    }

    public static void handle(ExtractionModeSetPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                InteractionMode state = InteractionModeManager.setState(player,
                        new InteractionMode(msg.behavior(), msg.slots()));
                player.displayClientMessage(InteractionModeManager.getStateMessage(state), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
