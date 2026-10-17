package closedw.br.networking;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端保存每位玩家"取出物品"修饰键（左Alt，可改键）的按下状态。
 * 状态由客户端通过 {@link ExtractKeyStatePayload} 同步，玩家加入时重置、连接断开时清理。
 */
public final class ExtractKeyStateManager {

    private static final Map<UUID, Boolean> ALT_STATES = new ConcurrentHashMap<>();

    private ExtractKeyStateManager() {
    }

    public static void setAltKeyDown(Player player, boolean pressed) {
        if (player == null) {
            return;
        }
        ALT_STATES.put(player.getUUID(), pressed);
    }

    public static boolean isAltKeyDown(Player player) {
        return player != null && ALT_STATES.getOrDefault(player.getUUID(), false);
    }

    /** 玩家加入时重置：客户端只在按键状态变化时才发包，重连后必须回到"未按下" */
    public static void reset(Player player) {
        if (player != null) {
            ALT_STATES.put(player.getUUID(), false);
        }
    }

    public static void remove(UUID uuid) {
        ALT_STATES.remove(uuid);
    }
}
