package closedw.br.container.support;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 容器槽位读写抽象：抹平原版 Container 与反射容器（农夫乐事厨锅、Cooking for Blockheads 烤炉）的差异。
 * 取出/放入/补货/连锁/预览只面向这个接口，不再各自分支。
 */
public interface ContainerAccess {

    /** 槽位总数。 */
    int size();

    /** 读取槽位内容；越界或不可用时返回 {@link ItemStack#EMPTY}。 */
    ItemStack get(int slot);

    /**
     * 尝试把 stack 放入 slot，会自动按堆叠上限与槽位过滤（canPlaceItem）处理。
     * simulate=true 时只计算可放入数量、不修改容器。
     * @return 实际放入数量（0 表示放不进）
     */
    int insert(int slot, ItemStack stack, boolean simulate);

    /** 从槽位移除 count 个物品。 */
    void remove(int slot, int count);

    /**
     * 取出物品并返回实际取到的堆叠（默认实现 = 读取 + {@link #remove}）。
     * 菜单槽位会覆盖这个方法走原版的 {@code Slot#safeTake}，从而遵守模组写在槽位上的
     * {@code mayPickup} 规则、并触发 {@code onTake} 钩子——即"以玩家身份取出"。
     * 调用方必须先确认背包放得下，取出的堆叠不会再放回容器。
     * @param player 操作者；菜单实现需要它
     */
    default ItemStack take(int slot, int count, Player player) {
        ItemStack existing = get(slot);
        if (existing.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        int take = Math.min(count, existing.getCount());
        ItemStack result = existing.copy();
        result.setCount(take);
        remove(slot, take);
        return result;
    }

    /**
     * 标记容器为脏，并执行该容器所需的同步副作用
     * （如 Ad Astra 机器需要额外 sync()）。
     */
    void markDirty();
}
