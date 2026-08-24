package common.adastra;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Ad Astra 压缩机的适配。
 *
 * 使用反射访问，因此即使玩家没有安装Ad Astra，本模组也能正常运行：
 * - 类加载 / 方法调用全部放在try/catch中，缺失依赖时静默返回。
 *
 * 压缩机槽位布局（CompressorBlockEntity）：
 *   0    电槽
 *   1    输入槽
 *   2    输出槽
 *
 * 该方块实体实现了原版{@link net.minecraft.inventory.Inventory}接口（Yarn 映射），
 */
public final class CompressorSupport {

	private CompressorSupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "ad_astra";

	/**
	 * 反射访问的方块实体类。
	 */
	private static final String COMPRESSOR_CLASS = "earth.terrarium.adastra.common.blockentities.machines.CompressorBlockEntity";

	/**
	 * 输出槽（CompressorBlockEntity）。
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

	/**
	 * 是否安装了Ad Astra
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 判断方块实体是否为压缩机
	 */
	public static boolean isCompressor(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName(COMPRESSOR_CLASS);
			return clazz.isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 槽 2 为输出槽。
	 */
	public static int[] getOutputSlots() {
		return new int[] { OUTPUT_SLOT };
	}
}