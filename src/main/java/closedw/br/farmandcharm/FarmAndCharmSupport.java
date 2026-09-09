package closedw.br.farmandcharm;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * [Let's Do] Farm & Charm的兼容
 * 使用反射访问，类加载 / 判断全部放在 try/catch
 * 三个方块实体都直接实现原版 WorldlyContainer（Yarn SidedInventory -> Inventory），
 * 内部是 NonNullList<ItemStack>，物品读写走通用 Inventory 路径，这里只做类型检测和槽位表。
 * 槽位布局：
 *   厨房锅（CookingPotBlockEntity，8格）：0-5食材 6盘(配方消耗的碗) 7输出
 *   烤盘（RoasterBlockEntity，8格）：0-5食材 6盘 7输出
 *   炉灶（StoveBlockEntity，5格）：0输出 1-3食材 4燃料
 * 注意：canPlaceItem 未重写（isValid 恒真），放入/补货的过滤完全依赖这里的槽位表。
 * 盘槽（锅/烤盘的槽6）映射到 FUEL 预设：锅和烤盘必须有盘才能工作，盘是消耗品
 */
public final class FarmAndCharmSupport {

	private FarmAndCharmSupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "farm_and_charm";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String COOKING_POT_CLASS = "net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity";
	private static final String ROASTER_CLASS = "net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity";
	private static final String STOVE_CLASS = "net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity";

	/**
	 * 厨房锅/烤盘的食材槽索引（0-5）
	 */
	private static final int[] POT_INPUT_SLOTS = { 0, 1, 2, 3, 4, 5 };

	/**
	 * 厨房锅/烤盘的盘槽索引（6，配方消耗的碗/盘，映射到FUEL预设）
	 */
	private static final int[] POT_DISH_SLOTS = { 6 };

	/**
	 * 厨房锅/烤盘的输出槽索引（7）
	 */
	private static final int[] POT_OUTPUT_SLOTS = { 7 };

	/**
	 * 炉灶的输出槽索引（0）
	 */
	private static final int[] STOVE_OUTPUT_SLOTS = { 0 };

	/**
	 * 炉灶的食材槽索引（1-3）
	 */
	private static final int[] STOVE_INPUT_SLOTS = { 1, 2, 3 };

	/**
	 * 炉灶的燃料槽索引（4）
	 */
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

	/**
	 * 是否安装了 Farm & Charm。
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 判断给定方块是否为厨房锅。
	 */
	public static boolean isCookingPot(BlockEntity blockEntity) {
		return isInstance(blockEntity, COOKING_POT_CLASS);
	}

	/**
	 * 判断给定方块是否为烤盘。
	 */
	public static boolean isRoaster(BlockEntity blockEntity) {
		return isInstance(blockEntity, ROASTER_CLASS);
	}

	/**
	 * 判断给定方块是否为炉灶。
	 */
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

	/**
	 * 返回容器对应的配置键：fc_cooking_pot / roaster / stove，不属于这三个方块返回null。
	 */
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

	/**
	 * 取出/放入的输入槽（食材）：锅/烤盘0-5，炉灶1-3。
	 */
	public static int[] getInputSlots(BlockEntity blockEntity) {
		if (isCookingPot(blockEntity) || isRoaster(blockEntity)) {
			return POT_INPUT_SLOTS;
		}
		if (isStove(blockEntity)) {
			return STOVE_INPUT_SLOTS;
		}
		return null;
	}

	/**
	 * 取出的输出槽：锅/烤盘7，炉灶0。
	 */
	public static int[] getOutputSlots(BlockEntity blockEntity) {
		if (isCookingPot(blockEntity) || isRoaster(blockEntity)) {
			return POT_OUTPUT_SLOTS;
		}
		if (isStove(blockEntity)) {
			return STOVE_OUTPUT_SLOTS;
		}
		return null;
	}

	/**
	 * FUEL预设槽（消耗品槽）：锅/烤盘为盘槽6，炉灶为燃料槽4。
	 */
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
