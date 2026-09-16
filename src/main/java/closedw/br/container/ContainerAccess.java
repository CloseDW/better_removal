package closedw.br.container;

import net.minecraft.item.ItemStack;

/**
 * 容器槽位读写抽象：抹平原版 Inventory 与反射容器（农夫乐事厨锅、Cooking for Blockheads 烤炉）的差异。
 * 取出/放入/补货/连锁/预览只面向这个接口，不再各自分支。
 */
public interface ContainerAccess {

	/** 槽位总数。 */
	int size();

	/** 读取槽位内容；越界或不可用时返回 {@link ItemStack#EMPTY}。 */
	ItemStack get(int slot);

	/**
	 * 尝试把 stack 放入 slot，会自动按堆叠上限与槽位过滤（isValid）处理。
	 * simulate=true 时只计算可放入数量、不修改容器。
	 * @return 实际放入数量（0 表示放不进）
	 */
	int insert(int slot, ItemStack stack, boolean simulate);

	/** 从槽位移除 count 个物品。 */
	void remove(int slot, int count);

	/**
	 * 标记容器为脏，并执行该容器所需的同步副作用
	 * （如 Ad Astra 机器需要额外 sync()）。
	 */
	void markDirty();
}
