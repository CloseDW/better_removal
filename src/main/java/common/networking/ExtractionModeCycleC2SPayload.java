package common.networking;

import common.BetterRemoval;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * 客户端 -> 服务端：请求循环切换取出模式（由服务端决定新模式并回发提示）。
 * 空载荷，仅作触发信号。
 */
public record ExtractionModeCycleC2SPayload() implements CustomPayload {

	public static final CustomPayload.Id<ExtractionModeCycleC2SPayload> ID =
			new CustomPayload.Id<>(BetterRemoval.id("extraction_mode_cycle"));

	public static final PacketCodec<RegistryByteBuf, ExtractionModeCycleC2SPayload> CODEC =
			PacketCodec.unit(new ExtractionModeCycleC2SPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}