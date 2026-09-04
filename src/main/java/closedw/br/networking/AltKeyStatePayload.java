package closedw.br.networking;

import closedw.br.BetterRemoval;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端：同步"取出物品"修饰键（左Alt）的按下状态。
 * 仅在安装Carry On时由客户端发送。
 */
public record AltKeyStatePayload(boolean pressed) implements CustomPacketPayload {

    public static final Type<AltKeyStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "alt_key_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AltKeyStatePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, AltKeyStatePayload::pressed, AltKeyStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}