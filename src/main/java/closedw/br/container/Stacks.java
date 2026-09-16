package closedw.br.container;

import net.minecraft.item.ItemStack;

import java.util.Objects;

/**
 * 物品堆叠判断工具。取出/放入/补货/预览共用同一份判断，避免各处各写一套而走样。
 */
public final class Stacks {

	private Stacks() {
	}

	/**
	 * 判断两个堆叠是否为同种物品（含NBT）。
	 */
	public static boolean isSameItem(ItemStack a, ItemStack b) {
		if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
			return false;
		}
		return a.isOf(b.getItem()) && Objects.equals(a.getNbt(), b.getNbt());
	}
}
