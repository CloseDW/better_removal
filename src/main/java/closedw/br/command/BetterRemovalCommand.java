package closedw.br.command;

import closedw.br.behavior.InteractionModeManager;
import closedw.br.container.rule.SlotRuleRegistry;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 *{@code /br now}显示当前模式；
 *{@code /br reload} 重新读取容器槽位规则。
 * 模式本身一律通过模式键位的滚轮切换，不再提供指令入口。
 */
public final class BetterRemovalCommand {

	private BetterRemovalCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		var root = CommandManager.literal("br")
				.requires(src -> src.getPlayer() != null);

		root.then(CommandManager.literal("reload").executes(ctx -> {
			ServerPlayerEntity player = ctx.getSource().getPlayer();
			if (player == null) {
				return 0;
			}
			int count = SlotRuleRegistry.reload();
			// 规则变化会改变「主动探测」是否可用，重新同步一次给客户端
			InteractionModeManager.refreshClient(player);
			if (count == 0) {
				player.sendMessage(Text.translatable("better-removal.message.rules_empty")
						.formatted(Formatting.RED), false);
			}
			player.sendMessage(Text.translatable("better-removal.message.rules_reloaded", count)
					.formatted(Formatting.YELLOW), false);
			return 1;
		}));

		Command<ServerCommandSource> show = ctx -> {
			ServerPlayerEntity player = ctx.getSource().getPlayer();
			if (player == null) {
				return 0;
			}
			player.sendMessage(InteractionModeManager.getStateMessage(InteractionModeManager.getState(player)), false);
			return 1;
		};
		root.then(CommandManager.literal("now").executes(show));
		root.executes(show);

		dispatcher.register(root);
	}
}
