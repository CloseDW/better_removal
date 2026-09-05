package com.example.adastra;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Ad Astra 氧气装载机的兼容。
 *
 * 使用反射访问，因此即使玩家没有安装Ad Astra，本模组也能正常运行：
 * - 类加载 / 方法调用全部放在try/catch中，缺失依赖时静默返回。
 *
 * 氧气装载机槽位布局（OxygenLoaderBlockEntity）：
 *   0    电池槽
 *   1    输入槽（水桶）
 *   2    输出槽（空桶）
 *   3    流体容器输入
 *   4    输出槽
 *
 * 该方块实体实现了原版 {@link net.minecraft.inventory.Inventory} 接口（Yarn 映射），
 */
public final class OxygenLoaderSupport {

	private OxygenLoaderSupport() {
	}

	/**
	 * mod id。
	 */
	public static final String MOD_ID = "ad_astra";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String CLASS = "earth.terrarium.adastra.common.blockentities.machines.OxygenLoaderBlockEntity";

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
	 * 是否安装了 Ad Astra。
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 判断给定方块是否为氧气装载机。
	 */
	public static boolean isOxygenLoader(BlockEntity blockEntity) {
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
}
