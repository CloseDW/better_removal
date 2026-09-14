package closedw.br.experimental;

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
 */
final class MenuSlotSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, String> LAST_LOGGED = new ConcurrentHashMap<>();

    private MenuSlotSupport() {
    }

    static SlotRole[] detect(BlockEntity blockEntity) {
        if (!(blockEntity instanceof Container target)) {
            return null;
        }
        if (!(blockEntity instanceof MenuProvider factory)) {
            return null;
        }
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
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
            if (slot.container != target) {
                sawOtherContainer = true;
                continue;
            }
            matchedOwnContainer = true;
            if (!safeIsActive(slot)) {
                continue;
            }
            int index = slot.getContainerSlot();
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

    private static SlotRole classifySlot(Slot slot, Player player) {
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
