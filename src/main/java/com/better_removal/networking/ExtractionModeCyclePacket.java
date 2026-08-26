package com.better_removal.networking;

import com.better_removal.ExtractionMode;
import com.better_removal.ExtractionModeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：请求循环切换取出模式（由服务端决定新模式并回发提示）。
 * 空载荷，仅作触发信号。
 */
public class ExtractionModeCyclePacket {

	public ExtractionModeCyclePacket() {
	}

	public ExtractionModeCyclePacket(FriendlyByteBuf buf) {
	}

	public void encode(FriendlyByteBuf buf) {
	}

	public static void handle(ExtractionModeCyclePacket msg, Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			ServerPlayer player = ctx.get().getSender();
			if (player != null) {
				ExtractionMode next = ExtractionModeManager.getMode(player).next();
				ExtractionModeManager.setMode(player, next);
				player.displayClientMessage(ExtractionModeManager.getModeMessage(next), false);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}