package com.example.adastra;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Ad Astra 电力高炉的兼容。
 * 使用反射访问，因此即使玩家没有安装Ad Astra，本模组也能正常运行：
 * - 类加载 / 方法调用全部放在try/catch 中，缺失依赖时静默返回。
 * 电力高炉槽位布局（EtrionicBlastFurnaceBlockEntity）：
 *   0      电槽
 *   1-4    输入槽
 *   5-8    输出槽
 * 该方块实体实现了原版 {@link net.minecraft.inventory.Inventory} 接口（Yarn 映射），
 */
public final class EtrionicBlastFurnaceSupport {

	private EtrionicBlastFurnaceSupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "ad_astra";

	/**
	 * 反射访问的方块实体类
	 */
	private static final String CLASS = "earth.terrarium.adastra.common.blockentities.machines.EtrionicBlastFurnaceBlockEntity";

	/**
	 * 输出槽
	 */
	private static final int OUTPUT_SLOT = 5;

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
	 * 是否安装了 Ad Astra
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 判断给定方块实体是否为电力高炉
	 */
	public static boolean isEtrionicBlastFurnace(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName(CLASS);
			return clazz.isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 返回所有4个输出槽。
	 */
	public static int[] getOutputSlots() {
		return new int[] { 5, 6, 7, 8 };
	}
}
