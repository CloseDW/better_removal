package closedw.br.container.access;

import closedw.br.container.support.ContainerAccess;
import closedw.br.container.sync.ContainerSync;
import closedw.br.farmersdelight.FarmersDelightSupport;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 农夫乐事厨锅的槽位读写：它没有实现原版 Container，
 * 而是通过反射访问自定义 ItemStackHandler（getStackInSlot / insertItem / setStackInSlot）。
 */
public final class CookingPotAccess implements ContainerAccess {

    /** 厨锅内部容器固定9格：0-5输入 6成品显示 7容器 8输出。 */
    private static final int SIZE = 9;

    private final BlockEntity blockEntity;

    public CookingPotAccess(BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public ItemStack get(int slot) {
        if (slot < 0 || slot >= SIZE) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = FarmersDelightSupport.getSlot(null, this.blockEntity.getBlockPos(), this.blockEntity, slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public int insert(int slot, ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= SIZE) {
            return 0;
        }
        return FarmersDelightSupport.insertToSlot(null, this.blockEntity.getBlockPos(), this.blockEntity, slot, stack, simulate);
    }

    @Override
    public void remove(int slot, int count) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        FarmersDelightSupport.removeFromSlot(null, this.blockEntity.getBlockPos(), this.blockEntity, slot, count);
    }

    @Override
    public void markDirty() {
        this.blockEntity.setChanged();
        ContainerSync.sync(this.blockEntity);
    }
}
