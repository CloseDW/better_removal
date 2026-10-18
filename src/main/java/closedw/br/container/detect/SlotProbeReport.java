package closedw.br.container.detect;

import closedw.br.container.rule.SlotRules;
import closedw.br.slot.SlotRole;


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
 * 玩家处于"主动探测"模式时，按住修饰键右击容器，服务端只跑一个后端：
 * 直接问 {@link Inventory#isValid} / {@code SidedInventory.canExtract}（inventory 接口）。
 * 输出一行可点击复制的规则串，粘进 Configured 的"容器槽位规则（声明式）"即可。
 * 判定不出任何槽位时显示"无法探测"。只输出文本。
 * <p>
 * 早期版本还会跑一个"菜单只读"后端，已按需求删除：菜单槽位号与容器槽位号是两套编号，
 * 拿菜单语义去推容器槽位会打印出指错槽位的规则串。够不着的容器改用「手动指认」覆盖
 * （见 {@code closedw.br.container.menu}）。
 */
public final class SlotProbeReport {

	private SlotProbeReport() {
	}

	/** 跑探测并把结果打印到该玩家的聊天框。 */
	public static void send(ServerPlayerEntity player, BlockEntity blockEntity) {
		String match = matchKey(blockEntity);
		int size = size(blockEntity);

		player.sendMessage(Text.translatable("better-removal.message.probe_header", match)
				.formatted(Formatting.AQUA), false);

		report(player, "better-removal.message.probe_inventory", match, size,
				probe(() -> inventoryRoles(blockEntity)), Formatting.LIGHT_PURPLE);
	}

	/** 打印一个后端的标题行，以及一行可复制的规则串。 */
	private static void report(ServerPlayerEntity player, String labelKey, String match, int size,
			SlotRole[] roles, Formatting color) {
		player.sendMessage(Text.translatable(labelKey).formatted(color), false);
		if (roles == null) {
			failed(player);
			return;
		}
		RoleSlots slots = collectRoles(roles, size);
		if (slots.isEmpty()) {
			failed(player);
			return;
		}
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
		Identifier id = SlotRules.blockId(blockEntity);
		return id != null ? id.toString() : "@" + blockEntity.getClass().getName();
	}

	private static SlotRole[] inventoryRoles(BlockEntity blockEntity) {
		if (!(blockEntity instanceof Inventory inventory)) {
			return null;
		}
		return SlotDetection.inventoryRoles(inventory);
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
