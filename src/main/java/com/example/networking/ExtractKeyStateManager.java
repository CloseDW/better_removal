package com.example.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端保存每位玩家"取出物品"左Alt
 * 状态由客户端通过 {@link ExtractKeyStateC2SPacket} 同步，连接断开时自动清理。
 */
public final class ExtractKeyStateManager {

	private static final Map<UUID, Boolean> ALT_STATES = new ConcurrentHashMap<>();

	private ExtractKeyStateManager() {
	}

	public static void registerServerHandlers() {
		ServerPlayNetworking.registerGlobalReceiver(ExtractKeyStateC2SPacket.TYPE, (packet, player, responseSender) -> {
			ALT_STATES.put(player.getUuid(), packet.pressed());
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ALT_STATES.put(handler.player.getUuid(), false);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ALT_STATES.remove(handler.player.getUuid());
		});
	}

	public static boolean isAltKeyDown(PlayerEntity player) {
		return ALT_STATES.getOrDefault(player.getUuid(), false);
	}
}