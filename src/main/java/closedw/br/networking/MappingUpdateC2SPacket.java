package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.slot.SlotRole;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;

/**
 * 客户端 -> 服务端：把当前悬停的 GUI 槽位标成某个角色。
 * 槽位号是<b>菜单槽位索引</b>（玩家在界面里看到的那个槽），由服务端用它自己那份菜单校验后落盘。
 */
public record MappingUpdateC2SPacket(int menuIndex, SlotRole role) implements FabricPacket {

	public static final PacketType<MappingUpdateC2SPacket> TYPE =
			PacketType.create(BetterRemoval.id("mapping_update"), MappingUpdateC2SPacket::new);

	public MappingUpdateC2SPacket(PacketByteBuf buf) {
		this(buf.readVarInt(), buf.readEnumConstant(SlotRole.class));
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeVarInt(this.menuIndex);
		buf.writeEnumConstant(this.role);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
