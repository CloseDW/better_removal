package closedw.br.experimental;

import closedw.br.BetterRemoval;
import closedw.br.ExtractionMode;
import closedw.br.OutputSlotExtractor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 实验性：未知容器的槽位判定（默认关闭）。
 * 只作用于{@link Inventory} 类型的方块实体。现有硬编码兼容完全不受影响——只有
 * {@code OutputSlotExtractor.getConfigKey} 返回 null 的未知容器才会走到这里。
 */
public final class AutoDetectSupport {

	private static final Logger LOGGER = BetterRemoval.LOGGER;

	/** 用于控制日志频率：同一种容器只在探测结果变化时打印一次。 */
	private static final Map<String, String> LAST_LOGGED = new ConcurrentHashMap<>();

	private AutoDetectSupport() {
	}

	/**
	 * 该方块是否有手写槽位规则。规则是显式声明，不需要白名单，但同样受实验性总开关约束。
	 */
	public static boolean hasRule(BlockEntity blockEntity) {
		if (blockEntity == null || !(blockEntity instanceof Inventory)) {
			return false;
		}
		if (!OutputSlotExtractor.isExperimentalEnabled()) {
			return false;
		}
		return SlotRules.find(blockEntity) != null;
	}

	/**
	 * 按手写规则取槽位。规则就是最终结果，不会再退回任何猜测：
	 * 返回 null 表示“这条规则在该模式下没有槽位”。
	 */
	public static int[] getRuleSlotsForMode(BlockEntity blockEntity, ExtractionMode mode) {
		if (mode == null || !(blockEntity instanceof Inventory inventory)) {
			return null;
		}
		if (!OutputSlotExtractor.isExperimentalEnabled()) {
			return null;
		}
		SlotRule rule = SlotRules.find(blockEntity);
		if (rule == null) {
			return null;
		}
		SlotRole[] roles;
		try {
			roles = rule.apply(inventory.size());
		}
		catch (Throwable t) {
			return null;
		}
		logDetection(blockEntity, "rule:" + rule.match(), roles);
		return slotsForMode(roles, mode);
	}

	/**
	 * 该方块是否应由自动探测接管（需要白名单命中）。
	 */
	public static boolean isEnabledFor(BlockEntity blockEntity) {
		if (blockEntity == null) {
			return false;
		}
		if (!OutputSlotExtractor.isExperimentalEnabled()) {
			return false;
		}
		if (!(blockEntity instanceof Inventory)) {
			// 没有实现原版容器接口的方块（纯自定义库存）槽位号没有意义，无法按槽位取出
			return false;
		}
		return matchesWhitelist(blockEntity);
	}

	/**
	 * 取出模式：返回自动探测出的槽位。返回 null 表示该模式下无可用槽位。
	 */
	public static int[] getSlotsForMode(BlockEntity blockEntity, ExtractionMode mode) {
		if (mode == null || !(blockEntity instanceof Inventory inventory)) {
			return null;
		}
		Detection detection = detect(blockEntity, inventory);
		if (detection == null) {
			return null;
		}
		logDetection(blockEntity, detection.source(), detection.roles());
		return slotsForMode(detection.roles(), mode);
	}

	/**
	 * 放入模式：INPUT/FUEL 分别返回探测出的输入/燃料槽位。
	 */
	public static int[] getDepositSlots(BlockEntity blockEntity, ExtractionMode mode) {
		if (mode != ExtractionMode.INPUT && mode != ExtractionMode.FUEL) {
			return null;
		}
		return getSlotsForMode(blockEntity, mode);
	}

	private static int[] slotsForMode(SlotRole[] roles, ExtractionMode mode) {
		Set<Integer> slots = new LinkedHashSet<>();
		for (int slot = 0; slot < roles.length; slot++) {
			switch (mode) {
				case OUTPUT -> {
					if (roles[slot] == SlotRole.OUTPUT) {
						slots.add(slot);
					}
				}
				case INPUT -> {
					if (roles[slot] == SlotRole.INPUT) {
						slots.add(slot);
					}
				}
				case FUEL -> {
					if (roles[slot] == SlotRole.FUEL) {
						slots.add(slot);
					}
				}
				case ALL -> {
					if (roles[slot] != SlotRole.NONE) {
						slots.add(slot);
					}
				}
			}
		}
		if (slots.isEmpty()) {
			return null;
		}
		int[] result = new int[slots.size()];
		int index = 0;
		for (int slot : slots) {
			result[index++] = slot;
		}
		return result;
	}

