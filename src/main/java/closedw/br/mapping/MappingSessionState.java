package closedw.br.mapping;

import closedw.br.networking.MappingFeedbackS2CPacket;
import closedw.br.slot.SlotRole;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 手动指认会话的客户端侧状态（服务端权威，由 {@link MappingFeedbackS2CPacket} 刷新）。
 * <p>
 * 放在 common 包而不是 client 包：网络处理器在两端都会注册，入口一律走通用类，
 * 客户端专属的渲染/输入代码才留在 {@code closedw.br.client} 里
 * （与 {@code InteractionModeManager#setClientState} 的做法一致）。
 */
public final class MappingSessionState {

	private static volatile boolean active;
	private static volatile boolean ok = true;
	private static volatile String message = "";
	private static volatile String match = "";
	/** 该容器菜单的槽位总数；0 = 没有会话。客户端据此判断当前界面是不是会话所属的那一个。 */
	private static volatile int menuSize;
	/** 菜单槽位索引 → 角色 */
	private static volatile Map<Integer, SlotRole> roles = Map.of();

	private MappingSessionState() {
	}

	public static void update(MappingFeedbackS2CPacket packet) {
		if (packet == null) {
			return;
		}
		if (!packet.active()) {
			clear();
			return;
		}
		match = packet.match() == null ? "" : packet.match();
		menuSize = Math.max(0, packet.menuSize());
		ok = packet.ok();
		message = packet.message() == null ? "" : packet.message();
		Map<Integer, SlotRole> next = new LinkedHashMap<>();
		List<MappingFeedbackS2CPacket.RoleEntry> entries = packet.roles();
		if (entries != null) {
			for (MappingFeedbackS2CPacket.RoleEntry entry : entries) {
				next.put(entry.menuIndex(), entry.role());
			}
		}
		roles = Collections.unmodifiableMap(next);
		active = true;
	}

	/** 本地乐观更新（滚轮一滚立刻看到标签变化，服务端回包会把它纠正回权威值）。 */
	public static void setLocalRole(int menuIndex, SlotRole role) {
		if (!active) {
			return;
		}
		Map<Integer, SlotRole> next = new LinkedHashMap<>(roles);
		if (role == null || role == SlotRole.NONE) {
			next.remove(menuIndex);
		}
		else {
			next.put(menuIndex, role);
		}
		roles = Collections.unmodifiableMap(next);
		ok = true;
	}

	public static void clear() {
		active = false;
		ok = true;
		message = "";
		match = "";
		menuSize = 0;
		roles = Map.of();
	}

	public static boolean isActive() {
		return active;
	}

	public static boolean isOk() {
		return ok;
	}

	public static String getMessage() {
		return message;
	}

	public static String getMatch() {
		return match;
	}

	/** 会话所属容器的菜单槽位总数；没有会话时为 0。 */
	public static int getMenuSize() {
		return menuSize;
	}

	public static Map<Integer, SlotRole> getRoles() {
		return roles;
	}

	public static SlotRole getRole(int menuIndex) {
		return roles.getOrDefault(menuIndex, SlotRole.NONE);
	}
}
