package closedw.br.jade;

import closedw.br.BetterRemoval;
import closedw.br.ExtractionAction;
import closedw.br.ExtractionModeManager;
import closedw.br.ModeState;
import closedw.br.OutputSlotExtractor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.List;

/**
 * Jade服务端数据提供器：把当前模式下将要取出/补货的真实物品下发给客户端
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
		// 客户端只在按住修饰键时显示预览（BetterRemovalClient.isExtractKeyPressed），
		// 服务端同样仅在修饰键按下时计算并下发数据，避免每次渲染都做无谓的聚合
		if (!OutputSlotExtractor.isModifierHeld(player)) {
			return;
		}

		ModeState state = ExtractionModeManager.getState(player);
		// 放入模式的预览不需要服务端数据（客户端直接显示主手物品）
		List<ItemStack> items;
		if (state.action() == ExtractionAction.EXTRACT) {
			items = OutputSlotExtractor.collectPreview(player, accessor.getBlockEntity(), state.mode());
		}
		else if (state.action() == ExtractionAction.RESTOCK) {
			// 补货：服务端同时知道容器内容与玩家背包，直接算出可补物品
			items = OutputSlotExtractor.collectRestockPreview(accessor.getBlockEntity(), player);
		}
		else {
			return;
		}
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