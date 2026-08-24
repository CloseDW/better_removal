package common.fossil;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Fossils and Archeology: Revival 分析仪
 *
 * 使用反射访问，类加载 / 判断全部放在 try/catch
 *
 * 分析仪槽位布局（AnalyzerBlockEntity）：
 *   0-8    输入槽
 *   9-12   输出槽
 *
 * 该方块实体实现了原版 {@link net.minecraft.inventory.Inventory} 接口（Yarn 映射，
 * 通过 BaseContainerBlockEntity），因此可直接转型用标准方法访问物品。
 */
public final class AnalyzerSupport {

	private AnalyzerSupport() {
	}

	/**
	 * mod id。
	 */
	public static final String MOD_ID = "fossil";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String ANALYZER_CLASS = "com.github.teamfossilsarcheology.fossil.block.entity.AnalyzerBlockEntity";

	/**
	 * 输出槽索引（9-12）。
	 */
	private static final int[] OUTPUT_SLOTS = { 9, 10, 11, 12 };

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
	 * 是否安装了 Fossils and Archeology: Revival。
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 是否为分析仪。
	 */
	public static boolean isAnalyzer(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName(ANALYZER_CLASS);
			return clazz.isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 获取分析仪的输出槽索引（9-12）。
	 */
	public static int[] getOutputSlots() {
		return OUTPUT_SLOTS;
	}
}