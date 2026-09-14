package com.better_removal.jade;

import com.better_removal.ExtractionAction;
import com.better_removal.ExtractionModeManager;
import com.better_removal.ModeState;
import com.better_removal.OutputSlotExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.List;

/**
 * Jade服务端数据提供器：把当前模式下将要取出/补货的真实物品下发给客户端
 */
public class BetterRemovalServerData implements IServerDataProvider<BlockAccessor> {

	public static final String DATA_KEY = "better_removal_preview";

	@Override
	public ResourceLocation getUid() {
		return ResourceLocation.fromNamespaceAndPath("better_removal", "server_preview");
	}

	@Override
	public void appendServerData(CompoundTag data, BlockAccessor accessor) {
		if (!(accessor.getPlayer() instanceof ServerPlayer player)) {
			return;
		}
		if (accessor.getBlockEntity() == null) {
			return;
		}
		// 客户端只在按住修饰键时显示预览（ClientEvents.isExtractKeyPressed），
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

		ListTag list = new ListTag();
		for (ItemStack stack : items) {
			list.add(stack.save(new CompoundTag()));
		}
		data.put(DATA_KEY, list);
	}
}