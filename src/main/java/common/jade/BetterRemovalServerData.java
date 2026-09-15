package common.jade;

import common.BetterRemoval;
import common.ExtractionAction;
import common.ExtractionModeManager;
import common.ModeState;
import common.OutputSlotExtractor;
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
 * Jade 服务端数据提供器：把当前模式下将要取出/补货的真实物品下发给客户端。
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
		if (!OutputSlotExtractor.isModifierHeld(player)) {
			return;
		}

		ModeState state = ExtractionModeManager.getState(player);
		List<ItemStack> items;
		if (state.action() == ExtractionAction.EXTRACT) {
			items = OutputSlotExtractor.collectPreview(player, blockEntity, state.mode());
		}
		else if (state.action() == ExtractionAction.RESTOCK) {
			items = OutputSlotExtractor.collectRestockPreview(blockEntity, player);
		}
		else {
			return;
		}
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
