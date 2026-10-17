package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.slot.SlotRole;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端：把当前悬停的 GUI 槽位标成某个角色。
 * 槽位号是<b>菜单槽位索引</b>（玩家在界面里看到的那个槽），由服务端用它自己那份菜单校验后落盘。
 */
public record MappingUpdatePayload(int menuIndex, SlotRole role) implements CustomPacketPayload {

    public static final Type<MappingUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "mapping_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MappingUpdatePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MappingUpdatePayload::menuIndex,
                    StreamCodec.of((buf, role) -> buf.writeEnum(role), buf -> buf.readEnum(SlotRole.class)),
                    MappingUpdatePayload::role,
                    MappingUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
