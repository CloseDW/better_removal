package com.better_removal.networking;

import com.better_removal.ExtractionAction;
import com.better_removal.ExtractionMode;
import com.better_removal.ExtractionModeManager;
import com.better_removal.ModeState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：同步当前模式（行为+槽位），供 Jade 联动等客户端功能读取。
 * 同时告诉客户端"主动探测"模式是否可用（实验性开关），模式滚轮据此决定是否多显示一个预设。
 */
public class ExtractionModeSyncPacket {

	private final ExtractionAction action;
	private final ExtractionMode mode;
	private final boolean probeAvailable;

	public ExtractionModeSyncPacket(ModeState state, boolean probeAvailable) {
		this(state.action(), state.mode(), probeAvailable);
	}

	public ExtractionModeSyncPacket(ExtractionAction action, ExtractionMode mode, boolean probeAvailable) {
		this.action = action;
		this.mode = mode;
		this.probeAvailable = probeAvailable;
	}

	public ExtractionModeSyncPacket(FriendlyByteBuf buf) {
		this.action = buf.readEnum(ExtractionAction.class);
		this.mode = buf.readEnum(ExtractionMode.class);
		this.probeAvailable = buf.readBoolean();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeEnum(this.action);
		buf.writeEnum(this.mode);
		buf.writeBoolean(this.probeAvailable);
	}

	public static void handle(ExtractionModeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> ExtractionModeManager.setClientState(new ModeState(msg.action, msg.mode), msg.probeAvailable));
		ctx.get().setPacketHandled(true);
	}
}
