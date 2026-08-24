package common.fossil;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Fossils and Archeology: Revival筛子
 *
 * 使用反射访问
 * 筛子槽位布局（SifterBlockEntity）：
 *   0      输入槽
 *   1-5    输出槽
 * 该方块实体实现了原版 {@link net.minecraft.inventory.Inventory} 接口
 */
public final class SifterSupport {

	private SifterSupport() {
	}

	/**
	 * mod id。
	 */
	public static final String MOD_ID = "fossil";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String SIFTER_CLASS = "com.github.teamfossilsarcheology.fossil.block.entity.SifterBlockEntity";

	/**
	 * 输出槽索引（1-5）
	 */
	private static final int[] OUTPUT_SLOTS = { 1, 2, 3, 4, 5 };

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

	public static boolean isSifter(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			return Class.forName(SIFTER_CLASS).isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	public static int[] getOutputSlots() {
		return OUTPUT_SLOTS;
	}
}