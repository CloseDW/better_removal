package com.example.adastra;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Ad Astra冷冻机的适配。
 *
 * 使用反射访问，因此即使玩家没有安装Ad Astra，本模组也能正常运行：
 * - 类加载 / 方法调用全部放在try/catch中，缺失依赖时静默返回。
 *
 * 低温冷冻机槽位布局（CryoFreezerBlockEntity）：
 *   0    电槽
 *   1    输入槽
 *   2    流体容器输入
 *   3    输出槽
 *
 * 该方块实体实现了原版 {@link net.minecraft.inventory.Inventory} 接口（Yarn 映射），
 * 可以直接转型使用标准方法访问物品。
 */
public final class CryoFreezerSupport {

	private CryoFreezerSupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "ad_astra";

	/**
	 * 反射访问的方块实体类
	 */
	private static final String CLASS = "earth.terrarium.adastra.common.blockentities.machines.CryoFreezerBlockEntity";

	/**
	 * 输出槽
	 */
	private static final int OUTPUT_SLOT = 3;

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
	 * 判断给定方块是否为低温冷冻机
	 */
	public static boolean isCryoFreezer(BlockEntity blockEntity) {
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
	 * 槽3
	 */
	public static int[] getOutputSlots() {
		return new int[] { OUTPUT_SLOT };
	}
}
