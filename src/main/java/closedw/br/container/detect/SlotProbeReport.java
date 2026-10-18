package closedw.br.container.detect;

import closedw.br.container.rule.SlotRules;
import closedw.br.slot.SlotRole;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 实验性：主动探测。
 * 玩家处于"主动探测"模式时，按住修饰键右击容器，服务端跑 inventory接口探测：
 * 直接问 {@link Container#canPlaceItem} 与 {@code WorldlyContainer.canTakeItemThroughFace}，
 * 输出一行可点击复制的规则串，粘进 Configured 的"容器槽位规则（声明式）"即可。
 * 判定不出任何槽位时显示"无法探测"。只输出文本。
 * 菜单只读那套后端已经去掉：探测出来的规则本来就是给声明式规则用的，而规则只认实现了
 * {@link Container} 的容器，两个后端口径统一才不会把槽位号指错。
 */
public final class SlotProbeReport {

    private SlotProbeReport() {
    }

    /** 跑 inventory接口探测并把结果打印到该玩家的聊天框。 */
    public static void send(ServerPlayer player, BlockEntity blockEntity) {
        String match = matchKey(blockEntity);
        int size = size(blockEntity);

        player.displayClientMessage(Component.translatable("betterremoval.message.probe_header", match)
                .withStyle(ChatFormatting.AQUA), false);

        report(player, "betterremoval.message.probe_inventory", match, size,
                probe(() -> inventoryRoles(blockEntity)), ChatFormatting.LIGHT_PURPLE);
    }

    /** 打印一个后端的标题行，以及一行可复制的规则串。 */
    private static void report(ServerPlayer player, String labelKey, String match, int size,
            SlotRole[] roles, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(labelKey).withStyle(color), false);
        if (roles == null) {
            failed(player);
            return;
        }
        RoleSlots slots = collectRoles(roles, size);
        if (slots.isEmpty()) {
            failed(player);
            return;
        }
        sendCopyable(player, "betterremoval.message.probe_format_rules", toConfigRule(match, slots));
    }

    private static void failed(ServerPlayer player) {
        player.displayClientMessage(Component.literal("  ")
                .append(Component.translatable("betterremoval.message.probe_failed").withStyle(ChatFormatting.RED)), false);
    }

    private static void sendCopyable(ServerPlayer player, String labelKey, String value) {
        MutableComponent line = Component.literal("  ")
                .append(Component.translatable(labelKey).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": "))
                .append(copyable(value));
        player.displayClientMessage(line, false);
    }

    /** 可点击复制的文本：点击复制到剪贴板，悬停给出提示。 */
    private static MutableComponent copyable(String value) {
        return Component.literal(value).withStyle(Style.EMPTY
                .withColor(ChatFormatting.WHITE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("betterremoval.message.probe_copy_hint"))));
    }

    /** 把探测出的槽位按角色收集成三组。 */
    private static RoleSlots collectRoles(SlotRole[] roles, int size) {
        List<Integer> input = new ArrayList<>();
        List<Integer> fuel = new ArrayList<>();
        List<Integer> output = new ArrayList<>();
        int length = size >= 0 ? Math.min(size, roles.length) : roles.length;
        for (int slot = 0; slot < length; slot++) {
            SlotRole role = roles[slot];
            if (role == null) {
                continue;
            }
            switch (role) {
                case INPUT -> input.add(slot);
                case FUEL -> fuel.add(slot);
                case OUTPUT -> output.add(slot);
                case NONE -> {
                }
            }
        }
        return new RoleSlots(input, fuel, output);
    }

    /** Configured"容器槽位规则"格式：{@code <匹配串> input=0,1 fuel=2 output=3}；无结果返回 null。 */
    private static String toConfigRule(String match, RoleSlots roles) {
        if (roles.isEmpty()) {
            return null;
        }
        StringBuilder rule = new StringBuilder(match);
        appendRule(rule, "input", roles.input());
        appendRule(rule, "fuel", roles.fuel());
        appendRule(rule, "output", roles.output());
        return rule.toString();
    }

    private static void appendRule(StringBuilder rule, String key, List<Integer> slots) {
        if (slots.isEmpty()) {
            return;
        }
        rule.append(' ').append(key).append('=');
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) {
                rule.append(',');
            }
            rule.append(slots.get(i).intValue());
        }
    }

    /** 规则匹配串：优先用方块ID，取不到时退回"@方块实体类名" */
    private static String matchKey(BlockEntity blockEntity) {
        ResourceLocation id = SlotRules.blockId(blockEntity);
        return id != null ? id.toString() : "@" + blockEntity.getClass().getName();
    }

    private static SlotRole[] inventoryRoles(BlockEntity blockEntity) {
        if (!(blockEntity instanceof Container container)) {
            return null;
        }
        return SlotDetection.inventoryRoles(container);
    }

    /** 容器槽位数；不是 {@link Container} 时返回 -1 */
    private static int size(BlockEntity blockEntity) {
        if (!(blockEntity instanceof Container container)) {
            return -1;
        }
        try {
            return container.getContainerSize();
        }
        catch (Throwable t) {
            return -1;
        }
    }

    /** 探测后端可能抛异常，失败按"这个后端没结果"处理 */
    private static SlotRole[] probe(Supplier<SlotRole[]> supplier) {
        try {
            return supplier.get();
        }
        catch (Throwable t) {
            return null;
        }
    }

    private record RoleSlots(List<Integer> input, List<Integer> fuel, List<Integer> output) {
        boolean isEmpty() {
            return this.input.isEmpty() && this.fuel.isEmpty() && this.output.isEmpty();
        }
    }
}
