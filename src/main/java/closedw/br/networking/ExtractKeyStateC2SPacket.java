package closedw.br.networking;

import closedw.br.BetterRemoval;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;

/**
 * 客户端 -> 服务端：同步修饰键（左Alt，可改键）的按下状态。
 * 由客户端在按键状态变化时发送，服务端据此判断是否触发免开 GUI 的取出/放入/补货。
 */
public record ExtractKeyStateC2SPacket(boolean pressed) implements FabricPacket {

	public static final PacketType<ExtractKeyStateC2SPacket> TYPE =
			PacketType.create(BetterRemoval.id("extract_key_state"), buf -> new ExtractKeyStateC2SPacket(buf));

	public ExtractKeyStateC2SPacket(PacketByteBuf buf) {
		this(buf.readBoolean());
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeBoolean(this.pressed);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}