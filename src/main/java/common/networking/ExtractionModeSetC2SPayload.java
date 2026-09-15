package common.networking;

import common.BetterRemoval;
import common.ExtractionAction;
import common.ExtractionMode;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * 客户端 -> 服务端：设置当前模式（行为+槽位）。
 */
public record ExtractionModeSetC2SPayload(ExtractionAction action, ExtractionMode mode) implements CustomPayload {

	public static final CustomPayload.Id<ExtractionModeSetC2SPayload> ID =
			new CustomPayload.Id<>(BetterRemoval.id("extraction_mode_set"));

	public static final PacketCodec<RegistryByteBuf, ExtractionModeSetC2SPayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.STRING, payload -> payload.action.name(),
					PacketCodecs.STRING, payload -> payload.mode.name(),
					(action, mode) -> new ExtractionModeSetC2SPayload(ExtractionAction.valueOf(action), ExtractionMode.valueOf(mode)));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
