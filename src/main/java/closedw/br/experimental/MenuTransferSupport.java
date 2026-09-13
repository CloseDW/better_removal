package closedw.br.experimental;

import closedw.br.BetterRemoval;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实验性：用“模拟 shift 点击”读出模组自己写的槽位规则。
 * 原理：服务端处理一次点击其实就是 {@code currentScreenHandler.onSlotClick(...)}，
 * 而 shift 点击（QUICK_MOVE）的本体是模组必须实现的 {@link ScreenHandler#quickMove(PlayerEntity, int)}
 */
final class MenuTransferSupport {

	private static final Logger LOGGER = BetterRemoval.LOGGER;

	/** 探测结果缓存：方块ID#类名 -> 槽位角色。空表表示“探测过但没有结果”。 */
	private static final Map<String, Map<Integer, SlotRole>> CACHE = new ConcurrentHashMap<>();

	/** 日志限流：同一条信息只打印一次。 */
	private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

	private MenuTransferSupport() {
	}

	/** 清空缓存（{@code /br reload} 时调用）。 */
	static void clearCache() {
		CACHE.clear();
		LOGGED.clear();
	}

	/**
	 * 只跑 quickMove 探测并返回原始结果（不合并任何基础判定），
	 * 供自动探测的 {@link #refine} 与主动探测报告共用。
	 *
	 * @return 槽位 -> 角色；null 表示这次探测不适用（结构不符 / 模组菜单跑不通），不会被缓存
	 */
	static Map<Integer, SlotRole> probeRaw(BlockEntity blockEntity) {
		if (!(blockEntity instanceof Inventory target)) {
			return null;
		}
		if (!(blockEntity instanceof NamedScreenHandlerFactory factory)) {
			return null;
		}
		World world = blockEntity.getWorld();
		if (world == null || world.isClient) {
			return null;
		}
		PlayerEntity player = MenuSlotSupport.probePlayer(world, blockEntity.getPos());
		if (player == null) {
			return null;
		}

		String key = cacheKey(blockEntity);
		Map<Integer, SlotRole> probed = CACHE.get(key);
		if (probed == null) {
			probed = probe(target, factory, player, key);
			if (probed == null) {
				// 这次探测不适用（结构不符 / 模组菜单跑不通），不缓存，也不影响只读结果
				return null;
			}
			CACHE.put(key, probed);
		}
		return probed;
	}

	/**
	 * 在只读菜单结果的基础上，用 quickMove 探测修正输入/燃料槽。
	 * @return 修正后的角色表；没有额外信息或探测失败时返回 null，调用方继续用只读结果
	 */
	static SlotRole[] refine(BlockEntity blockEntity, SlotRole[] base) {
		if (base == null) {
			return null;
		}
		Map<Integer, SlotRole> probed = probeRaw(blockEntity);
		if (probed == null || probed.isEmpty()) {
			return null;
		}

		String key = cacheKey(blockEntity);
		SlotRole[] roles = base.clone();
		StringBuilder detail = new StringBuilder();
		for (Map.Entry<Integer, SlotRole> entry : probed.entrySet()) {
			int slot = entry.getKey();
			if (slot < 0 || slot >= roles.length) {
				continue;
			}
			if (roles[slot] == SlotRole.OUTPUT) {
				// 只读结果已经判定“放不进去”，不要用探测结果去推翻它
				continue;
			}
			roles[slot] = entry.getValue();
			detail.append(' ').append(slot).append('=').append(entry.getValue().name());
		}
		if (detail.length() == 0) {
			return null;
		}
		logOnce(key, "quickMove探测 ->" + detail);
		return roles;
	}

	/**
	 * 真正跑探测：每个探测物调用一次 {@code quickMove}，看它落到哪个容器槽。
	 * @return 槽位 -> 角色；返回 null 表示探测不可用（调用方不要缓存）
	 */
	private static Map<Integer, SlotRole> probe(Inventory target, NamedScreenHandlerFactory factory,
			PlayerEntity player, String key) {
		PlayerInventory probeInventory = new PlayerInventory(player);
		PlayerInventory realInventory = player.getInventory();
		List<ItemStack> realSnapshot = snapshot(realInventory);
		ScreenHandler menu = null;
		try {
			menu = factory.createMenu(0, probeInventory, player);
			if (menu == null) {
				return null;
			}

			List<ItemStack> snapshot = snapshot(menu);
			int seedIndex = -1;
			int containerSlots = 0;
			for (int i = 0; i < menu.slots.size(); i++) {
				Slot slot = menu.slots.get(i);
				if (slot.inventory == target) {
					containerSlots++;
				}
				else if (seedIndex < 0 && slot.inventory == probeInventory) {
					// 玩家侧槽位必须真的指向我们那个一次性背包，否则不碰
					seedIndex = i;
				}
			}
			if (containerSlots == 0 || seedIndex < 0) {
				logOnce(key, "菜单槽位不是指向方块实体自身的Inventory或没有玩家侧沙箱，跳过quickMove探测");
				return null;
			}

			Set<Integer> inputs = new LinkedHashSet<>();
			Set<Integer> fuels = new LinkedHashSet<>();
			for (ItemStack probeStack : Probes.GENERIC) {
				restore(menu, snapshot);
				try {
					menu.slots.get(seedIndex).setStack(probeStack.copy());
					menu.quickMove(player, seedIndex);
				}
				catch (Throwable t) {
					// 个别模组的 quickMove 在“没有真正打开界面”时会抛异常，换下一个探测物
					continue;
				}
				boolean fuelProbe = probeStack.isOf(Items.COAL);
				for (int i = 0; i < menu.slots.size(); i++) {
					Slot slot = menu.slots.get(i);
					if (slot.inventory != target) {
						continue;
					}
					if (!gained(snapshot.get(i), slot)) {
						continue;
					}
					int index = slot.getIndex();
					if (index < 0 || index >= target.size()) {
						continue;
					}
					(fuelProbe ? fuels : inputs).add(index);
				}
			}

			restore(menu, snapshot);
			if (!matches(menu, snapshot)) {
				logOnce(key, "quickMove探测后容器状态未能完全还原，已放弃这次结果");
				return null;
			}

			Map<Integer, SlotRole> result = new LinkedHashMap<>();
			for (int slot : inputs) {
				result.put(slot, SlotRole.INPUT);
			}
			for (int slot : fuels) {
				// 同时收煤炭和粗铁 => 它是普通输入槽（和 Probes.fuelOrInput 的语义一致）
				if (!inputs.contains(slot)) {
					result.put(slot, SlotRole.FUEL);
				}
			}
			return result;
		}
		catch (Throwable t) {
			logOnce(key, "quickMove探测失败：" + t);
			return null;
		}
		finally {
			if (menu != null) {
				try {
					menu.onClosed(player);
				}
				catch (Throwable ignored) {
					// 与真实开关界面保持对称，失败不影响结果
				}
			}
			if (restoreInventory(realInventory, realSnapshot)) {
				// 说明某个模组的 quickMove 直接改了玩家真实背包，已还原；记一条便于排查
				logOnce(key, "探测期间玩家背包被模组代码改动，已还原");
			}
		}
	}

	/** 该槽位在这次操作中是否“多出了东西”（空槽被放进、或同类合并）。 */
	private static boolean gained(ItemStack before, Slot slot) {
		ItemStack after = safeGet(slot);
		if (after.isEmpty()) {
			return false;
		}
		return after.getCount() > before.getCount()
				|| (before.isEmpty() && !after.isEmpty());
	}

	private static List<ItemStack> snapshot(ScreenHandler menu) {
		List<ItemStack> stacks = new ArrayList<>(menu.slots.size());
		for (Slot slot : menu.slots) {
			stacks.add(safeGet(slot).copy());
		}
		return stacks;
	}

	/** 还原到快照；只写有差异的槽位，尽量少触发模组自己的 setStack 逻辑。 */
	private static void restore(ScreenHandler menu, List<ItemStack> snapshot) {
		int size = Math.min(menu.slots.size(), snapshot.size());
		for (int i = 0; i < size; i++) {
			Slot slot = menu.slots.get(i);
			if (ItemStack.areEqual(safeGet(slot), snapshot.get(i))) {
				continue;
			}
			try {
				slot.setStack(snapshot.get(i).copy());
			}
			catch (Throwable ignored) {
				// 个别槽位（展示槽）不允许写回，忽略
			}
		}
	}

	private static boolean matches(ScreenHandler menu, List<ItemStack> snapshot) {
		if (menu.slots.size() != snapshot.size()) {
			return false;
		}
		for (int i = 0; i < snapshot.size(); i++) {
			if (!ItemStack.areEqual(safeGet(menu.slots.get(i)), snapshot.get(i))) {
				return false;
			}
		}
		return true;
	}

	private static List<ItemStack> snapshot(Inventory inventory) {
		int size;
		try {
			size = inventory.size();
		}
		catch (Throwable t) {
			return null;
		}
		List<ItemStack> stacks = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			stacks.add(safeGet(inventory, i).copy());
		}
		return stacks;
	}

	/** @return 是否真的改过（改过说明模组代码动了玩家背包） */
	private static boolean restoreInventory(Inventory inventory, List<ItemStack> snapshot) {
		if (snapshot == null || inventory == null) {
			return false;
		}
		boolean changed = false;
		try {
			if (snapshot.size() != inventory.size()) {
				return false;
			}
			for (int i = 0; i < snapshot.size(); i++) {
				if (ItemStack.areEqual(inventory.getStack(i), snapshot.get(i))) {
					continue;
				}
				inventory.setStack(i, snapshot.get(i).copy());
				changed = true;
			}
			if (changed) {
				inventory.markDirty();
			}
		}
		catch (Throwable t) {
			return changed;
		}
		return changed;
	}

	private static ItemStack safeGet(Slot slot) {
		try {
			ItemStack stack = slot.getStack();
			return stack == null ? ItemStack.EMPTY : stack;
		}
		catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}

	private static ItemStack safeGet(Inventory inventory, int slot) {
		try {
			ItemStack stack = inventory.getStack(slot);
			return stack == null ? ItemStack.EMPTY : stack;
		}
		catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}

	private static String cacheKey(BlockEntity blockEntity) {
		Identifier id = SlotRules.blockId(blockEntity);
		return (id != null ? id.toString() : "?") + "#" + blockEntity.getClass().getName();
	}

	private static void logOnce(String key, String message) {
		String line = key + " -> " + message;
		if (LOGGED.add(line)) {
			LOGGER.info("[auto-detect] {}", line);
		}
	}
}
