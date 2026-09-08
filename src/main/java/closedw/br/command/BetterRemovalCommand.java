package closedw.br.command;

import closedw.br.ExtractionAction;
import closedw.br.ExtractionMode;
import closedw.br.ExtractionModeManager;
import closedw.br.ModeState;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * /br 指令：
 *   /br extract output|input|fuel|all  取出模式（空手+修饰键右击）
 *   /br deposit input|fuel             放入模式（手持物品+修饰键右击）
 *   /br restock                        补货模式（空手+修饰键右击，从背包补充已有同类物品）
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
			extract.then(CommandManager.literal(mode.getName()).executes(ctx -> {
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
			deposit.then(CommandManager.literal(mode.getName()).executes(ctx -> {
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

		Command<ServerCommandSource> show = ctx -> {
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
