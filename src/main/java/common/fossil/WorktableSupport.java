package common.fossil;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Fossils and Archeology: Revival 考古工作台
 *
 * 使用反射访问
 * 考古工作台槽位布局（WorktableBlockEntity）：
 *   0    输入槽
 *   1    燃料槽
 *   2    输出槽
 *
 * 该方块实体实现了原版 {@link net.minecraft.inventory.Inventory} 接口
 */
public final class WorktableSupport {

	private WorktableSupport() {
	}

	/**
	 * mod id。
	 */
	public static final String MOD_ID = "fossil";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String WORKTABLE_CLASS = "com.github.teamfossilsarcheology.fossil.block.entity.WorktableBlockEntity";

	/**
	 * 输出槽索引2
	 */
	private static final int OUTPUT_SLOT = 2;

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

	public static boolean isWorktable(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			return Class.forName(WORKTABLE_CLASS).isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	public static int[] getOutputSlots() {
		return new int[] { OUTPUT_SLOT };
	}
}