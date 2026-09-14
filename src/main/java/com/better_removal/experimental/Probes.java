package com.better_removal.experimental;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * 通用探测物：不做任何模组专属判断，只用“能不能把这些物品放进去”推断槽位语义。
 */
final class Probes {

	static final ItemStack[] GENERIC = {
			new ItemStack(Items.STONE),
			new ItemStack(Items.RAW_IRON),
			new ItemStack(Items.COAL),
			new ItemStack(Items.OAK_LOG),
			new ItemStack(Items.BUCKET),
			new ItemStack(Items.BOWL),
			new ItemStack(Items.GLASS_BOTTLE)
	};

	/** 可加工输入探测物：不能用既是燃料又是可加工物的物品。 */
	private static final ItemStack[] INPUT = { new ItemStack(Items.RAW_IRON) };
	/** 燃料探测物。 */
	private static final ItemStack[] FUEL = { new ItemStack(Items.COAL) };

	private Probes() {
	}

	/**
	 * 区分输入与燃料。调用前应已确认 {@link #GENERIC} 至少有一个探测物被接受。
	 */
	static SlotRole fuelOrInput(Predicate<ItemStack> accepts) {
		boolean input = anyOf(accepts, INPUT);
		boolean fuel = anyOf(accepts, FUEL);
		return fuel && !input ? SlotRole.FUEL : SlotRole.INPUT;
	}

	static boolean anyOf(Predicate<ItemStack> accepts, ItemStack[] probes) {
		for (ItemStack probe : probes) {
			if (accepts.test(probe)) {
				return true;
			}
		}
		return false;
	}
}
