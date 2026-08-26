package com.better_removal.fossil;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

/**
 * Fossils and Archeology: Revival 培养槽
 *
 * 使用反射访问
 * 培养槽槽位布局（CultureVatBlockEntity）：
 *   0    输入槽
 *   1    燃料槽
 *   2    输出槽
 *
 * 该方块实体实现了原版 {@link net.minecraft.world.Container} 接口，
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

	/**
	 * 输出槽索引2
	 */
	private static final int OUTPUT_SLOT = 2;

	private static final boolean LOADED = checkLoaded();

	private static boolean checkLoaded() {
		try {
			return ModList.get().isLoaded(MOD_ID);
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

	public static int[] getOutputSlots() {
		return new int[] { OUTPUT_SLOT };
	}
}