package common.cookingforblockheads;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;

/**
 * Cooking for Blockheads烤炉的兼容。
 * 烤炉方块实体本身不是Inventory，反射 getInternalContainer() 拿内部容器后复用通用读写。
 * 内部容器20格：0-2输入 3燃料 4-6输出 7-15加工格 16-19工具槽。
 */
public final class CookingForBlockheadsSupport {

	private CookingForBlockheadsSupport() {
	}

	public static final String MOD_ID = "cookingforblockheads";

	private static final String OVEN_CLASS = "net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity";
	private static final String CONFIG_CLASS = "net.blay09.mods.cookingforblockheads.CookingForBlockheadsConfig";

	private static final int[] INPUT_SLOTS = { 0, 1, 2 };
	private static final int[] FUEL_SLOTS = { 3 };
	private static final int[] OUTPUT_SLOTS = { 4, 5, 6 };
	private static final int[] ALL_SLOTS = { 0, 1, 2, 3, 4, 5, 6 };

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

	public static boolean isOven(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			return Class.forName(OVEN_CLASS).isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * CFB配置中的“禁止烤炉自动化”（disallowOvenAutomation）是否开启。
	 * 读不到配置时按未开启处理。
	 */
	public static boolean isAutomationDisallowed() {
		if (!LOADED) {
			return false;
		}
		try {
			Class<?> configClass = Class.forName(CONFIG_CLASS);
			Object data = configClass.getMethod("getActive").invoke(null);
			if (data == null) {
				return false;
			}
			return data.getClass().getField("disallowOvenAutomation").getBoolean(data);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 获取烤炉的内部容器。返回null表示不可用。
	 */
	public static Inventory getInternalContainer(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return null;
		}
		try {
			Class<?> clazz = Class.forName(OVEN_CLASS);
			if (!clazz.isInstance(blockEntity)) {
				return null;
			}
			Object container = clazz.getMethod("getInternalContainer").invoke(blockEntity);
			return container instanceof Inventory inventory ? inventory : null;
		}
		catch (Throwable t) {
			return null;
		}
	}

	public static int[] getInputSlots() {
		return INPUT_SLOTS;
	}

	public static int[] getFuelSlots() {
		return FUEL_SLOTS;
	}

	public static int[] getOutputSlots() {
		return OUTPUT_SLOTS;
	}

	public static int[] getAllSlots() {
		return ALL_SLOTS;
	}
}
