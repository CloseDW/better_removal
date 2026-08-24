package com.example.networking;

import com.example.BetterRemoval;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;

/**
 * 客户端 -> 服务端：同步"取出物品"左Alt
 * 仅在安装Carry On时由客户端发送，用于在服务端判断是否触发出品槽提取。
 */
public record ExtractKeyStateC2SPacket(boolean pressed) implements FabricPacket {

	public static final PacketType<ExtractKeyStateC2SPacket> TYPE =
			PacketType.create(BetterRemoval.id("extract_key_state"), buf -> new ExtractKeyStateC2SPacket(buf));

	public ExtractKeyStateC2SPacket(PacketByteBuf buf) {
		this(buf.readBoolean());
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeBoolean(this.pressed);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}