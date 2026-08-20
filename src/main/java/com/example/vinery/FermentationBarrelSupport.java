package com.example.vinery;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Vinery陈酿桶
 *
 * 使用反射访问，try/catch
 *
 * 陈酿桶槽位
 *   0              葡萄汁输入
 *   1, 2, 3        食材输入
 *   4              酒瓶槽
 *   5              通用输出槽
 *
 * 实现原版 {@link net.minecraft.inventory.Inventory} 接口
 */
public final class FermentationBarrelSupport {

	private FermentationBarrelSupport() {
	}

	/**
     * mod id。
	 */
	public static final String MOD_ID = "vinery";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String CLASS = "net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity";

	/**
	 * 5
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
	 * 是否安装
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 是否为陈酿桶
	 */
	public static boolean isFermentationBarrel(BlockEntity blockEntity) {
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
	 * 输出槽5
	 */
	public static int[] getOutputSlots() {
		return new int[] { OUTPUT_SLOT };
	}
}
