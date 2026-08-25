package com.example.command;

import com.example.ExtractionMode;
import com.example.ExtractionModeManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /br 指令：切换取出模式。
 *   /br output  取出输出槽
 *   /br input   取出输入槽
 *   /br fuel    取出燃料槽
 *   /br all     全部取出
 */
public final class BetterRemovalCommand {

	private BetterRemovalCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		var root = CommandManager.literal("br")
				.requires(src -> src.getPlayer() != null);

		for (ExtractionMode mode : ExtractionMode.values()) {
			root.then(CommandManager.literal(mode.getName())
					.executes(ctx -> {
						ServerPlayerEntity player = ctx.getSource().getPlayer();
						if (player == null) {
							return 0;
						}
						ExtractionModeManager.setMode(player, mode);
						player.sendMessage(ExtractionModeManager.getModeMessage(mode), false);
						return 1;
					}));
		}

		// /br now：显示当前取出模式
		root.then(CommandManager.literal("now")
				.executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayer();
					if (player == null) {
						return 0;
					}
					player.sendMessage(ExtractionModeManager.getCurrentModeMessage(ExtractionModeManager.getMode(player)), false);
					return 1;
				}));

		dispatcher.register(root);
	}
}