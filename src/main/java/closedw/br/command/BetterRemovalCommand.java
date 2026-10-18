package closedw.br.command;

import closedw.br.behavior.InteractionModeManager;
import closedw.br.container.menu.MenuMap;
import closedw.br.container.menu.MenuMapStore;
import closedw.br.container.rule.SlotRuleRegistry;
import closedw.br.slot.SlotRole;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /br list [关键字]} 列出已指认的容器，每行末尾的 {@code [-]} 点一下就删掉那条映射；
 * {@code /br reload} 重新读取容器槽位规则与指认映射。
 * 模式一律通过模式键位的滚轮切换（切换时服务端会在聊天框回显一次），不提供查看/切换模式的指令。
 * <p>
 * 文案一律走语言文件，且刻意写得短：聊天框里每多一行解释都算噪音。
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
			int rules = SlotRuleRegistry.reload();
			// 手动指认映射同样从盘上重读
			MenuMapStore.reload();
			int maps = MenuMapStore.size();
			// 规则变化会改变「主动探测/手动指认」是否可用，重新同步一次给客户端
			InteractionModeManager.refreshClient(player);
			// 一行说完：规则 0 条时标红，否则黄色
			player.sendMessage(Text.translatable("better-removal.message.reloaded", rules, maps)
					.formatted(rules == 0 ? Formatting.RED : Formatting.YELLOW), false);
			return 1;
		}));

		// /br list [关键字]：看自己指认过哪些容器（关键字按匹配串做包含过滤，忽略大小写）
		root.then(CommandManager.literal("list")
				.executes(ctx -> listMaps(ctx.getSource(), ""))
				.then(CommandManager.argument("filter", StringArgumentType.greedyString())
						.executes(ctx -> listMaps(ctx.getSource(), StringArgumentType.getString(ctx, "filter")))));

		// /br delete <匹配串>：由 /br list 每行末尾的 [-] 按钮触发（也可以手敲）
		root.then(CommandManager.literal("delete")
				.then(CommandManager.argument("match", StringArgumentType.greedyString())
						.executes(ctx -> deleteMap(ctx.getSource(),
								StringArgumentType.getString(ctx, "match")))));

		dispatcher.register(root);
	}

	/**
	 * 把已指认的容器列到聊天框：一行一个，格式「匹配串 — 输入×3 输出×1 [-]」。
	 * 匹配串可点击复制，行尾的 {@code [-]} 点一下就删除这条映射。
	 */
	private static int listMaps(ServerCommandSource source, String filter) {
		String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
		List<MenuMap> all = MenuMapStore.all();
		if (all.isEmpty()) {
			source.sendFeedback(() -> Text.translatable("better-removal.message.maps_empty")
					.formatted(Formatting.RED), false);
			return 0;
		}
		List<MenuMap> shown = new ArrayList<>();
		for (MenuMap map : all) {
			if (needle.isEmpty() || map.match().toLowerCase(Locale.ROOT).contains(needle)) {
				shown.add(map);
			}
		}
		if (shown.isEmpty()) {
			source.sendFeedback(() -> Text.translatable("better-removal.message.maps_none_matching", filter)
					.formatted(Formatting.RED), false);
			return 0;
		}
		source.sendFeedback(() -> Text.translatable("better-removal.message.maps_header", shown.size())
				.formatted(Formatting.YELLOW), false);
		for (MenuMap map : shown) {
			MutableText line = Text.literal("• ").formatted(Formatting.GRAY)
					.append(copyable(map.match()))
					.append(Text.literal(" — ").formatted(Formatting.DARK_GRAY))
					.append(roleSummary(map))
					.append(Text.literal("  "))
					.append(deleteButton(map.match()));
			source.sendFeedback(() -> line, false);
		}
		return shown.size();
	}

	/** 删除一条手动指认映射，回一行结果就完事（不重画列表）。 */
	private static int deleteMap(ServerCommandSource source, String rawMatch) {
		if (source.getPlayer() == null) {
			return 0;
		}
		final String key = MenuMap.normalize(rawMatch == null ? "" : rawMatch.trim());
		if (key.isEmpty() || MenuMapStore.findExact(key) == null) {
			source.sendFeedback(() -> Text.translatable("better-removal.message.maps_delete_missing", key)
					.formatted(Formatting.RED), false);
			return 0;
		}
		if (!MenuMapStore.remove(key)) {
			// 配置里没有、模组自带文件里才有：那种改不了
			source.sendFeedback(() -> Text.translatable(
					MenuMapStore.isShipped(key)
							? "better-removal.message.maps_delete_shipped"
							: "better-removal.message.maps_delete_missing", key)
					.formatted(Formatting.RED), false);
			return 0;
		}
		source.sendFeedback(() -> Text.translatable("better-removal.message.maps_deleted", key)
				.formatted(Formatting.GREEN), false);
		return 1;
	}

	/** 行尾的删除按钮：一个灰色的 {@code [-]}，点一下直接删。 */
	private static MutableText deleteButton(String match) {
		return Text.literal("[-]").setStyle(Style.EMPTY
				.withColor(Formatting.DARK_GRAY)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/br delete " + match)));
	}

	/** 一条映射的角色统计，如「输入×3 输出×1」；颜色和界面里的一致。 */
	private static Text roleSummary(MenuMap map) {
		MutableText summary = Text.empty();
		boolean first = true;
		for (SlotRole role : List.of(SlotRole.INPUT, SlotRole.OUTPUT, SlotRole.FUEL)) {
			int count = map.slots(role).length;
			if (count <= 0) {
				continue;
			}
			if (!first) {
				summary.append(Text.literal(" "));
			}
			first = false;
			summary.append(Text.translatable(role.getTranslationKey()).formatted(accent(role)))
					.append(Text.literal("×" + count).formatted(Formatting.WHITE));
		}
		return summary;
	}

	/** 角色配色：输入绿 / 输出青 / 燃料金，和区域边框、光标那套完全一致。 */
	private static Formatting accent(SlotRole role) {
		return switch (role) {
			case INPUT -> Formatting.GREEN;
			case OUTPUT -> Formatting.AQUA;
			case FUEL -> Formatting.GOLD;
			case NONE -> Formatting.GRAY;
		};
	}

	/** 可点击复制的文本。 */
	private static MutableText copyable(String value) {
		return Text.literal(value).setStyle(Style.EMPTY
				.withColor(Formatting.WHITE)
				.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						Text.translatable("better-removal.message.probe_copy_hint"))));
	}
}
