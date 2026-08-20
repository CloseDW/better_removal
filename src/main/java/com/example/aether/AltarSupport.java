package com.example.aether;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * 天境神能炉的兼容。
 *
 * 使用反射访问，类加载 / 判断全部放在 try/catch 中。
 *
 * 祭坛槽位布局（AbstractFurnaceBlockEntity）：
 *   0    输入
 *   1    燃料
 *   2    输出
 *
 * 该方块实体继承自原版 {@link net.minecraft.block.entity.AbstractFurnaceBlockEntity}，
 * 因此实现原版 {@link net.minecraft.inventory.Inventory} 接口，可直接转型用标准方法访问物品。
 */
public final class AltarSupport {

	private AltarSupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "aether";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String ALTAR_CLASS = "com.aetherteam.aether.blockentity.AltarBlockEntity";

	/**
	 * 输出槽索引。
	 */
	private static final int OUTPUT_SLOT = 2;

	private static final boolean LOADED = FreezerSupport.isLoaded();

	/**
	 * 判断给定方块是否为神能炉。
	 */
	public static boolean isAltar(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName(ALTAR_CLASS);
			return clazz.isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 获取祭坛的输出槽索引（2）。
	 */
	public static int[] getOutputSlots() {
		return new int[] { OUTPUT_SLOT };
	}
}
