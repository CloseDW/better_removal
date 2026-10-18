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
 * 同时告诉客户端"主动探测"与"手动指认"模式是否可用，模式滚轮据此决定要不要多显示预设。
 */
public record ExtractionModeSyncS2CPacket(Behavior behavior, SlotSet slots, boolean probeAvailable,
		boolean mappingAvailable) implements FabricPacket {

	public static final PacketType<ExtractionModeSyncS2CPacket> TYPE =
			PacketType.create(BetterRemoval.id("extraction_mode_sync"), ExtractionModeSyncS2CPacket::new);

	public ExtractionModeSyncS2CPacket(PacketByteBuf buf) {
		this(buf.readEnumConstant(Behavior.class), buf.readEnumConstant(SlotSet.class), buf.readBoolean(), buf.readBoolean());
	}

	public ExtractionModeSyncS2CPacket(InteractionMode state, boolean probeAvailable, boolean mappingAvailable) {
		this(state.behavior(), state.slots(), probeAvailable, mappingAvailable);
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeEnumConstant(this.behavior);
		buf.writeEnumConstant(this.slots);
		buf.writeBoolean(this.probeAvailable);
		buf.writeBoolean(this.mappingAvailable);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
