package closedw.br.jade;

import closedw.br.OutputSlotExtractor;
import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionMode;
import closedw.br.behavior.InteractionModeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.List;

/**
 * Jade服务端数据提供器：把当前模式下将要取出/补货的真实物品下发给客户端。
 */
public class BetterRemovalServerData implements IServerDataProvider<BlockAccessor> {

    public static final String DATA_KEY = "betterremoval_preview";

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("betterremoval", "server_preview");
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (accessor.getBlockEntity() == null) {
            return;
        }
        // 客户端只在按住修饰键时显示预览，服务端同样仅在修饰键按下时计算并下发数据
        if (!OutputSlotExtractor.isModifierHeld(player)) {
            return;
        }

        InteractionMode state = InteractionModeManager.getState(player);
        // 放入模式的预览不需要服务端数据（客户端直接显示主手物品）
        List<ItemStack> items;
        if (state.behavior() == Behavior.EXTRACT) {
            items = OutputSlotExtractor.collectPreview(player, accessor.getBlockEntity(), state.slots());
        }
        else if (state.behavior() == Behavior.RESTOCK) {
            // 补货：服务端同时知道容器内容与玩家背包，直接算出可补物品
            items = OutputSlotExtractor.collectRestockPreview(accessor.getBlockEntity(), player);
        }
        else {
            return;
        }
        if (items == null || items.isEmpty()) {
            return;
        }

        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            list.add(stack.save(accessor.getLevel().registryAccess()));
        }
        data.put(DATA_KEY, list);
    }
}
