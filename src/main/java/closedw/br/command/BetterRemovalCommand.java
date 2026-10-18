package closedw.br.command;

import closedw.br.behavior.InteractionModeManager;
import closedw.br.container.menu.MenuMap;
import closedw.br.container.menu.MenuMapStore;
import closedw.br.container.rule.SlotRuleRegistry;
import closedw.br.slot.SlotRole;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("br")
                .requires(src -> src.getPlayer() != null);

        root.then(Commands.literal("reload").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayer();
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
            player.displayClientMessage(Component.translatable("betterremoval.message.reloaded", rules, maps)
                    .withStyle(rules == 0 ? ChatFormatting.RED : ChatFormatting.YELLOW), false);
            return 1;
        }));

        // /br list [关键字]：看自己指认过哪些容器（关键字按匹配串做包含过滤，忽略大小写）
        root.then(Commands.literal("list")
                .executes(ctx -> listMaps(ctx.getSource(), ""))
                .then(Commands.argument("filter", StringArgumentType.greedyString())
                        .executes(ctx -> listMaps(ctx.getSource(), StringArgumentType.getString(ctx, "filter")))));

        // /br delete <匹配串>：由 /br list 每行末尾的 [-] 按钮触发（也可以手敲）
        root.then(Commands.literal("delete")
                .then(Commands.argument("match", StringArgumentType.greedyString())
                        .executes(ctx -> deleteMap(ctx.getSource(),
                                StringArgumentType.getString(ctx, "match")))));

        dispatcher.register(root);
    }

    /**
     * 把已指认的容器列到聊天框：一行一个，格式「匹配串 — 输入×3 输出×1 [-]」。
     * 匹配串可点击复制，行尾的 {@code [-]} 点一下就删除这条映射。
     */
    private static int listMaps(CommandSourceStack source, String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<MenuMap> all = MenuMapStore.all();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("betterremoval.message.maps_empty")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        List<MenuMap> shown = new ArrayList<>();
        for (MenuMap map : all) {
            if (needle.isEmpty() || map.match().toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(map);
            }
        }
        if (shown.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("betterremoval.message.maps_none_matching", filter)
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("betterremoval.message.maps_header", shown.size())
                .withStyle(ChatFormatting.YELLOW), false);
        for (MenuMap map : shown) {
            MutableComponent line = Component.literal("• ").withStyle(ChatFormatting.GRAY)
                    .append(copyable(map.match()))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(roleSummary(map))
                    .append(Component.literal("  "))
                    .append(deleteButton(map.match()));
            source.sendSuccess(() -> line, false);
        }
        return shown.size();
    }

    /** 删除一条手动指认映射，回一行结果就完事（不重画列表）。 */
    private static int deleteMap(CommandSourceStack source, String rawMatch) {
        if (source.getPlayer() == null) {
            return 0;
        }
        final String key = MenuMap.normalize(rawMatch == null ? "" : rawMatch.trim());
        if (key.isEmpty() || MenuMapStore.findExact(key) == null) {
            source.sendSuccess(() -> Component.translatable("betterremoval.message.maps_delete_missing", key)
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (!MenuMapStore.remove(key)) {
            // 配置里没有、模组自带文件里才有：那种改不了
            source.sendSuccess(() -> Component.translatable(
                    MenuMapStore.isShipped(key)
                            ? "betterremoval.message.maps_delete_shipped"
                            : "betterremoval.message.maps_delete_missing", key)
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("betterremoval.message.maps_deleted", key)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /** 行尾的删除按钮：一个灰色的 {@code [-]}，点一下直接删。 */
    private static MutableComponent deleteButton(String match) {
        return Component.literal("[-]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.DARK_GRAY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/br delete " + match)));
    }

    /** 一条映射的角色统计，如「输入×3 输出×1」；颜色和界面里的一致。 */
    private static Component roleSummary(MenuMap map) {
        MutableComponent summary = Component.empty();
        boolean first = true;
        for (SlotRole role : List.of(SlotRole.INPUT, SlotRole.OUTPUT, SlotRole.FUEL)) {
            int count = map.slots(role).length;
            if (count <= 0) {
                continue;
            }
            if (!first) {
                summary.append(Component.literal(" "));
            }
            first = false;
            summary.append(Component.translatable(role.getTranslationKey()).withStyle(accent(role)))
                    .append(Component.literal("×" + count).withStyle(ChatFormatting.WHITE));
        }
        return summary;
    }

    /** 角色配色：输入绿 / 输出青 / 燃料金，和区域边框、光标那套完全一致。 */
    private static ChatFormatting accent(SlotRole role) {
        return switch (role) {
            case INPUT -> ChatFormatting.GREEN;
            case OUTPUT -> ChatFormatting.AQUA;
            case FUEL -> ChatFormatting.GOLD;
            case NONE -> ChatFormatting.GRAY;
        };
    }

    /** 可点击复制的文本。 */
    private static MutableComponent copyable(String value) {
        return Component.literal(value).withStyle(Style.EMPTY
                .withColor(ChatFormatting.WHITE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("betterremoval.message.probe_copy_hint"))));
    }
}
