package com.better_removal.command;

import com.better_removal.ExtractionAction;
import com.better_removal.ExtractionMode;
import com.better_removal.ExtractionModeManager;
import com.better_removal.ModeState;
import com.better_removal.OutputSlotExtractor;
import com.better_removal.experimental.SlotRules;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /br 指令：
 *   /br extract output|input|fuel|all  取出模式（空手+修饰键右击）
 *   /br deposit input|fuel             放入模式（手持物品+修饰键右击）
 *   /br restock                        补货模式（空手+修饰键右击，从背包补充已有同类物品）
 *   /br reload                         重新读取容器槽位规则（改完 json / 配置后不用重启）
 *   /br now 或裸 /br                   显示当前模式
 */
public final class BetterRemovalCommand {

	private BetterRemovalCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = Commands.literal("br")
				.requires(src -> src.getPlayer() != null);

		var extract = Commands.literal("extract");
		for (ExtractionMode mode : ExtractionMode.values()) {
			extract.then(Commands.literal(mode.getName()).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				ExtractionModeManager.setState(player, new ModeState(ExtractionAction.EXTRACT, mode));
				player.displayClientMessage(ExtractionModeManager.getStateMessage(new ModeState(ExtractionAction.EXTRACT, mode)), false);
				return 1;
			}));
		}
		root.then(extract);

		var deposit = Commands.literal("deposit");
		for (ExtractionMode mode : List.of(ExtractionMode.INPUT, ExtractionMode.FUEL)) {
			deposit.then(Commands.literal(mode.getName()).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				ExtractionModeManager.setState(player, new ModeState(ExtractionAction.DEPOSIT, mode));
				player.displayClientMessage(ExtractionModeManager.getStateMessage(new ModeState(ExtractionAction.DEPOSIT, mode)), false);
				return 1;
			}));
		}
		root.then(deposit);

		root.then(Commands.literal("restock").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			ExtractionModeManager.setState(player, new ModeState(ExtractionAction.RESTOCK, ExtractionMode.ALL));
			player.displayClientMessage(ExtractionModeManager.getStateMessage(new ModeState(ExtractionAction.RESTOCK, ExtractionMode.ALL)), false);
			return 1;
		}));

		root.then(Commands.literal("reload").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			int count = SlotRules.reload();
			// 配置改完后重新同步一次：模式滚轮据此决定是否显示"主动探测"
			ExtractionModeManager.refreshClient(player);
			if (!OutputSlotExtractor.isExperimentalEnabled()) {
				player.displayClientMessage(Component.translatable("better_removal.message.rules_disabled")
						.withStyle(ChatFormatting.RED), false);
			}
			player.displayClientMessage(Component.translatable("better_removal.message.rules_reloaded", count)
					.withStyle(ChatFormatting.YELLOW), false);
			return 1;
		}));

		root.then(Commands.literal("now").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			player.displayClientMessage(ExtractionModeManager.getStateMessage(ExtractionModeManager.getState(player)), false);
			return 1;
		}));
		root.executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			player.displayClientMessage(ExtractionModeManager.getStateMessage(ExtractionModeManager.getState(player)), false);
			return 1;
		});

		dispatcher.register(root);
	}
}