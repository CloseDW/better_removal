package closedw.br.networking;

import closedw.br.client.MappingSession;
import closedw.br.mapping.MappingSessionState;
import closedw.br.slot.SlotRole;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：指认会话的权威状态。
 * <p>
 * 每次角色改动后服务端都会把整张角色表回传，客户端照着渲染叠层标签——
 * 这样客户端的乐观更新与服务端不一致时会被立刻纠正。
 * {@code active=false} 表示会话已结束（关闭界面、切走模式），客户端清掉叠层状态。
 *
 * @param active  会话是否仍在进行
 * @param ok      本次改动是否被接受
 * @param message 结果文案的翻译键（{@code betterremoval.mapping.*}）
 * @param match   该容器的匹配串，用于界面标题显示
 * @param menuSize 该容器菜单的槽位总数（会话已结束时为 0）。
 *                 客户端用它判断"当前打开的界面是不是这次会话所属的那一个"——
 *                 槽位数对不上（例如玩家背包）就一律不画叠层、不响应滚轮。
 *                 <b>字段顺序必须与另三端一致</b>（active, ok, message, match, menuSize, roles）。
 * @param roles   菜单槽位索引 → 角色
 */
public record MappingFeedbackPayload(boolean active, boolean ok, String message, String match, int menuSize,
        List<RoleEntry> roles) {

    /** 一个已指认的槽位。 */
    public record RoleEntry(int menuIndex, SlotRole role) {
    }

    public MappingFeedbackPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), readRoles(buf));
    }

    private static List<RoleEntry> readRoles(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<RoleEntry> roles = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            roles.add(new RoleEntry(buf.readVarInt(), buf.readEnum(SlotRole.class)));
        }
        return List.copyOf(roles);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.active);
        buf.writeBoolean(this.ok);
        buf.writeUtf(this.message == null ? "" : this.message);
        buf.writeUtf(this.match == null ? "" : this.match);
        buf.writeVarInt(this.active ? this.menuSize : 0);
        List<RoleEntry> roles = this.roles == null ? List.of() : this.roles;
        buf.writeVarInt(roles.size());
        for (RoleEntry entry : roles) {
            buf.writeVarInt(entry.menuIndex());
            buf.writeEnum(entry.role());
        }
    }

    public static void handle(MappingFeedbackPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            MappingSessionState.update(msg);
            if (!msg.active()) {
                // 会话结束：必须在这里丢掉绑定的界面引用。
                // 会话结束的典型时刻是"没有容器界面在渲染"（关掉 GUI 服务端才会结束会话），
                // 渲染钩子那时不会跑，只靠它清理会让绑定状态一直挂着。
                // 注：这个包只发往客户端（PLAY_TO_CLIENT），所以引用客户端类是安全的。
                MappingSession.onSessionEnded();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
