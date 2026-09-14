package closedw.br.experimental;

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
 * 玩家处于"主动探测"模式时，按住修饰键右击容器，服务端依次跑两个探测后端（菜单只读、库存接口），
 * 每个后端各输出 JSON 与规则串两行，点击即可复制。
 */
public final class SlotProbeReport {

    private SlotProbeReport() {
    }

    public static void send(ServerPlayer player, BlockEntity blockEntity) {
        String match = matchKey(blockEntity);
        int size = size(blockEntity);

        player.displayClientMessage(Component.translatable("betterremoval.message.probe_header", match)
                .withStyle(ChatFormatting.AQUA), false);

        report(player, "betterremoval.message.probe_menu", match, size,
                probe(() -> MenuSlotSupport.detect(blockEntity)), ChatFormatting.GREEN);
        report(player, "betterremoval.message.probe_inventory", match, size,
                probe(() -> inventoryRoles(blockEntity)), ChatFormatting.LIGHT_PURPLE);
    }

    private static void report(ServerPlayer player, String labelKey, String match, int size,
            SlotRole[] roles, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(labelKey).withStyle(color), false);
        if (roles == null) {
            failed(player);
            return;
        }
        RoleSlots slots = collectRoles(roles, size);
        String json = toJson(match, slots);
        if (json == null) {
            failed(player);
            return;
        }
        sendCopyable(player, "betterremoval.message.probe_format_json", json);
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

    private static MutableComponent copyable(String value) {
        return Component.literal(value).withStyle(Style.EMPTY
                .withColor(ChatFormatting.WHITE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("betterremoval.message.probe_copy_hint"))));
    }

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

    private static String toJson(String match, RoleSlots roles) {
        if (roles.isEmpty()) {
            return null;
        }
        StringBuilder json = new StringBuilder("{\"match\":\"").append(match).append('"');
        appendJson(json, "input", roles.input());
        appendJson(json, "fuel", roles.fuel());
        appendJson(json, "output", roles.output());
        return json.append('}').toString();
    }

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

    private static void appendJson(StringBuilder json, String key, List<Integer> slots) {
        if (slots.isEmpty()) {
            return;
        }
        json.append(",\"").append(key).append("\":[");
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(slots.get(i).intValue());
        }
        json.append(']');
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

    private static String matchKey(BlockEntity blockEntity) {
        ResourceLocation id = SlotRules.blockId(blockEntity);
        return id != null ? id.toString() : "@" + blockEntity.getClass().getName();
    }

    private static SlotRole[] inventoryRoles(BlockEntity blockEntity) {
        if (!(blockEntity instanceof Container container)) {
            return null;
        }
        return AutoDetectSupport.inventoryRoles(container);
    }

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
