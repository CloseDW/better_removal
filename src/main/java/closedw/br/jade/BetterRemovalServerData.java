package closedw.br.jade;

import closedw.br.ExtractionMode;
import closedw.br.ExtractionModeManager;
import closedw.br.ExtractionPreviewItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.List;

/**
 * Jade 服务端数据提供器：把当前模式下将要取出的真实物品下发给客户端。
 * 解决部分容器（原版/Aether/Fossil/Vinery等）不把库存同步到客户端方块实体，
 * 导致客户端本地无法读取物品的问题。
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

        ExtractionMode mode = ExtractionModeManager.getMode(player);
        List<ItemStack> items = ExtractionPreviewItems.collect(accessor.getBlockEntity(), mode);
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