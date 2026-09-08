package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.ExtractionAction;
import closedw.br.ExtractionMode;
import closedw.br.ModeState;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;

/**
 * 服务端 -> 客户端：同步当前模式（行为+槽位），供 Jade 联动等客户端功能读取。
 */
public record ExtractionModeSyncS2CPacket(ExtractionAction action, ExtractionMode mode) implements FabricPacket {

	public static final PacketType<ExtractionModeSyncS2CPacket> TYPE =
			PacketType.create(BetterRemoval.id("extraction_mode_sync"), buf -> new ExtractionModeSyncS2CPacket(buf));

	public ExtractionModeSyncS2CPacket(PacketByteBuf buf) {
		this(buf.readEnumConstant(ExtractionAction.class), buf.readEnumConstant(ExtractionMode.class));
	}

	public ExtractionModeSyncS2CPacket(ModeState state) {
		this(state.action(), state.mode());
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
