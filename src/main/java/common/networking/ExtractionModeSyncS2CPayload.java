package common.networking;

import common.BetterRemoval;
import common.ExtractionMode;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * 服务端 -> 客户端：同步当前取出模式，供 Jade 联动等客户端功能读取。
 */
public record ExtractionModeSyncS2CPayload(ExtractionMode mode) implements CustomPayload {

	public static final CustomPayload.Id<ExtractionModeSyncS2CPayload> ID =
			new CustomPayload.Id<>(BetterRemoval.id("extraction_mode_sync"));

	public static final PacketCodec<RegistryByteBuf, ExtractionModeSyncS2CPayload> CODEC =
			PacketCodec.tuple(PacketCodecs.STRING,
					ExtractionModeSyncS2CPayload::modeName,
					name -> new ExtractionModeSyncS2CPayload(ExtractionMode.valueOf(name)));

	private String modeName() {
		return this.mode.name();
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}