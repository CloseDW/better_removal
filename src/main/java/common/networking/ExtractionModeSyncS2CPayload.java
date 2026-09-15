package common.networking;

import common.BetterRemoval;
import common.ExtractionAction;
import common.ExtractionMode;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * 服务端 -> 客户端：同步当前模式（行为+槽位），并告知"主动探测"是否可用。
 */
public record ExtractionModeSyncS2CPayload(ExtractionAction action, ExtractionMode mode, boolean probeAvailable) implements CustomPayload {

	public static final CustomPayload.Id<ExtractionModeSyncS2CPayload> ID =
			new CustomPayload.Id<>(BetterRemoval.id("extraction_mode_sync"));

	public static final PacketCodec<RegistryByteBuf, ExtractionModeSyncS2CPayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.STRING, payload -> payload.action.name(),
					PacketCodecs.STRING, payload -> payload.mode.name(),
					PacketCodecs.BOOL, payload -> payload.probeAvailable,
					(action, mode, probeAvailable) -> new ExtractionModeSyncS2CPayload(
							ExtractionAction.valueOf(action), ExtractionMode.valueOf(mode), probeAvailable));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
