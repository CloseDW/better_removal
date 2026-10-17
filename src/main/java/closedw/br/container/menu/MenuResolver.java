package closedw.br.container.menu;

import closedw.br.BetterRemoval;
import closedw.br.slot.SlotRole;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 菜单槽位的定位与自检。
 */
public final class MenuResolver {

    /** 失败原因的翻译键（由服务端判定、客户端显示）。 */
    public static final String ERROR_NOT_PROVIDER = "betterremoval.mapping.error.not_provider";
    public static final String ERROR_MENU_FAILED = "betterremoval.mapping.error.menu_failed";
    public static final String ERROR_SIZE_CHANGED = "betterremoval.mapping.error.size_changed";
    public static final String ERROR_SLOT_UNUSABLE = "betterremoval.mapping.error.slot_unusable";

    private static final Logger LOGGER = BetterRemoval.LOGGER;
    private static final Map<String, String> LAST_LOGGED = new ConcurrentHashMap<>();

    private MenuResolver() {
    }

    /**
     * 构造一个临时菜单。失败（不是 MenuProvider、构造抛异常、客户端侧）返回 null。
     * <p>
     * containerId 用 0：这个菜单从不注册给玩家，所以它和玩家自己的背包菜单不会冲突。
     */
    public static AbstractContainerMenu open(BlockEntity blockEntity, Player player) {
        if (blockEntity == null || player == null) {
            return null;
        }
        Level level = player.level();
        if (level == null || level.isClientSide) {
            // 菜单只能在服务端构造
            return null;
        }
        if (!(blockEntity instanceof MenuProvider provider)) {
            return null;
        }
        try {
            return provider.createMenu(0, player.getInventory(), player);
        }
        catch (Throwable t) {
            logOnce(blockEntity, "menu construction failed: " + t);
            return null;
        }
    }

    /** 关闭临时菜单，与真正的开/关 GUI 对称。 */
    public static void close(AbstractContainerMenu menu, Player player) {
        if (menu == null) {
            return;
        }
        try {
            menu.removed(player);
        }
        catch (Throwable ignored) {
            // 关闭钩子失败不影响已经完成的物品移动
        }
    }

    /**
     * 该菜单槽位能不能被本模组操作：索引在范围内、不是假槽位（合成预览/模板槽）、也不是玩家背包槽位。
     */
    public static boolean isUsable(AbstractContainerMenu menu, Player player, int menuIndex) {
        if (menu == null || menuIndex < 0 || menuIndex >= menu.slots.size()) {
            return false;
        }
        try {
            Slot slot = menu.getSlot(menuIndex);
            if (slot == null || slot.isFake()) {
                return false;
            }
            return player == null || slot.container != player.getInventory();
        }
        catch (Throwable t) {
            return false;
        }
    }

    /**
     * 离线重建自检：拿真实容器把一条映射完整解析一遍（含槽位可用性与菜单槽位总数校验）。
     * <p>
     * 每次指认改动都会跑一遍，通过才落盘——这样"保存成功但用不了"不会发生：
     * 不能在没有 GUI 的情况下重建菜单的容器，会在指认阶段就被拒绝。
     *
     * @return null 表示通过；否则是失败原因的翻译键
     */
    public static String check(MenuMap map, BlockEntity blockEntity, Player player) {
        if (map == null) {
            return ERROR_MENU_FAILED;
        }
        if (!(blockEntity instanceof MenuProvider)) {
            return ERROR_NOT_PROVIDER;
        }
        AbstractContainerMenu menu = open(blockEntity, player);
        if (menu == null) {
            return ERROR_MENU_FAILED;
        }
        try {
            if (menu.slots.size() != map.menuSize()) {
                return ERROR_SIZE_CHANGED;
            }
            for (SlotRole role : List.of(SlotRole.INPUT, SlotRole.FUEL, SlotRole.OUTPUT)) {
                for (int index : map.slots(role)) {
                    if (!isUsable(menu, player, index)) {
                        return ERROR_SLOT_UNUSABLE;
                    }
                }
            }
            return null;
        }
        catch (Throwable t) {
            return ERROR_MENU_FAILED;
        }
        finally {
            close(menu, player);
        }
    }

    /**
     * 取一个服务端玩家用来构造菜单代理。菜单构造需要一个玩家实例，语义上谁都可以
     * （只用来构造菜单、不做任何写操作），取离方块最近的玩家最接近"正在操作这台机器的人"。
     */
    public static Player probePlayer(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return null;
        }
        Player best = null;
        int bestDistance = Integer.MAX_VALUE;
        try {
            for (ServerPlayer candidate : serverLevel.players()) {
                BlockPos candidatePos = candidate.blockPosition();
                int distance = Math.abs(candidatePos.getX() - pos.getX())
                        + Math.abs(candidatePos.getY() - pos.getY())
                        + Math.abs(candidatePos.getZ() - pos.getZ());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        catch (Throwable t) {
            return null;
        }
        return best;
    }

    private static void logOnce(BlockEntity blockEntity, String message) {
        String key = blockEntity.getClass().getName();
        if (message.equals(LAST_LOGGED.put(key, message))) {
            return;
        }
        LOGGER.info("[menu-map] {}: {}", key, message);
    }
}
