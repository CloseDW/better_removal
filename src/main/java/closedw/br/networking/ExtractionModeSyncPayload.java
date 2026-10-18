package closedw.br.networking;

import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionMode;
import closedw.br.behavior.InteractionModeManager;
import closedw.br.slot.SlotSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：同步当前模式（行为+槽位），供 Jade 联动等客户端功能读取。
 * 同时告诉客户端"主动探测"与"手动指认"模式是否可用，模式滚轮据此决定要不要多显示预设。
 */
public record ExtractionModeSyncPayload(Behavior behavior, SlotSet slots, boolean probeAvailable,
        boolean mappingAvailable) {

    public ExtractionModeSyncPayload(InteractionMode state, boolean probeAvailable, boolean mappingAvailable) {
        this(state.behavior(), state.slots(), probeAvailable, mappingAvailable);
    }

    public ExtractionModeSyncPayload(FriendlyByteBuf buf) {
        this(buf.readEnum(Behavior.class), buf.readEnum(SlotSet.class), buf.readBoolean(), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(this.behavior);
        buf.writeEnum(this.slots);
        buf.writeBoolean(this.probeAvailable);
        buf.writeBoolean(this.mappingAvailable);
    }

    public static void handle(ExtractionModeSyncPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> InteractionModeManager.setClientState(
                new InteractionMode(msg.behavior(), msg.slots()),
                msg.probeAvailable(), msg.mappingAvailable()));
        ctx.get().setPacketHandled(true);
    }
}
