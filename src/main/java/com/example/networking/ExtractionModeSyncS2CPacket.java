package com.example.networking;

import com.example.BetterRemoval;
import com.example.ExtractionMode;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;

/**
 * 服务端 -> 客户端：同步当前取出模式，供 Jade 联动等客户端功能读取。
 */
public record ExtractionModeSyncS2CPacket(ExtractionMode mode) implements FabricPacket {

	public static final PacketType<ExtractionModeSyncS2CPacket> TYPE =
			PacketType.create(BetterRemoval.id("extraction_mode_sync"), buf -> new ExtractionModeSyncS2CPacket(buf));

	public ExtractionModeSyncS2CPacket(PacketByteBuf buf) {
		this(buf.readEnumConstant(ExtractionMode.class));
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeEnumConstant(this.mode);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}