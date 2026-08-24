package common.crabbersdelight;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Crabber's Delight捕蟹笼
 *
 * 使用反射访问，类加载 / 判断全部放在 try/catch
 *
 * 捕蟹笼槽位布局（CrabTrapBlockEntity）：
 *   0    诱饵槽
 *   1-9  捕获物槽
 *
 * 该方块实体实现了原版 {@link net.minecraft.inventory.SidedInventory}（Yarn 映射），
 * 因此可直接作为 {@link net.minecraft.inventory.Inventory} 用标准方法访问物品。
 */
public final class CrabTrapSupport {

	private CrabTrapSupport() {
	}

	/**
	 * mod id。
	 */
	public static final String MOD_ID = "crabbersdelight";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String CRAB_TRAP_CLASS = "alabaster.crabbersdelight.common.block.entity.CrabTrapBlockEntity";

	/**
	 * 捕获物槽索引（1-9）
	 */
	private static final int[] CATCH_SLOTS = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };

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
	 * Crabber's Delight。
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 判断给定方块是否为捕蟹笼。
	 */
	public static boolean isCrabTrap(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName(CRAB_TRAP_CLASS);
			return clazz.isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 获取捕蟹笼的捕获物槽索引（1-9）。
	 */
	public static int[] getCatchSlots() {
		return CATCH_SLOTS;
	}
}