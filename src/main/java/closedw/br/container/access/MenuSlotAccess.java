package closedw.br.container.access;

import closedw.br.container.menu.MenuResolver;
import closedw.br.container.support.ContainerAccess;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 菜单槽位的读写：槽位号是<b>菜单槽位索引</b>，物品读写全部经 {@link Slot}，
 * 因此等价于"玩家在界面上手动操作"——{@code canInsert} / {@code canTakeItems} / 单槽堆叠上限
 * 都由模组作者自己写在 {@code Slot} 上的规则裁决，不需要方块实体实现任何接口。
 * <p>
 * 关键实现直接复用原版的玩家语义：
 * <ul>
 *   <li>放入 → {@link Slot#insertStack(ItemStack, int)}（自己会查 {@code canInsert} 与堆叠上限）</li>
 *   <li>取出 → {@link Slot#takeStackRange(int, int, PlayerEntity)}（自己会查 {@code canTakeItems}，并触发 {@code onTakeItem} 钩子）</li>
 * </ul>
 * 菜单是临时对象，由入口在 finally 里调用 {@link #closeAll(PlayerEntity)} 统一关闭。
 * <p>
 * <b>1.20.1 移植说明</b>：这是 1.21.1 版的对应实现。1.20.1 的 {@link Slot} 没有
 * {@code safeInsert}/{@code safeTake}/{@code mayPlace}/{@code mayPickup}/{@code allowModification}/
 * {@code isFake}，一律换成语义等价的 {@code insertStack}/{@code takeStackRange}/{@code canInsert}/
 * {@code canTakeItems}/{@code canTakePartial}；{@code isFake} 的概念在 1.20.1 不存在（见 {@link MenuResolver}）。
 */
public final class MenuSlotAccess implements ContainerAccess {

	/** 本次操作期间临时构造、等待关闭的菜单。服务端 tick 单线程处理，够用。 */
	private static final List<MenuSlotAccess> OPEN = Collections.synchronizedList(new ArrayList<>());

	private final ScreenHandler menu;
	private final PlayerEntity player;
	/** 改动过的容器，{@link #markDirty()} 时一起标脏。 */
	private final Set<Inventory> touched = new LinkedHashSet<>();

	public MenuSlotAccess(ScreenHandler menu, PlayerEntity player) {
		this.menu = menu;
		this.player = player;
		OPEN.add(this);
	}

	/**
	 * 关闭本次操作里临时构造的所有菜单。由各入口在 finally 里调用。
	 * @param player 只关这个玩家的；传 null 表示全关
	 */
	public static void closeAll(PlayerEntity player) {
		List<MenuSlotAccess> snapshot;
		synchronized (OPEN) {
			snapshot = new ArrayList<>(OPEN);
			OPEN.clear();
		}
		List<MenuSlotAccess> remaining = new ArrayList<>();
		for (MenuSlotAccess access : snapshot) {
			if (player == null || access.player == player) {
				access.close();
			}
			else {
				remaining.add(access);
			}
		}
		if (!remaining.isEmpty()) {
			synchronized (OPEN) {
				OPEN.addAll(remaining);
			}
		}
	}

	private void close() {
		MenuResolver.close(this.menu, this.player);
	}

	@Override
	public int size() {
		return this.menu.slots.size();
	}

	@Override
	public ItemStack get(int slot) {
		Slot target = slotAt(slot);
		if (target == null) {
			return ItemStack.EMPTY;
		}
		try {
			ItemStack stack = target.getStack();
			return stack == null ? ItemStack.EMPTY : stack;
		}
		catch (Throwable t) {
			return ItemStack.EMPTY;
		}
	}

	@Override
	public int insert(int slot, ItemStack stack, boolean simulate) {
		Slot target = slotAt(slot);
		if (target == null || stack == null || stack.isEmpty()) {
			return 0;
		}
		int canInsert = canInsert(target, stack);
		if (canInsert <= 0 || simulate) {
			return Math.max(0, canInsert);
		}
		try {
			// 必须传副本：insertStack 会就地 split/decrement 传入的堆叠
			ItemStack probe = stack.copy();
			int before = probe.getCount();
			target.insertStack(probe, canInsert);
			int inserted = before - probe.getCount();
			if (inserted > 0) {
				this.touched.add(target.inventory);
			}
			return inserted;
		}
		catch (Throwable t) {
			return 0;
		}
	}

	/**
	 * 取出：走 {@link Slot#takeStackRange(int, int, PlayerEntity)}，即原版"玩家从槽位里拿东西"的完整路径。
	 * <p>
	 * {@code allowModification}（1.20.1 = {@code canTakePartial}）为假的槽位（典型是输出槽：只能取不能放）
	 * 在原版里要求整堆拿走，所以这里在放不下整堆时干脆不动它，避免"先取出、放不进背包、又放不回去"而丢物品。
	 */
	@Override
	public ItemStack take(int slot, int count, PlayerEntity actor) {
		Slot target = slotAt(slot);
		PlayerEntity who = actor != null ? actor : this.player;
		if (target == null || who == null || count <= 0) {
			return ItemStack.EMPTY;
		}
		try {
			ItemStack existing = target.getStack();
			if (existing == null || existing.isEmpty()) {
				return ItemStack.EMPTY;
			}
			int want = Math.min(count, existing.getCount());
			if (want <= 0) {
				return ItemStack.EMPTY;
			}
			if (!target.canTakePartial(who) && want < existing.getCount()) {
				// 需要整堆才允许改动的槽位
				return ItemStack.EMPTY;
			}
			ItemStack taken = target.takeStackRange(want, want, who);
			if (taken != null && !taken.isEmpty()) {
				this.touched.add(target.inventory);
				return taken;
			}
		}
		catch (Throwable t) {
			// 模组自定义逻辑抛异常时按"这次没取到"处理
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 直接覆盖槽位内容。语义上绕过了 {@code canTakeItems}，所以内部调用方一律走 {@link #take}；
	 * 这个实现只为满足 {@link ContainerAccess} 接口（例如厨锅那种反射容器需要它）。
	 */
	@Override
	public void remove(int slot, int count) {
		Slot target = slotAt(slot);
		if (target == null || count <= 0) {
			return;
		}
		try {
			ItemStack existing = target.getStack();
			if (existing == null || existing.isEmpty()) {
				return;
			}
			int take = Math.min(count, existing.getCount());
			int left = existing.getCount() - take;
			if (left <= 0) {
				target.setStack(ItemStack.EMPTY);
			}
			else {
				ItemStack copy = existing.copy();
				copy.setCount(left);
				target.setStack(copy);
			}
			this.touched.add(target.inventory);
		}
		catch (Throwable ignored) {
			// 与其它访问实现一致：失败就当没发生
		}
	}

	@Override
	public void markDirty() {
		for (Inventory container : this.touched) {
			try {
				container.markDirty();
			}
			catch (Throwable ignored) {
				// 单个容器标脏失败不影响其它容器
			}
		}
		this.touched.clear();
	}

	private Slot slotAt(int slot) {
		if (slot < 0 || slot >= this.menu.slots.size()) {
			return null;
		}
		try {
			return this.menu.getSlot(slot);
		}
		catch (Throwable t) {
			return null;
		}
	}

	/**
	 * 可放入数量，语义与 {@link Slot#insertStack(ItemStack, int)} 一致（含 {@code canInsert} 与单槽上限）。
	 * 独立算一遍是为了：① 支持 simulate；② 避免把超过上限的数量交给 insertStack
	 * （槽位内容超堆叠时原版那边会算出负数）。
	 */
	private static int canInsert(Slot target, ItemStack stack) {
		try {
			if (!target.canInsert(stack)) {
				return 0;
			}
			ItemStack existing = target.getStack();
			int room = target.getMaxItemCount(stack) - (existing == null ? 0 : existing.getCount());
			if (room <= 0) {
				return 0;
			}
			if (existing != null && !existing.isEmpty() && !ItemStack.canCombine(existing, stack)) {
				return 0;
			}
			return Math.min(stack.getCount(), room);
		}
		catch (Throwable t) {
			// 模组自定义过滤逻辑抛异常时视为不接受
			return 0;
		}
	}
}
