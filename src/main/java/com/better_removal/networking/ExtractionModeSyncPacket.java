package com.better_removal.networking;

import com.better_removal.ExtractionMode;
import com.better_removal.ExtractionModeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：同步当前取出模式，供 Jade 联动等客户端功能读取。
 */
public class ExtractionModeSyncPacket {

	private final ExtractionMode mode;

	public ExtractionModeSyncPacket(ExtractionMode mode) {
		this.mode = mode;
	}

	public ExtractionModeSyncPacket(FriendlyByteBuf buf) {
		this.mode = buf.readEnum(ExtractionMode.class);
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeEnum(this.mode);
	}

	public static void handle(ExtractionModeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> ExtractionModeManager.setClientMode(msg.mode));
		ctx.get().setPacketHandled(true);
	}
}