package com.better_removal.adastra;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

/**
 * Ad Astra 燃料精炼机的兼容
 *
 * 使用反射访问，因此即使玩家没有安装 Ad Astra，本模组也能正常运行：
 * - 类加载 / 方法调用全部放在try/catch中，缺失依赖时静默返回。
 *
 * 燃料精炼机槽位布局（FuelRefineryBlockEntity）：
 *   0    电槽
 *   1    输入槽（原油）
 *   2    输出槽（空桶）
 *   3    流体容器输入（放入空桶）
 *   4    输出槽（满桶）
 *
 * 该方块实体实现了原版{@link net.minecraft.world.Container}接口（Mojang 映射），
 */
public final class FuelRefinerySupport {

	private FuelRefinerySupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "ad_astra";

	/**
	 * 反射访问的方块实体类
	 */
	private static final String CLASS = "earth.terrarium.adastra.common.blockentities.machines.FuelRefineryBlockEntity";

	/**
	 * 槽2：空输出
	 * 槽4：满输出
	 */
	private static final int[] OUTPUT_SLOTS = { 2, 4 };

	private static final boolean LOADED = checkLoaded();

	private static boolean checkLoaded() {
		try {
			return ModList.get().isLoaded(MOD_ID);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 是否安装了Ad Astra。
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 判断给定方块是否为燃料精炼机
	 */
	public static boolean isFuelRefinery(BlockEntity blockEntity) {
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
	 * 槽2（空容器）和槽4（满容器）。
	 */
	public static int[] getOutputSlots() {
		return OUTPUT_SLOTS;
	}
}