package closedw.br.container.detect;

import closedw.br.BetterRemoval;
import closedw.br.container.rule.RuleDirection;
import closedw.br.container.rule.SlotRule;
import closedw.br.container.rule.SlotRuleRegistry;
import closedw.br.container.rule.SlotRules;
import closedw.br.OutputSlotExtractor;
import closedw.br.slot.SlotRole;
import closedw.br.slot.SlotSet;


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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 未知容器的槽位判定。
 * 声明式规则{@link SlotRuleRegistry}）
 * 自动探测（{@link #isEnabledFor} 及其后）：靠白名单命中的启发式猜测，需要实验性总开关打开。
 * 两者都只作用于实现了 {@link Inventory} 的方块实体，且都注册在硬编码容器之后，
 */
public final class AutoDetectSupport {

	private static final Logger LOGGER = BetterRemoval.LOGGER;

	/** 用于控制日志频率：同一种容器只在探测结果变化时打印一次。 */
	private static final Map<String, String> LAST_LOGGED = new ConcurrentHashMap<>();

	private AutoDetectSupport() {
	}

	// 声明式规则
	/**
	 * 该方块是否有手写槽位规则。规则是显式声明，不受实验性总开关约束。
	 */
	public static boolean hasRule(BlockEntity blockEntity) {
		if (blockEntity == null || !(blockEntity instanceof Inventory)) {
			return false;
		}
		return SlotRuleRegistry.hasRule(blockEntity);
	}

	/**
	 * 按手写规则取槽位。规则就是最终结果，不会再退回任何猜测：
	 * 返回 null 表示「这条规则在该模式下没有槽位」。
	 */
	public static int[] getRuleSlotsForMode(BlockEntity blockEntity, SlotSet mode) {
		if (mode == null || !(blockEntity instanceof Inventory)) {
			return null;
		}
		SlotRule rule = SlotRuleRegistry.find(blockEntity);
		if (rule == null) {
			return null;
		}
		String mismatch = SlotRuleRegistry.sizeMismatch(blockEntity, rule);
		if (mismatch != null) {
			logSizeMismatch(blockEntity, rule, mismatch);
		}
		int[] slots;
		switch (mode) {
			case OUTPUT -> slots = ruleSlots(blockEntity, rule, RuleDirection.EXTRACT, Set.of(SlotRole.OUTPUT));
			case INPUT -> slots = ruleSlots(blockEntity, rule, RuleDirection.EXTRACT, Set.of(SlotRole.INPUT));
			case FUEL -> slots = ruleSlots(blockEntity, rule, RuleDirection.EXTRACT, Set.of(SlotRole.FUEL));
			// ALL = 输入 ∪ 燃料 ∪ 输出（忽略项与未声明项都不算）
			// 先各自求值再合并，不依赖方法实参的求值顺序
			case ALL -> {
				int[] inputs = ruleSlots(blockEntity, rule, RuleDirection.EXTRACT, Set.of(SlotRole.INPUT));
				int[] fuels = ruleSlots(blockEntity, rule, RuleDirection.EXTRACT, Set.of(SlotRole.FUEL));
				int[] outputs = ruleSlots(blockEntity, rule, RuleDirection.EXTRACT, Set.of(SlotRole.OUTPUT));
				slots = union(inputs, fuels, outputs);
			}
			default -> slots = new int[0];
		}
		logDetection(blockEntity, "rule:" + rule.match(), toRoles(blockEntity, rule));
		return slots.length == 0 ? null : slots;
	}

	/** 按手写规则取「放入」方向的槽位；规格与 {@link #getRuleSlotsForMode} 一致。 */
	public static int[] getRuleDepositSlots(BlockEntity blockEntity, SlotSet mode) {
		if (mode != SlotSet.INPUT && mode != SlotSet.FUEL) {
			return null;
		}
		if (!(blockEntity instanceof Inventory)) {
			return null;
		}
		SlotRule rule = SlotRuleRegistry.find(blockEntity);
		if (rule == null) {
			return null;
		}
		Set<SlotRole> wanted = mode == SlotSet.INPUT ? Set.of(SlotRole.INPUT) : Set.of(SlotRole.FUEL);
		int[] slots = ruleSlots(blockEntity, rule, RuleDirection.DEPOSIT, wanted);
		return slots.length == 0 ? null : slots;
	}

	private static int[] ruleSlots(BlockEntity blockEntity, SlotRule rule, RuleDirection direction, Set<SlotRole> wanted) {
		return SlotRuleRegistry.slotsFor(blockEntity, rule, direction, wanted);
	}

	/** 该规则是否要求在取出前额外做「能否取出」的校验（规则里的 {@code sided} 字段）。 */
	public static boolean ruleRequiresSidedCheck(BlockEntity blockEntity) {
		SlotRule rule = SlotRuleRegistry.find(blockEntity);
		return rule != null && rule.sided();
	}

	/**
	 * 「这个槽位现在能否被取出」的通用判定。
	 * 只有实现了 {@link SidedInventory} 的容器才声明得起这件事：{@code canExtract} 返回 false 就表示
	 * 该槽位不允许被自动化取出（升级槽、展示槽之类）。没有 sided 信息的容器一律视为可取出，
	 * @param inventory 容器的原版视图，可为 null（此时无法判定，按可取出处理）
	 */
	public static boolean canExtract(Inventory inventory, int slot, ItemStack stack) {
		if (!(inventory instanceof SidedInventory sided)) {
			return true;
		}
		ItemStack probe = stack == null || stack.isEmpty() ? new ItemStack(Items.STONE) : stack;
		for (Direction side : Direction.values()) {
			try {
				if (sided.canExtract(slot, probe, side)) {
					return true;
				}
			}
			catch (Throwable ignored) {
				// 模组自定义判定抛异常时按可取出处理，宁可照旧也不要静默失效
				return true;
			}
		}
		return false;
	}

	/** 合并多个槽位号（有序去重）。 */
	private static int[] union(int[]... groups) {
		Set<Integer> merged = new LinkedHashSet<>();
		for (int[] group : groups) {
			for (int slot : group) {
				merged.add(slot);
			}
		}
		int[] result = new int[merged.size()];
		int cursor = 0;
		for (int slot : merged) {
			result[cursor++] = slot;
		}
		return result;
	}

	/** 规则声明的角色表；仅用于日志。 */
	private static SlotRole[] toRoles(BlockEntity blockEntity, SlotRule rule) {
		if (!(blockEntity instanceof Inventory inventory)) {
			return new SlotRole[0];
		}
		try {
			int size = inventory.size();
			SlotRole[] extract = rule.extract().toRoles(size);
			SlotRole[] deposit = rule.deposit().toRoles(size);
			SlotRole[] merged = new SlotRole[size];
			for (int slot = 0; slot < size; slot++) {
				merged[slot] = extract[slot] != SlotRole.NONE ? extract[slot] : deposit[slot];
			}
			return merged;
		}
		catch (Throwable t) {
			return new SlotRole[0];
		}
	}

	private static void logSizeMismatch(BlockEntity blockEntity, SlotRule rule, String message) {
		Identifier id = SlotRules.blockId(blockEntity);
		String key = (id != null ? id.toString() : blockEntity.getClass().getName()) + "|" + rule.match();
		if (message.equals(LAST_LOGGED.put(key, message))) {
			return;
		}
		LOGGER.warn("[container] rule for {} ({}): {}", key, rule.match(), message);
	}

	// 自动探测（实验性，需要白名单命中）
	/**
	 * 该方块是否应由自动探测接管（需要实验性总开关 + 白名单命中）。
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
	public static int[] getSlotsForMode(BlockEntity blockEntity, SlotSet mode) {
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
	public static int[] getDepositSlots(BlockEntity blockEntity, SlotSet mode) {
		if (mode != SlotSet.INPUT && mode != SlotSet.FUEL) {
			return null;
		}
		return getSlotsForMode(blockEntity, mode);
	}

	private static int[] slotsForMode(SlotRole[] roles, SlotSet mode) {
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
	 * 依次尝试各个探测后端，返回第一个能给出结果的那个。
	 */
	private static Detection detect(BlockEntity blockEntity, Inventory inventory) {
		// 优先级1：GUI 菜单语义（只读判定）
		SlotRole[] menuRoles = MenuSlotSupport.detect(blockEntity);
		if (menuRoles != null && menuRoles.length == inventory.size()) {
			return new Detection(menuRoles, "menu");
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
	 * 只用原版容器接口推断：接受通用探测物 → 输入/燃料；
	 * 全部拒绝但能取出 → 输出；
	 * 只认自己已有的特定物品 → 受限输入槽，忽略。
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

	static boolean canExtractAnySide(SidedInventory inventory, int slot) {
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
		List<String> whitelist = OutputSlotExtractor.getConfigList("auto_detect_whitelist");
		if (whitelist.isEmpty()) {
			// 兼容旧键名
			whitelist = OutputSlotExtractor.getConfigList("experimental_auto_detect_whitelist");
		}
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
		LOGGER.info("[container] {} -> {}", key, value);
	}

	private record Detection(SlotRole[] roles, String source) {
	}
}