	/**
	 * 依次尝试各个探测后端，返回第一个能给出结果的那个
	 */
	private static Detection detect(BlockEntity blockEntity, Inventory inventory) {
		// 优先级1：GUI 菜单语义（只读判定），命中后再用 quickMove 主动探测修正输入/燃料槽
		SlotRole[] menuRoles = MenuSlotSupport.detect(blockEntity);
		if (menuRoles != null && menuRoles.length == inventory.size()) {
			String source = "menu";
			if (OutputSlotExtractor.isTransferProbeEnabled()) {
				SlotRole[] refined = MenuTransferSupport.refine(blockEntity, menuRoles);
				if (refined != null) {
					menuRoles = refined;
					source = "menu+transfer";
				}
			}
			return new Detection(menuRoles, source);
		}

		// 优先级2：容器接口启发式
		SlotRole[] roles = inventoryRoles(inventory);
		for (SlotRole role : roles) {
			if (role != SlotRole.NONE) {
				return new Detection(roles, "inventory");
			}
		}
		return null;
	}

	/**
	 * 清空各种探测缓存（{@code /br reload} 时调用）：让改完配置/规则后重新探测。
	 */
	public static void clearCaches() {
		MenuTransferSupport.clearCache();
	}

	/**
	 * 只用原版容器接口推断：接受通用探测物 → 输入/燃料；
	 *全部拒绝但能取出 → 输出；
	 *只认自己已有的特定物品 → 受限输入槽，忽略。
	 *（包内可见：主动探测报告也要用这一份结果）
	 */
	static SlotRole[] inventoryRoles(Inventory inventory) {
		int size;
		try {
			size = inventory.size();
		}
		catch (Throwable t) {
			return new SlotRole[0];
		}
		SlotRole[] roles = new SlotRole[size];
		SidedInventory sided = inventory instanceof SidedInventory s ? s : null;
		for (int slot = 0; slot < size; slot++) {
			int index = slot;
			Predicate<ItemStack> accepts = stack -> isValid(inventory, index, stack);
			if (Probes.anyOf(accepts, Probes.GENERIC)) {
				roles[slot] = Probes.fuelOrInput(accepts);
				continue;
			}
			// 通用探测物全被拒，但接受自己已有的物品 => 只认特定物品的受限槽位（升级件/工具/模板）
			ItemStack existing = getStack(inventory, slot);
			if (!existing.isEmpty() && accepts.test(existing)) {
				roles[slot] = SlotRole.NONE;
				continue;
			}
			// 有 sided 信息时以 canExtract 为准（可以排除只放不取的升级槽）；
			// 否则退化为“不能放入 => 视为输出”的推测。
			boolean extractable = sided != null ? canExtractAnySide(sided, slot) : true;
			roles[slot] = extractable ? SlotRole.OUTPUT : SlotRole.NONE;
		}
		return roles;
	}

	private static boolean isValid(Inventory inventory, int slot, ItemStack stack) {
		try {
			return inventory.isValid(slot, stack);
		}
		catch (Throwable ignored) {
			// 模组自定义过滤逻辑可能抛异常，视为不接受
			return false;
		}
	}

	private static ItemStack getStack(Inventory inventory, int slot) {
		try {
			ItemStack stack = inventory.getStack(slot);
			return stack == null ? ItemStack.EMPTY : stack;
		}
		catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}

	private static boolean canExtractAnySide(SidedInventory inventory, int slot) {
		ItemStack probe = new ItemStack(Items.STONE);
		for (Direction side : Direction.values()) {
			try {
				if (inventory.canExtract(slot, probe, side)) {
					return true;
				}
			}
			catch (Throwable ignored) {
				// 同上
			}
		}
		return false;
	}

	/**
	 * 白名单匹配规则与 {@link SlotRules#matchesEntry} 完全一致（忽略大小写）：
	 * {@code *} / 模组ID / {@code 模组ID:*} / {@code 模组ID:方块ID} / {@code @类名前缀}。
	 */
	private static boolean matchesWhitelist(BlockEntity blockEntity) {
		List<String> whitelist = OutputSlotExtractor.getConfigList("experimental_auto_detect_whitelist");
		if (whitelist.isEmpty()) {
			return false;
		}
		for (String entry : whitelist) {
			if (SlotRules.matchesEntry(blockEntity, entry)) {
				return true;
			}
		}
		return false;
	}

	private static void logDetection(BlockEntity blockEntity, String source, SlotRole[] roles) {
		Identifier id = SlotRules.blockId(blockEntity);
		String key = (id != null ? id.toString() : blockEntity.getClass().getName());
		StringBuilder signature = new StringBuilder(source).append(" |");
		for (int slot = 0; slot < roles.length; slot++) {
			signature.append(' ').append(slot).append('=').append(roles[slot].name());
		}
		String value = signature.toString();
		if (value.equals(LAST_LOGGED.put(key, value))) {
			return;
		}
		LOGGER.info("[auto-detect] {} -> {}", key, value);
	}

	private record Detection(SlotRole[] roles, String source) {
	}
}
