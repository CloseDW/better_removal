package closedw.br.networking;

import closedw.br.BetterRemoval;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge 1.20.1 的网络层：通道注册与发送入口。
 * <p>
 * 与 NeoForge 侧的 {@code PayloadRegistrar} 一一对应，只是 Forge 1.20.1 没有
 * {@code StreamCodec}/{@code CustomPacketPayload}，每个包自己带
 * {@code encode(FriendlyByteBuf)} 与 {@code (FriendlyByteBuf)} 构造器。
 * <p>
 * <b>双方必须用同一份构建</b>（包结构、方向、ID 变了就不兼容）。
 */
public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int id = 0;

    private NetworkHandler() {
    }

    /** 由 {@link BetterRemoval} 在构造期调用（对应 NeoForge 侧的 RegisterPayloadHandlersEvent）。 */
    public static void register() {
        CHANNEL.messageBuilder(ExtractionModeSetPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ExtractionModeSetPayload::encode)
                .decoder(ExtractionModeSetPayload::new)
                .consumerMainThread(ExtractionModeSetPayload::handle)
                .add();

        CHANNEL.messageBuilder(ExtractKeyStatePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ExtractKeyStatePayload::encode)
                .decoder(ExtractKeyStatePayload::new)
                .consumerMainThread(ExtractKeyStatePayload::handle)
                .add();

        CHANNEL.messageBuilder(MappingUpdatePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MappingUpdatePayload::encode)
                .decoder(MappingUpdatePayload::new)
                .consumerMainThread(MappingUpdatePayload::handle)
                .add();

        CHANNEL.messageBuilder(ExtractionModeSyncPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ExtractionModeSyncPayload::encode)
                .decoder(ExtractionModeSyncPayload::new)
                .consumerMainThread(ExtractionModeSyncPayload::handle)
                .add();

        CHANNEL.messageBuilder(MappingFeedbackPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MappingFeedbackPayload::encode)
                .decoder(MappingFeedbackPayload::new)
                .consumerMainThread(MappingFeedbackPayload::handle)
                .add();
    }

    /** 客户端 -> 服务端。 */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    /** 服务端 -> 单个玩家。 */
    public static void sendToPlayer(ServerPlayer player, Object message) {
        if (player != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }
}
