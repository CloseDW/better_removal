package common.command;

import com.mojang.brigadier.CommandDispatcher;
import common.ExtractionAction;
import common.ExtractionMode;
import common.ExtractionModeManager;
import common.ModeState;
import common.OutputSlotExtractor;
import common.experimental.SlotRules;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * /br 指令：
 *   /br extract output|input|fuel|all  取出模式
 *   /br deposit input|fuel             放入模式
 *   /br restock                        补货模式
 *   /br reload                         重新读取容器槽位规则
 *   /br now 或裸 /br                   显示当前模式
 */
public final class BetterRemovalCommand {

	private BetterRemovalCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		var root = CommandManager.literal("br")
				.requires(src -> src.getPlayer() != null);

		var extract = CommandManager.literal("extract");
		for (ExtractionMode mode : ExtractionMode.values()) {
			extract.then(CommandManager.literal(mode.getName())
					.executes(ctx -> {
						ServerPlayerEntity player = ctx.getSource().getPlayer();
						if (player == null) {
							return 0;
						}
						ExtractionModeManager.setState(player, new ModeState(ExtractionAction.EXTRACT, mode));
						player.sendMessage(ExtractionModeManager.getStateMessage(new ModeState(ExtractionAction.EXTRACT, mode)), false);
						return 1;
					}));
		}
		root.then(extract);

		var deposit = CommandManager.literal("deposit");
		for (ExtractionMode mode : List.of(ExtractionMode.INPUT, ExtractionMode.FUEL)) {
			deposit.then(CommandManager.literal(mode.getName())
					.executes(ctx -> {
						ServerPlayerEntity player = ctx.getSource().getPlayer();
						if (player == null) {
							return 0;
						}
						ExtractionModeManager.setState(player, new ModeState(ExtractionAction.DEPOSIT, mode));
						player.sendMessage(ExtractionModeManager.getStateMessage(new ModeState(ExtractionAction.DEPOSIT, mode)), false);
						return 1;
					}));
		}
		root.then(deposit);

		root.then(CommandManager.literal("restock").executes(ctx -> {
			ServerPlayerEntity player = ctx.getSource().getPlayer();
			if (player == null) {
				return 0;
			}
			ExtractionModeManager.setState(player, new ModeState(ExtractionAction.RESTOCK, ExtractionMode.ALL));
			player.sendMessage(ExtractionModeManager.getStateMessage(new ModeState(ExtractionAction.RESTOCK, ExtractionMode.ALL)), false);
			return 1;
		}));

		root.then(CommandManager.literal("reload").executes(ctx -> {
			ServerPlayerEntity player = ctx.getSource().getPlayer();
			if (player == null) {
				return 0;
			}
			int count = SlotRules.reload();
			ExtractionModeManager.refreshClient(player);
			if (!OutputSlotExtractor.isExperimentalEnabled()) {
				player.sendMessage(Text.translatable("better-removal.message.rules_disabled")
						.formatted(Formatting.RED), false);
			}
			player.sendMessage(Text.translatable("better-removal.message.rules_reloaded", count)
					.formatted(Formatting.YELLOW), false);
			return 1;
		}));

		var show = (com.mojang.brigadier.Command<ServerCommandSource>) ctx -> {
			ServerPlayerEntity player = ctx.getSource().getPlayer();
			if (player == null) {
				return 0;
			}
			player.sendMessage(ExtractionModeManager.getStateMessage(ExtractionModeManager.getState(player)), false);
			return 1;
		};
		root.then(CommandManager.literal("now").executes(show));
		root.executes(show);

		dispatcher.register(root);
	}
}
