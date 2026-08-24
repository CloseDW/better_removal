package common.networking;

import common.BetterRemoval;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * 客户端 -> 服务端：同步"取出物品"修饰键（左Alt）的按下状态。
 * 仅在安装Carry On时由客户端发送，用于在服务端判断是否触发出品槽提取。
 */
public record ExtractKeyStateC2SPayload(boolean pressed) implements CustomPayload {

	public static final CustomPayload.Id<ExtractKeyStateC2SPayload> ID =
			new CustomPayload.Id<>(BetterRemoval.id("extract_key_state"));

	public static final PacketCodec<RegistryByteBuf, ExtractKeyStateC2SPayload> CODEC =
			PacketCodec.tuple(PacketCodecs.BOOL, ExtractKeyStateC2SPayload::pressed, ExtractKeyStateC2SPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}