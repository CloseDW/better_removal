package closedw.br;

import closedw.br.container.ContainerAccess;
import closedw.br.container.ContainerRegistry;
import closedw.br.container.ContainerSupport;
import net.minecraft.block.entity.BlockEntity;
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
		ContainerSupport support = ContainerRegistry.find(blockEntity);
		if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
			return null;
		}
		int[] slots = support.extractSlots(blockEntity, mode);
		ContainerAccess access = support.access(blockEntity);
		if (slots == null || access == null) {
			return null;
		}
		List<ItemStack> items = new ArrayList<>();
		for (int slot : slots) {
			// 访问实现内部已做越界保护
			ItemStack stack = access.get(slot);
			if (!stack.isEmpty()) {
				items.add(stack);
			}
		}
		return items;
	}
}
