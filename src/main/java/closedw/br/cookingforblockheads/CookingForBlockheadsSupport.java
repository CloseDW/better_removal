package closedw.br.cookingforblockheads;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;

/**
 * Cooking for Blockheads烤炉的兼容
 * 使用反射访问，try/catch
 * 烤炉槽位布局（OvenBlockEntity 内部容器，共20格）：
 *   0-2   输入槽（仅收可熔炼物品，canPlaceItem过滤）
 *   3     燃料槽
 *   4-6   输出槽
 *   7-15  3x3加工格（9路并行烹饪，各有独立计时；取出会中断该格烹饪）
 *   16-19 工具槽
 * 烤炉方块实体（BalmBlockEntity）本身不是Inventory，
 * 但 getInternalContainer() 返回的 Balm DefaultContainer 实现了原版 Inventory，
 * 反射拿到后可直接复用通用的 takeSlots / depositToInventory / restockFromInventory。
 * 容器 setItem 会触发 slotChanged -> setChanged -> 下个tick同步客户端，无需手动sync。
 */
public final class CookingForBlockheadsSupport {

	private CookingForBlockheadsSupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "cookingforblockheads";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String OVEN_CLASS = "net.blay09.mods.cookingforblockheads.tile.OvenBlockEntity";

	/**
	 * CFB配置类（getActive() 静态方法返回配置数据，其中有 disallowOvenAutomation 字段）
	 */
	private static final String CONFIG_CLASS = "net.blay09.mods.cookingforblockheads.CookingForBlockheadsConfig";

	/**
	 * 输入槽索引（0-2）
	 */
	private static final int[] INPUT_SLOTS = { 0, 1, 2 };

	/**
	 * 燃料槽索引（3）
	 */
	private static final int[] FUEL_SLOTS = { 3 };

	/**
	 * 输出槽索引（4-6）
	 */
	private static final int[] OUTPUT_SLOTS = { 4, 5, 6 };

	/**
	 * ALL模式槽位：输入+燃料+输出（不含加工格7-15与工具格16-19）
	 */
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

	/**
	 * 是否安装了 Cooking for Blockheads。
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 判断给定方块是否为烤炉（含全部染色变体，按方块实体类型判断）。
	 */
	public static boolean isOven(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName(OVEN_CLASS);
			return clazz.isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * CFB配置中的“禁止烤炉自动化”（disallowOvenAutomation）是否开启。
	 * 开启时本模组对烤炉不动作（与漏斗等自动化同等对待）。
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
	 * 获取烤炉的内部容器
	 * 返回null表示不可用
	 * 注意：开关由调用方通过 isAutomationDisallowed() 检查。
	 */
	public static Inventory getInternalInventory(BlockEntity blockEntity) {
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

	/**
	 * 获取烤炉的输入槽索引（0-2）。
	 */
	public static int[] getInputSlots() {
		return INPUT_SLOTS;
	}

	/**
	 * 获取烤炉的燃料槽索引（3）。
	 */
	public static int[] getFuelSlots() {
		return FUEL_SLOTS;
	}

	/**
	 * 获取烤炉的输出槽索引（4-6）。
	 */
	public static int[] getOutputSlots() {
		return OUTPUT_SLOTS;
	}

	/**
	 * 获取烤炉ALL模式的槽位（0-6，输入+燃料+输出）。
	 */
	public static int[] getAllSlots() {
		return ALL_SLOTS;
	}
}
