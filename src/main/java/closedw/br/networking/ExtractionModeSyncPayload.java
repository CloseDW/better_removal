package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.ExtractionAction;
import closedw.br.ExtractionMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端：同步当前模式（行为+槽位），供 Jade 联动等客户端功能读取。
 * 同时告诉客户端"主动探测"模式是否可用（实验性开关），模式滚轮据此决定是否多显示一个预设。
 */
public record ExtractionModeSyncPayload(ExtractionAction action, ExtractionMode mode, boolean probeAvailable) implements CustomPacketPayload {

    public static final Type<ExtractionModeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "mode_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractionModeSyncPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.of((buf, a) -> buf.writeEnum(a), buf -> buf.readEnum(ExtractionAction.class)),
                    ExtractionModeSyncPayload::action,
                    StreamCodec.of((buf, m) -> buf.writeEnum(m), buf -> buf.readEnum(ExtractionMode.class)),
                    ExtractionModeSyncPayload::mode,
                    ByteBufCodecs.BOOL,
                    ExtractionModeSyncPayload::probeAvailable,
                    ExtractionModeSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
