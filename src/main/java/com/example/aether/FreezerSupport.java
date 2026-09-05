package com.example.aether;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * 天境冷冻器
 *
 * 使用反射访问，类加载 / 判断全部放在 try/catch
 * 冷冻器槽位布局（AbstractFurnaceBlockEntity）
 *   0    输入槽
 *   1    燃料槽
 *   2    输出槽
 *
 * 该方块实体继承自原版 {@link net.minecraft.block.entity.AbstractFurnaceBlockEntity}，
 * 因此实现原版 {@link net.minecraft.inventory.Inventory} 接口，可直接转型用标准方法访问物品。
 */
public final class FreezerSupport {

	private FreezerSupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "aether";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String FREEZER_CLASS = "com.aetherteam.aether.blockentity.FreezerBlockEntity";

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
	 * 是否安装了 The Aether。
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 判断给定方块是否为冷冻器
	 */
	public static boolean isFreezer(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName(FREEZER_CLASS);
			return clazz.isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}
}
