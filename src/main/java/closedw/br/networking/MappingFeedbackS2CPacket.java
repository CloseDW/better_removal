package closedw.br.networking;

import closedw.br.BetterRemoval;
import closedw.br.slot.SlotRole;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：指认会话的权威状态。
 * <p>
 * 每次角色改动后服务端都会把整张角色表回传，客户端照着渲染叠层标签——
 * 这样客户端的乐观更新与服务端不一致时会被立刻纠正。
 * {@code active=false} 表示会话已结束（关闭界面、切走模式），客户端清掉叠层状态。
 *
 * @param active  会话是否还在进行
 * @param ok      本次改动是否被接受
 * @param message 结果文案的翻译键（{@code better-removal.mapping.*}）
 * @param match   该容器的匹配串，用于界面标题显示
 * @param menuSize 该容器菜单的槽位总数（会话已结束时为 0）。
 *                 客户端用它判断"当前打开的界面是不是这次会话所属的那一个"——
 *                 槽位数对不上（例如玩家背包）就一律不画叠层、不响应滚轮。
 * @param roles   菜单槽位索引 → 角色
 */
public record MappingFeedbackS2CPacket(boolean active, boolean ok, String message, String match, int menuSize,
		List<RoleEntry> roles) implements FabricPacket {

	/** 一个已指认的槽位。 */
	public record RoleEntry(int menuIndex, SlotRole role) {
	}

	public static final PacketType<MappingFeedbackS2CPacket> TYPE =
			PacketType.create(BetterRemoval.id("mapping_feedback"), MappingFeedbackS2CPacket::new);

	public MappingFeedbackS2CPacket(PacketByteBuf buf) {
		this(buf.readBoolean(), buf.readBoolean(), buf.readString(), buf.readString(), buf.readVarInt(), readRoles(buf));
	}

	private static List<RoleEntry> readRoles(PacketByteBuf buf) {
		int size = buf.readVarInt();
		List<RoleEntry> roles = new ArrayList<>(Math.max(0, size));
		for (int i = 0; i < size; i++) {
			roles.add(new RoleEntry(buf.readVarInt(), buf.readEnumConstant(SlotRole.class)));
		}
		return List.copyOf(roles);
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeBoolean(this.active);
		buf.writeBoolean(this.ok);
		buf.writeString(this.message == null ? "" : this.message);
		buf.writeString(this.match == null ? "" : this.match);
		buf.writeVarInt(this.active ? this.menuSize : 0);
		List<RoleEntry> entries = this.roles == null ? List.of() : this.roles;
		buf.writeVarInt(entries.size());
		for (RoleEntry entry : entries) {
			buf.writeVarInt(entry.menuIndex());
			buf.writeEnumConstant(entry.role());
		}
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
