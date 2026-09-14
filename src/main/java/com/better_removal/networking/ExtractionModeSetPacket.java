package com.better_removal.networking;

import com.better_removal.ExtractionAction;
import com.better_removal.ExtractionMode;
import com.better_removal.ExtractionModeManager;
import com.better_removal.ModeState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：设置当前模式（行为+槽位）。
 */
public class ExtractionModeSetPacket {

	private final ExtractionAction action;
	private final ExtractionMode mode;

	public ExtractionModeSetPacket(ExtractionAction action, ExtractionMode mode) {
		this.action = action;
		this.mode = mode;
	}

	public ExtractionModeSetPacket(FriendlyByteBuf buf) {
		this.action = buf.readEnum(ExtractionAction.class);
		this.mode = buf.readEnum(ExtractionMode.class);
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeEnum(this.action);
		buf.writeEnum(this.mode);
	}

	public static void handle(ExtractionModeSetPacket msg, Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			ServerPlayer player = ctx.get().getSender();
			if (player != null) {
				ModeState state = ExtractionModeManager.setState(player, new ModeState(msg.action, msg.mode));
				player.displayClientMessage(ExtractionModeManager.getStateMessage(state), false);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}