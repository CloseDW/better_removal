package closedw.br.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：同步修饰键（左Alt，可改键）的按下状态。
 * 由客户端在按键状态变化时发送，服务端据此判断是否触发免开 GUI 的取出/放入/补货。
 */
public record ExtractKeyStatePayload(boolean pressed) {

    public ExtractKeyStatePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.pressed);
    }

    public static void handle(ExtractKeyStatePayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ExtractKeyStateManager.setAltKeyDown(player, msg.pressed());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
