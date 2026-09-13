package closedw.br.experimental;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 实验性：主动探测。
 * <p>玩家处于"主动探测"模式时，按住修饰键右击容器，服务端依次跑三个探测后端：
 * ① 菜单只读：读容器自己 GUI 菜单里 {@code canInsert}/{@code canTakeItems}/{@code isEnabled} 声明；
 * ② 主动搬运：用探测物跑一遍容器自己的 {@code quickMove}（shift 点击的搬运规则），看完落到哪个槽；
 * ③ 库存接口：直接问 {@link Inventory#isValid} / {@code SidedInventory.canExtract}。
 * 三次探测的结果各自拼成一行规则 JSON 打印到聊天框，玩家挑一条复制进
 * {@code config/better-removal/containers/*.json}（或 Configured 的"容器槽位规则"）即可生效；
 * 某个后端判定不出任何槽位时，该行显示"无法探测"。只输出文本，不写任何文件；探测物会立刻还原，容器内容不变。
 */
public final class SlotProbeReport {

	private SlotProbeReport() {
	}

	/** 跑三次探测并把结果打印到该玩家的聊天框。 */
	public static void send(ServerPlayerEntity player, BlockEntity blockEntity) {
		String match = matchKey(blockEntity);
		int size = size(blockEntity);

		player.sendMessage(Text.translatable("better-removal.message.probe_header", match)
				.formatted(Formatting.AQUA), false);

		line(player, "better-removal.message.probe_menu", match, size,
				probe(() -> MenuSlotSupport.detect(blockEntity)), Formatting.GREEN);
		line(player, "better-removal.message.probe_transfer", match, size,
				probe(() -> toRoles(MenuTransferSupport.probeRaw(blockEntity), size)), Formatting.GOLD);
		line(player, "better-removal.message.probe_inventory", match, size,
				probe(() -> inventoryRoles(blockEntity)), Formatting.LIGHT_PURPLE);

		player.sendMessage(Text.translatable("better-removal.message.probe_hint")
				.formatted(Formatting.GRAY), false);
	}

	/** 打印一行：标签 + 规则 JSON（或"无法探测"）。 */
	private static void line(ServerPlayerEntity player, String labelKey, String match, int size,
			SlotRole[] roles, Formatting color) {
		String json = roles == null ? null : toJson(match, roles, size);
		MutableText line = Text.translatable(labelKey).formatted(color).append(Text.literal(": "));
		if (json == null) {
			line.append(Text.translatable("better-removal.message.probe_failed").formatted(Formatting.RED));
		}
		else {
			line.append(Text.literal(json).formatted(Formatting.WHITE));
		}
		player.sendMessage(line, false);
	}

	/** 探针数组 -> 规则 JSON；一个槽位都没判定出来时返回 null。 */
	private static String toJson(String match, SlotRole[] roles, int size) {
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
		if (input.isEmpty() && fuel.isEmpty() && output.isEmpty()) {
			// 三份都没结果时提示"无法探测"，不要给出一条什么都没说的空规则
			return null;
		}
		StringBuilder json = new StringBuilder("{\"match\":\"").append(match).append('"');
		append(json, "input", input);
		append(json, "fuel", fuel);
		append(json, "output", output);
		return json.append('}').toString();
	}

	/** 只写非空的角色数组：没提到的槽位规则不会去动 */
	private static void append(StringBuilder json, String key, List<Integer> slots) {
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

	private static SlotRole[] toRoles(Map<Integer, SlotRole> probed, int size) {
		if (probed == null || size < 0) {
			return null;
		}
		SlotRole[] roles = new SlotRole[size];
		for (Map.Entry<Integer, SlotRole> entry : probed.entrySet()) {
			int slot = entry.getKey();
			if (slot >= 0 && slot < size) {
				roles[slot] = entry.getValue();
			}
		}
		return roles;
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
}
