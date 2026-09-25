package closedw.br.networking;

import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionMode;
import closedw.br.BetterRemoval;
import closedw.br.slot.SlotSet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;

/**
 * 服务端 -> 客户端：同步当前模式（行为+槽位），供 Jade 联动等客户端功能读取。
 * 同时告诉客户端"主动探测"模式是否可用（实验性开关），模式滚轮据此决定是否多显示一个预设。
 */
public record ExtractionModeSyncS2CPacket(Behavior behavior, SlotSet slots, boolean probeAvailable) implements FabricPacket {

	public static final PacketType<ExtractionModeSyncS2CPacket> TYPE =
			PacketType.create(BetterRemoval.id("extraction_mode_sync"), buf -> new ExtractionModeSyncS2CPacket(buf));

	public ExtractionModeSyncS2CPacket(PacketByteBuf buf) {
		this(buf.readEnumConstant(Behavior.class), buf.readEnumConstant(SlotSet.class), buf.readBoolean());
	}

	public ExtractionModeSyncS2CPacket(InteractionMode state, boolean probeAvailable) {
		this(state.behavior(), state.slots(), probeAvailable);
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeEnumConstant(this.behavior);
		buf.writeEnumConstant(this.slots);
		buf.writeBoolean(this.probeAvailable);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
