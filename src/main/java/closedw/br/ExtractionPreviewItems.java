package closedw.br;

import closedw.br.container.access.MenuSlotAccess;
import closedw.br.container.support.ContainerAccess;
import closedw.br.container.support.ContainerRegistry;
import closedw.br.container.support.ContainerSupport;
import closedw.br.slot.SlotSet;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 计算当前模式下将要取出的物品（服务端取物逻辑与 Jade 预览共用）。
 * 返回 null 表示该容器不受支持/未启用。
 */
public final class ExtractionPreviewItems {

    private ExtractionPreviewItems() {
    }

    public static List<ItemStack> collect(Player player, BlockEntity blockEntity, SlotSet mode) {
        ContainerSupport support = ContainerRegistry.find(blockEntity);
        if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
            return null;
        }
        int[] slots = support.extractSlots(blockEntity, mode);
        if (slots == null) {
            return null;
        }
        try {
            ContainerAccess access = support.access(blockEntity, player);
            if (access == null) {
                return null;
            }
            List<ItemStack> items = new ArrayList<>();
            for (int slot : slots) {
                // 访问实现内部已做越界保护
                ItemStack stack = access.get(slot);
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
            return items;
        }
        finally {
            // 菜单槽位容器（手动指认映射）会临时构造菜单，读完全部关掉（只关这个玩家的）
            MenuSlotAccess.closeAll(player);
        }
    }
}
