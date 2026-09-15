package common.experimental;

import common.ExtractionMode;
import common.OutputSlotExtractor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 实验性：未知容器的槽位判定（默认关闭）。
 */
public final class AutoDetectSupport {

	private static final Logger LOGGER = LoggerFactory.getLogger("better-removal");

	private static final Map<String, String> LAST_LOGGED = new ConcurrentHashMap<>();

	private AutoDetectSupport() {
	}

	public static boolean hasRule(BlockEntity blockEntity) {
		if (blockEntity == null || !(blockEntity instanceof Inventory)) {
			return false;
		}
		if (!OutputSlotExtractor.isExperimentalEnabled()) {
			return false;
		}
		return SlotRules.find(blockEntity) != null;
	}

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

	public static boolean isEnabledFor(BlockEntity blockEntity) {
		if (blockEntity == null) {
			return false;
		}
		if (!OutputSlotExtractor.isExperimentalEnabled()) {
			return false;
		}
		if (!(blockEntity instanceof Inventory)) {
			return false;
		}
		return matchesWhitelist(blockEntity);
	}

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
			ItemStack existing = getStack(inventory, slot);
			if (!existing.isEmpty() && accepts.test(existing)) {
				roles[slot] = SlotRole.NONE;
				continue;
			}
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
