package closedw.br.networking;

import closedw.br.mapping.MappingSessionManager;
import closedw.br.slot.SlotRole;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：把当前悬停的 GUI 槽位标成某个角色。
 * 槽位号是<b>菜单槽位索引</b>（玩家在界面里看到的那个槽），由服务端用它自己那份菜单校验后落盘。
 */
public record MappingUpdatePayload(int menuIndex, SlotRole role) {

    public MappingUpdatePayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readEnum(SlotRole.class));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.menuIndex);
        buf.writeEnum(this.role);
    }

    public static void handle(MappingUpdatePayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MappingSessionManager.setRole(player, msg.menuIndex(), msg.role());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
