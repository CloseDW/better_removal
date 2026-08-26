package com.better_removal.command;

import com.better_removal.ExtractionMode;
import com.better_removal.ExtractionModeManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * /br 指令：切换取出模式。
 *   /br output  取出输出槽
 *   /br input   取出输入槽
 *   /br fuel    取出燃料槽
 *   /br all     全部取出
 *   /br now     显示当前取出模式
 */
public final class BetterRemovalCommand {

	private BetterRemovalCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = Commands.literal("br")
				.requires(src -> src.getPlayer() != null);

		for (ExtractionMode mode : ExtractionMode.values()) {
			root.then(Commands.literal(mode.getName())
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						ExtractionModeManager.setMode(player, mode);
						player.displayClientMessage(ExtractionModeManager.getModeMessage(mode), false);
						return 1;
					}));
		}

		// /br now：显示当前取出模式
		root.then(Commands.literal("now")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					player.displayClientMessage(ExtractionModeManager.getCurrentModeMessage(ExtractionModeManager.getMode(player)), false);
					return 1;
				}));

		dispatcher.register(root);
	}
}