package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionMode;
import closedw.br.slot.SlotSet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端：同步当前模式（行为+槽位），供 Jade 联动等客户端功能读取。
 * 同时告诉客户端"主动探测"与"手动指认"模式是否可用，模式滚轮据此决定要不要多显示预设。
 */
public record ExtractionModeSyncPayload(Behavior behavior, SlotSet slots, boolean probeAvailable,
        boolean mappingAvailable) implements CustomPacketPayload {

    public static final Type<ExtractionModeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "mode_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractionModeSyncPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.of((buf, a) -> buf.writeEnum(a), buf -> buf.readEnum(Behavior.class)),
                    ExtractionModeSyncPayload::behavior,
                    StreamCodec.of((buf, s) -> buf.writeEnum(s), buf -> buf.readEnum(SlotSet.class)),
                    ExtractionModeSyncPayload::slots,
                    ByteBufCodecs.BOOL,
                    ExtractionModeSyncPayload::probeAvailable,
                    ByteBufCodecs.BOOL,
                    ExtractionModeSyncPayload::mappingAvailable,
                    ExtractionModeSyncPayload::new);

    public ExtractionModeSyncPayload(InteractionMode state, boolean probeAvailable, boolean mappingAvailable) {
        this(state.behavior(), state.slots(), probeAvailable, mappingAvailable);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
