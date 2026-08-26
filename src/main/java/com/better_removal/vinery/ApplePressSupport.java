package com.better_removal.vinery;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Vinery苹果压榨器
 *
 * 反射访问
 * try/catch
 *   0    压榨输入槽
 *   1    中间产物槽
 *   2    酒瓶输入槽
 *   3    唯一输出槽
 *
 * 实现原版 {@link net.minecraft.world.Container} 接口
 */
public final class ApplePressSupport {

	private ApplePressSupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "vinery";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String CLASS = "net.satisfy.vinery.core.block.entity.ApplePressBlockEntity";

	/**
	 * 输出槽索引
	 */
	private static final int OUTPUT_SLOT = 3;

	private static final boolean LOADED = FermentationBarrelSupport.isLoaded();

	/**
	 * 苹果压榨器。
	 */
	public static boolean isApplePress(BlockEntity blockEntity) {
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
	 * 获取苹果压榨器的输出槽索引3
	 */
	public static int[] getOutputSlots() {
		return new int[] { OUTPUT_SLOT };
	}
}