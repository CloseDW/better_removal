package closedw.br.container;

import closedw.br.adastra.AdAstraMachineSupport;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/**
 * 原版 {@link Inventory} 的槽位读写。
 * 保留原有语义：空槽先过 isValid、同类堆叠合并、写回时显式 setStack
 * （部分模组容器的 getStack 返回副本，直接 increment 不生效）。
 */
public final class InventoryAccess implements ContainerAccess {

	private final BlockEntity blockEntity;
	private final Inventory inventory;

	public InventoryAccess(BlockEntity blockEntity, Inventory inventory) {
		this.blockEntity = blockEntity;
		this.inventory = inventory;
	}

	@Override
	public int size() {
		try {
			return this.inventory.size();
		}
		catch (Throwable t) {
			return 0;
		}
	}

	@Override
	public ItemStack get(int slot) {
		if (slot < 0 || slot >= size()) {
			return ItemStack.EMPTY;
		}
		try {
			ItemStack stack = this.inventory.getStack(slot);
			return stack == null ? ItemStack.EMPTY : stack;
		}
		catch (Throwable t) {
			return ItemStack.EMPTY;
		}
	}

	@Override
	public int insert(int slot, ItemStack stack, boolean simulate) {
		if (stack == null || stack.isEmpty() || slot < 0 || slot >= size()) {
			return 0;
		}
		ItemStack existing = get(slot);
		if (existing.isEmpty()) {
			if (!isValid(slot, stack)) {
				return 0;
			}
			int put = Math.min(stack.getCount(), stack.getMaxCount());
			if (put <= 0) {
				return 0;
			}
			if (!simulate) {
				ItemStack copy = stack.copy();
				copy.setCount(put);
				this.inventory.setStack(slot, copy);
			}
			return put;
		}
		if (Stacks.isSameItem(existing, stack)) {
			int canMove = Math.min(stack.getCount(), existing.getMaxCount() - existing.getCount());
			if (canMove <= 0) {
				return 0;
			}
			if (!simulate) {
				existing.increment(canMove);
				// 显式写回：部分模组容器的 getStack 返回副本，直接 increment 不会生效
				this.inventory.setStack(slot, existing);
			}
			return canMove;
		}
		return 0;
	}

	@Override
	public void remove(int slot, int count) {
		if (count <= 0 || slot < 0 || slot >= size()) {
			return;
		}
		ItemStack stack = get(slot);
		if (stack.isEmpty()) {
			return;
		}
		stack.decrement(count);
		this.inventory.setStack(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
	}

	@Override
	public void markDirty() {
		this.inventory.markDirty();
		if (AdAstraMachineSupport.isAdAstraMachine(this.blockEntity)) {
			// Ad Astra机器在物品变化后需要同步，否则客户端显示不同步
			AdAstraMachineSupport.sync(this.blockEntity);
		}
	}

	private boolean isValid(int slot, ItemStack stack) {
		try {
			return this.inventory.isValid(slot, stack);
		}
		catch (Throwable t) {
			// 模组自定义过滤逻辑可能抛异常，视为不接受
			return false;
		}
	}
}
