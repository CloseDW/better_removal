package com.example.fossil;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Fossils and Archeology: Revival 培养槽
 *
 * 使用反射访问
 * 培养槽槽位布局（CultureVatBlockEntity）：
 *   0    输入槽
 *   1    燃料槽
 *   2    输出槽
 *
 * 该方块实体实现了原版 {@link net.minecraft.inventory.Inventory} 接口，
 */
public final class CultureVatSupport {

	private CultureVatSupport() {
	}

	/**
	 * mod id。
	 */
	public static final String MOD_ID = "fossil";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String CULTURE_VAT_CLASS = "com.github.teamfossilsarcheology.fossil.block.entity.CultureVatBlockEntity";

	private static final boolean LOADED = checkLoaded();

	private static boolean checkLoaded() {
		try {
			return FabricLoader.getInstance().isModLoaded(MOD_ID);
		}
		catch (Throwable t) {
			return false;
		}
	}

	public static boolean isLoaded() {
		return LOADED;
	}

	public static boolean isCultureVat(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			return Class.forName(CULTURE_VAT_CLASS).isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}
}
