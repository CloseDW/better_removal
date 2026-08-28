package common.jade;

import common.BetterRemoval;
import common.ExtractionMode;
import common.ExtractionModeManager;
import common.ExtractionPreviewItems;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.List;

/**
 * Jade 服务端数据提供器：把当前模式下将要取出的真实物品下发给客户端。
 * 解决部分容器（原版/Aether/Fossil/Vinery等）不把库存同步到客户端方块实体，
 * 导致客户端本地无法读取物品的问题。
 */
public class BetterRemovalServerData implements IServerDataProvider<BlockAccessor> {

	public static final String DATA_KEY = "better_removal_preview";

	@Override
	public Identifier getUid() {
		return BetterRemoval.id("server_preview");
	}

	@Override
	public void appendServerData(NbtCompound data, BlockAccessor accessor) {
		if (!(accessor.getPlayer() instanceof ServerPlayerEntity player)) {
			return;
		}
		BlockEntity blockEntity = accessor.getBlockEntity();
		if (blockEntity == null) {
			return;
		}

		ExtractionMode mode = ExtractionModeManager.getMode(player);
		List<ItemStack> items = ExtractionPreviewItems.collect(blockEntity, mode);
		if (items == null || items.isEmpty()) {
			return;
		}

		NbtList list = new NbtList();
		for (ItemStack stack : items) {
			list.add(stack.encode(accessor.getLevel().getRegistryManager()));
		}
		data.put(DATA_KEY, list);
	}
}