package com.example.farmersdelight;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * FarmersDelightRefabricated的兼容
 *
 * 使用反射访问，类加载 / 方法调用全部放在try/catch
 * 厨锅槽位布局（CookingPotBlockEntity）：
 *   0-5  输入槽（食材）
 *   6    成品显示槽
 *   7    容器槽
 *   8    成品输出槽
 *
 * 该方块通过 {@code getInventory()} 返回自定义的ItemStackHandler（vectorwing.farmersdelight.refabricated.inventory.ItemStackHandler）。
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
	 * 木篮 /竹篮共用的方块实体类全名（BasketBlockEntity，Inventory）。
	 */
	private static final String BASKET_CLASS = "vectorwing.farmersdelight.common.block.entity.BasketBlockEntity";

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
	public static ItemStack getSlot(World world, BlockPos pos, BlockEntity blockEntity, int slot) {
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
	public static void removeFromSlot(World world, BlockPos pos, BlockEntity blockEntity, int slot, int count) {
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
				stack.decrement(count);
				inventory.getClass().getMethod("setStackInSlot", int.class, ItemStack.class)
						.invoke(inventory, slot, stack);
			}
		}
		catch (Throwable t) {

		}
	}
}
