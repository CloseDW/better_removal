package com.better_removal.networking;

import com.better_removal.CarryOnCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：同步"取出物品"修饰键（左Alt）的按下状态。
 * 仅在安装Carry On时由客户端发送，用于在服务端判断是否触发出品槽提取。
 */
public class AltKeyStatePacket {

	private final boolean pressed;

	public AltKeyStatePacket(boolean pressed) {
		this.pressed = pressed;
	}

	public AltKeyStatePacket(FriendlyByteBuf buf) {
		this.pressed = buf.readBoolean();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeBoolean(this.pressed);
	}

	public static void handle(AltKeyStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			ServerPlayer player = ctx.get().getSender();
			if (player != null) {
				CarryOnCompat.setAltKeyDown(player.getUUID(), msg.pressed);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}