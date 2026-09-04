package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.ExtractionMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端：同步当前取出模式，供 Jade 联动等客户端功能读取。
 */
public record ExtractionModeSyncPayload(ExtractionMode mode) implements CustomPacketPayload {

    public static final Type<ExtractionModeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "mode_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractionModeSyncPayload> CODEC =
            StreamCodec.composite(StreamCodec.of((buf, m) -> buf.writeEnum(m), buf -> buf.readEnum(ExtractionMode.class)),
                    ExtractionModeSyncPayload::mode, ExtractionModeSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}