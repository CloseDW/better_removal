package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.ExtractionAction;
import closedw.br.ExtractionMode;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;

/**
 * 客户端 -> 服务端：设置当前模式（行为+槽位）。
 */
public record ExtractionModeSetC2SPacket(ExtractionAction action, ExtractionMode mode) implements FabricPacket {

	public static final PacketType<ExtractionModeSetC2SPacket> TYPE =
			PacketType.create(BetterRemoval.id("extraction_mode_set"), buf -> new ExtractionModeSetC2SPacket(buf));

	public ExtractionModeSetC2SPacket(PacketByteBuf buf) {
		this(buf.readEnumConstant(ExtractionAction.class), buf.readEnumConstant(ExtractionMode.class));
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeEnumConstant(this.action);
		buf.writeEnumConstant(this.mode);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
