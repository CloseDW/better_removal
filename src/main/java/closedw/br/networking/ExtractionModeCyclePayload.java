package closedw.br.networking;

import closedw.br.BetterRemoval;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端：请求循环切换取出模式（由服务端决定新模式并回发提示）。空载荷。
 */
public record ExtractionModeCyclePayload() implements CustomPacketPayload {

    public static final Type<ExtractionModeCyclePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "mode_cycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractionModeCyclePayload> CODEC =
            StreamCodec.unit(new ExtractionModeCyclePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}