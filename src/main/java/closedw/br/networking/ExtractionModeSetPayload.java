package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.ExtractionAction;
import closedw.br.ExtractionMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端：设置当前模式（行为+槽位）。
 */
public record ExtractionModeSetPayload(ExtractionAction action, ExtractionMode mode) implements CustomPacketPayload {

    public static final Type<ExtractionModeSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "mode_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractionModeSetPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.of((buf, a) -> buf.writeEnum(a), buf -> buf.readEnum(ExtractionAction.class)),
                    ExtractionModeSetPayload::action,
                    StreamCodec.of((buf, m) -> buf.writeEnum(m), buf -> buf.readEnum(ExtractionMode.class)),
                    ExtractionModeSetPayload::mode,
                    ExtractionModeSetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
