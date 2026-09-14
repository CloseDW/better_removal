package com.better_removal.experimental;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 实验性：用容器自己的 GUI 菜单推断槽位角色。
 * 原理：菜单里的 {@link Slot} 是模组作者写给玩家看的规则，
 * {@code mayPlace}（能否放入）、{@code mayPickup}（能否取出）、{@code isActive}（槽位是否显示）、
 * {@code getMaxStackSize}（堆叠上限）合起来足以区分“输入/燃料/输出/内部槽位”，
 * 而且不要求模组把 {@link Container} 接口实现正确。
 */
final class MenuSlotSupport {

	private static final Logger LOGGER = LogUtils.getLogger();

	/** 探测失败原因只在变化时打印一次，避免刷屏。 */
	private static final Map<String, String> LAST_LOGGED = new ConcurrentHashMap<>();

	private MenuSlotSupport() {
	}

	/**
	 * 按菜单语义推断每个槽位的角色。
	 * @return 长度等于该容器槽位数；不适用或无法判断时返回 null
	 */
	static SlotRole[] detect(BlockEntity blockEntity) {
		if (!(blockEntity instanceof Container target)) {
			return null;
		}
		if (!(blockEntity instanceof MenuProvider factory)) {
			return null;
		}
		Level level = blockEntity.getLevel();
		if (level == null || level.isClientSide) {
			// 菜单只能在服务端构造
			return null;
		}
		Player player = probePlayer(level, blockEntity.getBlockPos());
		if (player == null) {
			return null;
		}

		AbstractContainerMenu menu = null;
		try {
			menu = factory.createMenu(0, new Inventory(player), player);
			if (menu == null) {
				return null;
			}
			return classify(blockEntity, target, menu, player);
		}
		catch (Throwable t) {
			logOnce(blockEntity, "菜单探测失败：" + t);
			return null;
		}
		finally {
			if (menu != null) {
				try {
					// 与真正的开/关 GUI 对称
					menu.removed(player);
				}
				catch (Throwable ignored) {
					// 关闭钩子失败不影响探测结果
				}
			}
		}
	}

	private static SlotRole[] classify(BlockEntity blockEntity, Container target, AbstractContainerMenu menu, Player player) {
		SlotRole[] roles = new SlotRole[target.getContainerSize()];
		boolean matchedOwnContainer = false;
		boolean sawOtherContainer = false;
		for (Slot slot : menu.slots) {
			// 玩家背包等其它容器的槽位与本方块无关
			if (slot.container != target) {
				sawOtherContainer = true;
				continue;
			}
			matchedOwnContainer = true;
			if (!safeIsActive(slot)) {
				// 当前状态下隐藏/禁用的槽位（例如没装升级时的升级按钮位）
				continue;
			}
			int index = slot.getContainerSlot();
			if (index < 0 || index >= roles.length) {
				continue;
			}
			// 同一个容器槽位可能被菜单重复引用，只认第一个能判断出角色的
			if (roles[index] == null) {
				SlotRole role = classifySlot(slot, player);
				if (role != SlotRole.NONE) {
					roles[index] = role;
				}
			}
		}

		if (!matchedOwnContainer) {
			if (sawOtherContainer) {
				logOnce(blockEntity, "菜单槽位指向内部容器而非方块实体，实验性自动探测不支持这种结构");
			}
			return null;
		}

		boolean any = false;
		for (int slot = 0; slot < roles.length; slot++) {
			if (roles[slot] == null) {
				roles[slot] = SlotRole.NONE;
			}
			else {
				any = true;
			}
		}
		return any ? roles : null;
	}

	/**
	 * 单个槽位的角色判断。
	 */
	private static SlotRole classifySlot(Slot slot, Player player) {
		Predicate<ItemStack> accepts = stack -> safeCanInsert(slot, stack);

		if (Probes.anyOf(accepts, Probes.GENERIC)) {
			return Probes.fuelOrInput(accepts);
		}

		// 通用探测物全部被拒：可能是输出槽，也可能是“只认特定物品”的受限槽位（升级件/工具/模板）。
		ItemStack existing = safeGetStack(slot);
		if (!existing.isEmpty() && accepts.test(existing)) {
			// 该槽位认自己当前装的东西 => 它是受限输入槽，不是输出槽，不要把手里的东西塞进去、也不该被取出
			return SlotRole.NONE;
		}
		if (!safeCanTake(slot, player)) {
			// 只能放不能取（展示槽/预览槽）
			return SlotRole.NONE;
		}
		if (safeMaxItemCount(slot) <= 1) {
			// 升级/工具类槽位几乎都限制为 1 个，而输出槽通常是可堆叠的；宁可不动它
			return SlotRole.NONE;
		}
		return SlotRole.OUTPUT;
	}

	/**
	 * 取一个服务端玩家用于构造菜单代理。语义上谁都可以（只用来构造菜单、不做任何写操作），
	 * 取离方块最近的玩家最接近“正在操作这台机器的人”。
	 */
	static Player probePlayer(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		Player best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (ServerPlayer candidate : serverLevel.players()) {
			int distance = candidate.blockPosition().distManhattan(pos);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		return best;
	}

	private static boolean safeCanInsert(Slot slot, ItemStack stack) {
		try {
			return slot.mayPlace(stack);
		}
		catch (Throwable ignored) {
			// 模组自定义过滤逻辑可能抛异常，视为不接受
			return false;
		}
	}

	private static boolean safeCanTake(Slot slot, Player player) {
		try {
			return slot.mayPickup(player);
		}
		catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean safeIsActive(Slot slot) {
		try {
			return slot.isActive();
		}
		catch (Throwable ignored) {
			return false;
		}
	}

	private static ItemStack safeGetStack(Slot slot) {
		try {
			ItemStack stack = slot.getItem();
			return stack == null ? ItemStack.EMPTY : stack;
		}
		catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}

	private static int safeMaxItemCount(Slot slot) {
		try {
			return slot.getMaxStackSize();
		}
		catch (Throwable ignored) {
			// 取不到上限时保守处理（按受限槽位对待）
			return 1;
		}
	}

	private static void logOnce(BlockEntity blockEntity, String message) {
		String key = blockEntity.getClass().getName();
		if (message.equals(LAST_LOGGED.put(key, message))) {
			return;
		}
		LOGGER.info("[auto-detect] {}: {}", key, message);
	}
}
