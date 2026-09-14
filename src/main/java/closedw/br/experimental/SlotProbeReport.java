package closedw.br.experimental;

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
 * 玩家处于"主动探测"模式时，按住修饰键右击容器，服务端依次跑两个探测后端：
 * ① 菜单只读：读容器自己 GUI 菜单里 {@code canInsert}/{@code canTakeItems}/{@code isEnabled} 声明；
 * ② 库存接口：直接问 {@link Inventory#isValid} / {@code SidedInventory.canExtract}。
 * 每个后端各输出两行，点击即可复制：JSON 粘进 {@code config/better-removal/containers/*.json}，
 * 规则串粘进 Configured 的"容器槽位规则"。
 * 某个后端判定不出任何槽位时显示"无法探测"。只输出文本。
 */
public final class SlotProbeReport {

	private SlotProbeReport() {
	}

	/** 跑两次探测并把结果打印到该玩家的聊天框。 */
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

	/** 打印一个后端的标题行，以及 JSON / 规则串两行可复制的文本。 */
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

	/** 可点击复制的文本：点击复制到剪贴板，悬停给出提示。 */
	private static MutableText copyable(String value) {
		return Text.literal(value).setStyle(Style.EMPTY
				.withColor(Formatting.WHITE)
				.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						Text.translatable("better-removal.message.probe_copy_hint"))));
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

	/** JSON 规则；一个槽位都没判定出来时返回 null。 */
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

	/** 规则匹配串：优先用方块ID，取不到时退回"@方块实体类名" */
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

	/** 容器槽位数；不是 {@link Inventory} 时返回 -1 */
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
