package com.example;

import com.example.farmersdelight.FarmersDelightSupport;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 计算当前模式下将要取出的物品（服务端取物逻辑与 Jade 预览共用）。
 * 返回 null 表示该容器不受支持/未启用
 */
public final class ExtractionPreviewItems {

	private ExtractionPreviewItems() {
	}

	public static List<ItemStack> collect(BlockEntity blockEntity, ExtractionMode mode) {
		// 农夫乐事厨锅：通过反射访问其 ItemStackHandler
		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			if (!OutputSlotExtractor.isContainerEnabled("cooking_pot")) {
				return null;
			}
			int[] slots;
			if (mode == ExtractionMode.ALL) {
				slots = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
			}
			else if (mode == ExtractionMode.OUTPUT) {
				slots = new int[] { 8 };
			}
			else if (mode == ExtractionMode.INPUT) {
				slots = new int[] { 0, 1, 2, 3, 4, 5 };
			}
			else {
				slots = new int[] { 7 };
			}
			List<ItemStack> items = new ArrayList<>();
			for (int slot : slots) {
				ItemStack stack = FarmersDelightSupport.getSlot(null, blockEntity.getPos(), blockEntity, slot);
				if (stack != null && !stack.isEmpty()) {
					items.add(stack);
				}
			}
			return items;
		}

		int[] slots = OutputSlotExtractor.getSlotsForMode(blockEntity, mode);
		if (slots == null) {
			return null;
		}
		if (!(blockEntity instanceof Inventory inventory)) {
			return null;
		}

		List<ItemStack> items = new ArrayList<>();
		for (int slot : slots) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isEmpty()) {
				items.add(stack);
			}
		}
		return items;
	}
}