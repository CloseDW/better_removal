package common.experimental;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 实验性：用容器自己的 GUI 菜单推断槽位角色。
 */
final class MenuSlotSupport {

	private static final Logger LOGGER = LoggerFactory.getLogger("better-removal");

	private static final Map<String, String> LAST_LOGGED = new ConcurrentHashMap<>();

	private MenuSlotSupport() {
	}

	static SlotRole[] detect(BlockEntity blockEntity) {
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
		PlayerEntity player = probePlayer(world, blockEntity.getPos());
		if (player == null) {
			return null;
		}

		ScreenHandler menu = null;
		try {
			menu = factory.createMenu(0, new PlayerInventory(player), player);
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
					menu.onClosed(player);
				}
				catch (Throwable ignored) {
					// 关闭钩子失败不影响探测结果
				}
			}
		}
	}

	private static SlotRole[] classify(BlockEntity blockEntity, Inventory target, ScreenHandler menu, PlayerEntity player) {
		SlotRole[] roles = new SlotRole[target.size()];
		boolean matchedOwnContainer = false;
		boolean sawOtherContainer = false;
		for (Slot slot : menu.slots) {
			if (slot.inventory != target) {
				sawOtherContainer = true;
				continue;
			}
			matchedOwnContainer = true;
			if (!safeIsEnabled(slot)) {
				continue;
			}
			int index = slot.getIndex();
			if (index < 0 || index >= roles.length) {
				continue;
			}
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

	private static SlotRole classifySlot(Slot slot, PlayerEntity player) {
		Predicate<ItemStack> accepts = stack -> safeCanInsert(slot, stack);

		if (Probes.anyOf(accepts, Probes.GENERIC)) {
			return Probes.fuelOrInput(accepts);
		}

		ItemStack existing = safeGetStack(slot);
		if (!existing.isEmpty() && accepts.test(existing)) {
			return SlotRole.NONE;
		}
		if (!safeCanTake(slot, player)) {
			return SlotRole.NONE;
		}
		if (safeMaxItemCount(slot) <= 1) {
			return SlotRole.NONE;
		}
		return SlotRole.OUTPUT;
	}

	static PlayerEntity probePlayer(World world, BlockPos pos) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return null;
		}
		PlayerEntity best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (ServerPlayerEntity candidate : serverWorld.getPlayers()) {
			int distance = candidate.getBlockPos().getManhattanDistance(pos);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		return best;
	}

	private static boolean safeCanInsert(Slot slot, ItemStack stack) {
		try {
			return slot.canInsert(stack);
		}
		catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean safeCanTake(Slot slot, PlayerEntity player) {
		try {
			return slot.canTakeItems(player);
		}
		catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean safeIsEnabled(Slot slot) {
		try {
			return slot.isEnabled();
		}
		catch (Throwable ignored) {
			return false;
		}
	}

	private static ItemStack safeGetStack(Slot slot) {
		try {
			ItemStack stack = slot.getStack();
			return stack == null ? ItemStack.EMPTY : stack;
		}
		catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}

	private static int safeMaxItemCount(Slot slot) {
		try {
			return slot.getMaxItemCount();
		}
		catch (Throwable ignored) {
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
