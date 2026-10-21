package closedw.br.container.access;

import closedw.br.container.Stacks;
import closedw.br.container.support.ContainerAccess;
import closedw.br.container.sync.ContainerSync;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 原版 {@link Container} 的槽位读写。
 * 保留原有语义：空槽先过 canPlaceItem、同类堆叠合并、写回时显式 setItem
 * （部分模组容器的 getItem 返回副本，直接 grow 不生效）。
 */
public final class InventoryAccess implements ContainerAccess {

    private final BlockEntity blockEntity;
    private final Container container;

    public InventoryAccess(BlockEntity blockEntity, Container container) {
        this.blockEntity = blockEntity;
        this.container = container;
    }

    @Override
    public int size() {
        try {
            return this.container.getContainerSize();
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
            ItemStack stack = this.container.getItem(slot);
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
            if (!canPlaceItem(slot, stack)) {
                return 0;
            }
            int put = Math.min(stack.getCount(), stack.getMaxStackSize());
            if (put <= 0) {
                return 0;
            }
            if (!simulate) {
                ItemStack copy = stack.copy();
                copy.setCount(put);
                this.container.setItem(slot, copy);
            }
            return put;
        }
        if (Stacks.isSameItem(existing, stack)) {
            int canMove = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (canMove <= 0) {
                return 0;
            }
            if (!simulate) {
                existing.grow(canMove);
                // 显式写回：部分模组容器的 getItem 返回副本，直接 grow 不会生效
                this.container.setItem(slot, existing);
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
        stack.shrink(count);
        this.container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
    }

    @Override
    public void markDirty() {
        this.container.setChanged();
        // 内部容器不一定是方块实体本身（烤炉那种反射取得的容器），只标脏容器未必会落盘
        if (this.blockEntity != null && this.container != this.blockEntity) {
            this.blockEntity.setChanged();
        }
        // 客户端也缓存了物品列表的容器（如 Ad Astra 机器）靠这一句刷新，其余容器内部会跳过
        ContainerSync.sync(this.blockEntity);
    }

    private boolean canPlaceItem(int slot, ItemStack stack) {
        try {
            return this.container.canPlaceItem(slot, stack);
        }
        catch (Throwable t) {
            // 模组自定义过滤逻辑可能抛异常，视为不接受
            return false;
        }
    }
}
