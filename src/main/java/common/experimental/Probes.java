package common.experimental;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.function.Predicate;

/**
 * 通用探测物：只用“能不能把这些物品放进去”推断槽位语义。
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

	private static final ItemStack[] INPUT = { new ItemStack(Items.RAW_IRON) };
	private static final ItemStack[] FUEL = { new ItemStack(Items.COAL) };

	private Probes() {
	}

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
