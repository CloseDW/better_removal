package common.experimental;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

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

	public static void send(ServerPlayerEntity player, BlockEntity blockEntity) {
		String match = matchKey(blockEntity);
		int size = size(blockEntity);

		player.sendMessage(Text.translatable("better-removal.message.probe_header", match)
				.formatted(Formatting.AQUA), false);

		report(player, "better-removal.message.probe_menu", match, size,
				probe(() -> MenuSlotSupport.detect(blockEntity)), Formatting.GREEN);
		report(player, "better-removal.message.probe_inventory", match, size,
				probe(() -> inventoryRoles(blockEntity)), Formatting.LIGHT_PURPLE);
	}

	private static void report(ServerPlayerEntity player, String labelKey, String match, int size,
			SlotRole[] roles, Formatting color) {
		player.sendMessage(Text.translatable(labelKey).formatted(color), false);
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
		sendCopyable(player, "better-removal.message.probe_format_json", json);
		sendCopyable(player, "better-removal.message.probe_format_rules", toConfigRule(match, slots));
	}

	private static void failed(ServerPlayerEntity player) {
		player.sendMessage(Text.literal("  ")
				.append(Text.translatable("better-removal.message.probe_failed").formatted(Formatting.RED)), false);
	}

	private static void sendCopyable(ServerPlayerEntity player, String labelKey, String value) {
		MutableText line = Text.literal("  ")
				.append(Text.translatable(labelKey).formatted(Formatting.GRAY))
				.append(Text.literal(": "))
				.append(copyable(value));
		player.sendMessage(line, false);
	}

	private static MutableText copyable(String value) {
		return Text.literal(value).setStyle(Style.EMPTY
				.withColor(Formatting.WHITE)
				.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						Text.translatable("better-removal.message.probe_copy_hint"))));
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
		Identifier id = SlotRules.blockId(blockEntity);
		return id != null ? id.toString() : "@" + blockEntity.getClass().getName();
	}

	private static SlotRole[] inventoryRoles(BlockEntity blockEntity) {
		if (!(blockEntity instanceof Inventory inventory)) {
			return null;
		}
		return AutoDetectSupport.inventoryRoles(inventory);
	}

	private static int size(BlockEntity blockEntity) {
		if (!(blockEntity instanceof Inventory inventory)) {
			return -1;
		}
		try {
			return inventory.size();
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
