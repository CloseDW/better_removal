package closedw.br.networking;

import closedw.br.behavior.Behavior;
import closedw.br.BetterRemoval;
import closedw.br.slot.SlotSet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;

/**
 * 客户端 -> 服务端：设置当前模式（行为+槽位）。
 */
public record ExtractionModeSetC2SPacket(Behavior behavior, SlotSet slots) implements FabricPacket {

	public static final PacketType<ExtractionModeSetC2SPacket> TYPE =
			PacketType.create(BetterRemoval.id("extraction_mode_set"), buf -> new ExtractionModeSetC2SPacket(buf));

	public ExtractionModeSetC2SPacket(PacketByteBuf buf) {
		this(buf.readEnumConstant(Behavior.class), buf.readEnumConstant(SlotSet.class));
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeEnumConstant(this.behavior);
		buf.writeEnumConstant(this.slots);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
