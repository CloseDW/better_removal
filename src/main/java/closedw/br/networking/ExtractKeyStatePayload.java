package closedw.br.networking;

import closedw.br.BetterRemoval;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端：同步修饰键（左Alt，可改键）的按下状态。
 * 由客户端在按键状态变化时发送，服务端据此判断是否触发免开 GUI 的取出/放入/补货。
 */
public record ExtractKeyStatePayload(boolean pressed) implements CustomPacketPayload {

    public static final Type<ExtractKeyStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "extract_key_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractKeyStatePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ExtractKeyStatePayload::pressed, ExtractKeyStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
