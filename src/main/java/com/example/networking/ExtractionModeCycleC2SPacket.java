package com.example.networking;

import com.example.BetterRemoval;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;

/**
 * 客户端 -> 服务端：请求循环切换取出模式（由服务端决定新模式并回发提示）。
 * 空载荷，仅作触发信号。
 */
public record ExtractionModeCycleC2SPacket() implements FabricPacket {

	public static final PacketType<ExtractionModeCycleC2SPacket> TYPE =
			PacketType.create(BetterRemoval.id("extraction_mode_cycle"), buf -> new ExtractionModeCycleC2SPacket());

	public ExtractionModeCycleC2SPacket(PacketByteBuf buf) {
		this();
	}

	@Override
	public void write(PacketByteBuf buf) {
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}