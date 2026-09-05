package com.example.jade;

import com.example.BetterRemoval;
import com.example.ExtractionMode;
import com.example.ExtractionModeManager;
import com.example.OutputSlotExtractor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.List;

/**
 * Jade服务端数据提供器：把当前模式下将要取出的真实物品下发给客户端
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
		if (accessor.getBlockEntity() == null) {
			return;
		}

		ExtractionMode mode = ExtractionModeManager.getMode(player);
		List<ItemStack> items = OutputSlotExtractor.collectPreview(player, accessor.getBlockEntity(), mode);
		if (items == null || items.isEmpty()) {
			return;
		}

		NbtList list = new NbtList();
		for (ItemStack stack : items) {
			list.add(stack.writeNbt(new NbtCompound()));
		}
		data.put(DATA_KEY, list);
	}
}