package com.better_removal;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carry On 兼容：服务端保存每位玩家"取出物品"修饰键（左Alt）的按下状态。
 * 状态由客户端通过 {@link com.better_removal.networking.AltKeyStatePacket} 同步。
 */
public final class CarryOnCompat {

	private static final Map<UUID, Boolean> ALT_STATES = new ConcurrentHashMap<>();

	private CarryOnCompat() {
	}

	public static void setAltKeyDown(UUID uuid, boolean pressed) {
		ALT_STATES.put(uuid, pressed);
	}

	public static boolean isAltKeyDown(Player player) {
		return ALT_STATES.getOrDefault(player.getUUID(), false);
	}

	public static void remove(UUID uuid) {
		ALT_STATES.remove(uuid);
	}
}