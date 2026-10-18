package closedw.br;

import closedw.br.container.access.MenuSlotAccess;
import closedw.br.container.support.ContainerAccess;
import closedw.br.container.support.ContainerRegistry;
import closedw.br.container.support.ContainerSupport;
import closedw.br.slot.SlotSet;


import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 计算当前模式下将要取出的物品（服务端取物逻辑与 Jade 预览共用）。
 * 返回 null 表示该容器不受支持/未启用。
 * <p>
 * <b>一定要传操作玩家</b>：手动指认出来的容器（{@code MenuContainerSupport}）得靠他构造临时菜单，
 * 而且槽位的 {@code canTakeItems} 判定要跟真正取物时用同一个玩家，预览才不会和实际结果不一致
 * （不传玩家时那类实现只能拿"离方块最近的服务端玩家"顶上）。
 */
public final class ExtractionPreviewItems {

	/** 合并后单叠的上限：同种物品叠成一叠，超过这个数就再开一叠（仍不超过物品自身的堆叠上限）。 */
	private static final int MAX_MERGED_STACK = 64;

	private ExtractionPreviewItems() {
	}

	/**
	 * 把 stack 并入同种物品的已有条目：数量相加，单叠不超过 {@code min(64, 该物品的堆叠上限)}，
	 * 装不下的再开一叠；列表里没有同类就新建一叠。
	 * <p>
	 * 判定用 {@link ItemStack#canCombine}（1.20.1 里对应 1.21 的 isSameItemSameComponents）——
	 * 附魔/自定义数据不同的绝不合并。只会改列表里的副本，绝不碰传进来的 stack（它可能是容器里的活对象）。
	 */
	public static void mergeInto(List<ItemStack> items, ItemStack stack) {
		if (items == null || stack == null || stack.isEmpty()) {
			return;
		}
		int limit = Math.max(1, Math.min(MAX_MERGED_STACK, stack.getMaxCount()));
		int remaining = stack.getCount();
		// 先填已有的同类叠
		for (ItemStack existing : items) {
			if (remaining <= 0) {
				return;
			}
			if (!ItemStack.canCombine(existing, stack)) {
				continue;
			}
			int room = limit - existing.getCount();
			if (room <= 0) {
				continue;
			}
			int move = Math.min(room, remaining);
			existing.increment(move);
			remaining -= move;
		}
		// 还剩下的开新叠
		while (remaining > 0) {
			int take = Math.min(limit, remaining);
			ItemStack copy = stack.copy();
			copy.setCount(take);
			items.add(copy);
			remaining -= take;
		}
	}

	public static List<ItemStack> collect(PlayerEntity player, BlockEntity blockEntity, SlotSet mode) {
		ContainerSupport support = ContainerRegistry.find(blockEntity);
		if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
			return null;
		}
		int[] slots = support.extractSlots(blockEntity, mode);
		try {
			ContainerAccess access = support.access(blockEntity, player);
			if (slots == null || access == null) {
				return null;
			}
			List<ItemStack> items = new ArrayList<>();
			for (int slot : slots) {
				// 访问实现内部已做越界保护；同种物品在这里就叠成一叠
				mergeInto(items, access.get(slot));
			}
			return items;
		}
		finally {
			// 菜单槽位容器（手动指认映射）会临时构造菜单，读完全部关掉（只关这个玩家的）
			MenuSlotAccess.closeAll(player);
		}
	}
}
