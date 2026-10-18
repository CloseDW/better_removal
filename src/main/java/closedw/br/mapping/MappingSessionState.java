package closedw.br.mapping;

import closedw.br.networking.MappingFeedbackPayload;
import closedw.br.slot.SlotRole;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 手动指认会话的客户端侧状态（服务端权威，由 {@link MappingFeedbackPayload} 刷新）。
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
    /**
     * 本会话所属容器的菜单槽位总数（会话已结束时为 0）。
     * <p>
     * 这是客户端判断"当前打开的界面是不是这次会话所属的那一个"的<b>唯一判据</b>——
     * 槽位数对不上（例如玩家背包）就一律不画叠层、不响应滚轮，
     * 否则两套菜单槽位号会互相污染。见 {@code client/MappingSession#bindScreen}。
     */
    private static volatile int menuSize;
    /** 菜单槽位索引 → 角色 */
    private static volatile Map<Integer, SlotRole> roles = Map.of();

    private MappingSessionState() {
    }

    public static void update(MappingFeedbackPayload payload) {
        if (payload == null) {
            return;
        }
        if (!payload.active()) {
            clear();
            return;
        }
        match = payload.match() == null ? "" : payload.match();
        ok = payload.ok();
        message = payload.message() == null ? "" : payload.message();
        menuSize = Math.max(0, payload.menuSize());
        Map<Integer, SlotRole> next = new LinkedHashMap<>();
        List<MappingFeedbackPayload.RoleEntry> entries = payload.roles();
        if (entries != null) {
            for (MappingFeedbackPayload.RoleEntry entry : entries) {
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

    /** 本会话所属容器的菜单槽位总数；会话已结束时为 0。 */
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
