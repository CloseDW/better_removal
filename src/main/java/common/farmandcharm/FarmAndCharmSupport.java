package common.farmandcharm;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * [Let's Do] Farm & Charm的兼容。
 * 三个方块实体都实现原版 Inventory，物品读写走通用路径，这里只做类型检测和槽位表。
 *   厨房锅/烤盘（8格）：0-5食材 6盘(消耗品) 7输出
 *   炉灶（5格）：0输出 1-3食材 4燃料
 */
public final class FarmAndCharmSupport {

	private FarmAndCharmSupport() {
	}

	public static final String MOD_ID = "farm_and_charm";

	private static final String COOKING_POT_CLASS = "net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity";
	private static final String ROASTER_CLASS = "net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity";
	private static final String STOVE_CLASS = "net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity";

	private static final int[] POT_INPUT_SLOTS = { 0, 1, 2, 3, 4, 5 };
	private static final int[] POT_DISH_SLOTS = { 6 };
	private static final int[] POT_OUTPUT_SLOTS = { 7 };
	private static final int[] STOVE_OUTPUT_SLOTS = { 0 };
	private static final int[] STOVE_INPUT_SLOTS = { 1, 2, 3 };
	private static final int[] STOVE_FUEL_SLOTS = { 4 };

	private static final boolean LOADED = checkLoaded();

	private static boolean checkLoaded() {
		try {
			return FabricLoader.getInstance().isModLoaded(MOD_ID);
		}
		catch (Throwable t) {
			return false;
		}
	}

	public static boolean isLoaded() {
		return LOADED;
	}

	public static boolean isCookingPot(BlockEntity blockEntity) {
		return isInstance(blockEntity, COOKING_POT_CLASS);
	}

	public static boolean isRoaster(BlockEntity blockEntity) {
		return isInstance(blockEntity, ROASTER_CLASS);
	}

	public static boolean isStove(BlockEntity blockEntity) {
		return isInstance(blockEntity, STOVE_CLASS);
	}

	private static boolean isInstance(BlockEntity blockEntity, String className) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			return Class.forName(className).isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	public static String getConfigKey(BlockEntity blockEntity) {
		if (isCookingPot(blockEntity)) {
			return "fc_cooking_pot";
		}
		if (isRoaster(blockEntity)) {
			return "roaster";
		}
		if (isStove(blockEntity)) {
			return "stove";
		}
		return null;
	}

	public static int[] getInputSlots(BlockEntity blockEntity) {
		if (isCookingPot(blockEntity) || isRoaster(blockEntity)) {
			return POT_INPUT_SLOTS;
		}
		if (isStove(blockEntity)) {
			return STOVE_INPUT_SLOTS;
		}
		return null;
	}

	public static int[] getOutputSlots(BlockEntity blockEntity) {
		if (isCookingPot(blockEntity) || isRoaster(blockEntity)) {
			return POT_OUTPUT_SLOTS;
		}
		if (isStove(blockEntity)) {
			return STOVE_OUTPUT_SLOTS;
		}
		return null;
	}

	public static int[] getFuelSlots(BlockEntity blockEntity) {
		if (isCookingPot(blockEntity) || isRoaster(blockEntity)) {
			return POT_DISH_SLOTS;
		}
		if (isStove(blockEntity)) {
			return STOVE_FUEL_SLOTS;
		}
		return null;
	}
}
