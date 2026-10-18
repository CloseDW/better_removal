package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.slot.SlotRole;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：指认会话的权威状态。
 * <p>
 * 每次角色改动后服务端都会把整张角色表回传，客户端照着渲染叠层标签——
 * 这样客户端的乐观更新与服务端不一致时会被立刻纠正。
 * {@code active=false} 表示会话已结束（关闭界面、切走模式），客户端清掉叠层状态。
 *
 * @param ok      本次改动是否被接受
 * @param message 结果文案的翻译键（{@code betterremoval.mapping.*}）
 * @param match   该容器的匹配串，用于界面标题显示
 * @param menuSize 该容器菜单的槽位总数（会话已结束时为 0）。
 *                 客户端用它判断"当前打开的界面是不是这次会话所属的那一个"——
 *                 槽位数对不上（例如玩家背包）就一律不画叠层、不响应滚轮。
 *                 <b>字段顺序必须与 Fabric 两端的 {@code MappingFeedbackS2CPacket} 一致</b>
 *                 （active, ok, message, match, menuSize, roles）。
 * @param roles   菜单槽位索引 → 角色
 */
public record MappingFeedbackPayload(boolean active, boolean ok, String message, String match, int menuSize,
        List<RoleEntry> roles) implements CustomPacketPayload {

    /** 一个已指认的槽位。 */
    public record RoleEntry(int menuIndex, SlotRole role) {
    }

    public static final Type<MappingFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterRemoval.MODID, "mapping_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MappingFeedbackPayload> CODEC = new StreamCodec<>() {
        @Override
        public MappingFeedbackPayload decode(RegistryFriendlyByteBuf buf) {
            boolean active = buf.readBoolean();
            boolean ok = buf.readBoolean();
            String message = buf.readUtf();
            String match = buf.readUtf();
            int menuSize = buf.readVarInt();
            int size = buf.readVarInt();
            List<RoleEntry> roles = new ArrayList<>(Math.max(0, size));
            for (int i = 0; i < size; i++) {
                roles.add(new RoleEntry(buf.readVarInt(), buf.readEnum(SlotRole.class)));
            }
            return new MappingFeedbackPayload(active, ok, message, match, menuSize, List.copyOf(roles));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, MappingFeedbackPayload value) {
            buf.writeBoolean(value.active());
            buf.writeBoolean(value.ok());
            buf.writeUtf(value.message() == null ? "" : value.message());
            buf.writeUtf(value.match() == null ? "" : value.match());
            buf.writeVarInt(value.active() ? value.menuSize() : 0);
            List<RoleEntry> roles = value.roles() == null ? List.of() : value.roles();
            buf.writeVarInt(roles.size());
            for (RoleEntry entry : roles) {
                buf.writeVarInt(entry.menuIndex());
                buf.writeEnum(entry.role());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
