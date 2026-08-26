package com.better_removal.farmersdelight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

/**
 * Farmer's Delight的兼容（Forge 原版）
 *
 * 使用反射访问，类加载 / 方法调用全部放在try/catch
 * 厨锅槽位布局（CookingPotBlockEntity）：
 *   0-5  输入槽（食材）
 *   6    成品显示槽
 *   7    容器槽
 *   8    成品输出槽
 *
 * 该方块通过 {@code getInventory()} 返回自定义的ItemStackHandler。
 * 这里通过反射调用getStackInSlot / setStackInSlot取出成品输出槽的物品。
 */
public final class FarmersDelightSupport {

	private FarmersDelightSupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "farmersdelight";

	/**
	 * 需要反射访问的方块实体类
	 */
	private static final String COOKING_POT_CLASS = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity";

	/**
	 * 木篮 /竹篮共用的方块实体类全名（BasketBlockEntity，Container）。
	 */
	private static final String BASKET_CLASS = "vectorwing.farmersdelight.common.block.entity.BasketBlockEntity";

	/**
	 * 成品输出槽索引（CookingPotBlockEntity.OUTPUT_SLOT）。
	 */
	private static final int OUTPUT_SLOT = 8;

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
	 * 是否安装了 Farmer's Delight。
	 */
	public static boolean isLoaded() {
		return LOADED;
	}

	/**
	 * 判断给定方块是否为厨锅。
	 */
	public static boolean isCookingPot(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName(COOKING_POT_CLASS);
			return clazz.isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 判断给定方块是否为木篮 / 竹篮。
	 */
	public static boolean isBasket(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName(BASKET_CLASS);
			return clazz.isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 读取厨锅任意槽位的物品。返回 null 表示不可用。只读取。
	 */
	public static ItemStack getSlot(Level level, BlockPos pos, BlockEntity blockEntity, int slot) {
		if (!LOADED || blockEntity == null) {
			return null;
		}
		try {
			Class<?> clazz = Class.forName(COOKING_POT_CLASS);
			if (!clazz.isInstance(blockEntity)) {
				return null;
			}
			Object inventory = clazz.getMethod("getInventory").invoke(blockEntity);
			if (inventory == null) {
				return null;
			}
			Object stackObj = inventory.getClass().getMethod("getStackInSlot", int.class).invoke(inventory, slot);
			ItemStack stack = (ItemStack) stackObj;
			if (stack == null || stack.isEmpty()) {
				return null;
			}
			return stack.copy();
		}
		catch (Throwable t) {
			return null;
		}
	}

	/**
	 * 把厨锅任意槽位的物品减少count
	 */
	public static void removeFromSlot(Level level, BlockPos pos, BlockEntity blockEntity, int slot, int count) {
		if (!LOADED || blockEntity == null || count <= 0) {
			return;
		}
		try {
			Class<?> clazz = Class.forName(COOKING_POT_CLASS);
			if (!clazz.isInstance(blockEntity)) {
				return;
			}
			Object inventory = clazz.getMethod("getInventory").invoke(blockEntity);
			if (inventory == null) {
				return;
			}
			Object stackObj = inventory.getClass().getMethod("getStackInSlot", int.class).invoke(inventory, slot);
			ItemStack stack = (ItemStack) stackObj;
			if (stack == null || stack.isEmpty()) {
				return;
			}
			if (count >= stack.getCount()) {
				inventory.getClass().getMethod("setStackInSlot", int.class, ItemStack.class)
						.invoke(inventory, slot, ItemStack.EMPTY);
			}
			else {
				stack.shrink(count);
				inventory.getClass().getMethod("setStackInSlot", int.class, ItemStack.class)
						.invoke(inventory, slot, stack);
			}
		}
		catch (Throwable t) {

		}
	}
}