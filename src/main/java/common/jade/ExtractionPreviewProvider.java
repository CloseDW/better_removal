package common.jade;

import common.BetterRemoval;
import common.CarryOnKeyState;
import common.ExtractionMode;
import common.ExtractionModeManager;
import common.ExtractionPreviewItems;
import common.OutputSlotExtractor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Jade 联动：当玩家空手按住修饰键（潜行；装了Carry On用左Alt）看向容器时，
 * 在Jade提示框中添加一行高亮预览，圈出将要取出的物品。
 * 物品优先读取服务端通过 {@link BetterRemovalServerData} 下发的真实数据，
 * 其次回退到客户端本地读取。
 */
public class ExtractionPreviewProvider implements IBlockComponentProvider {

	private static final int MAX_PREVIEW_ICONS = 24;

	@Override
	public Identifier getUid() {
		return BetterRemoval.id("extraction_preview");
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		// Jade 预览开关（Configured）
		if (!OutputSlotExtractor.isContainerEnabled("jade_preview")) {
			return;
		}
		PlayerEntity player = accessor.getPlayer();
		if (player == null) {
			return;
		}
		// 空手
		if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) {
			return;
		}
		// 按住修饰键
		if (!isModifierHeld(player)) {
			return;
		}

		BlockEntity blockEntity = accessor.getBlockEntity();
		if (blockEntity == null) {
			return;
		}

		List<ItemStack> items = readItems(accessor, blockEntity);
		if (items == null || items.isEmpty()) {
			return;
		}

		// 高亮预览行：标签 + 物品图标，外层用彩色边框包裹
		IElementHelper helper = IElementHelper.get();
		ITooltip inner = helper.tooltip();
		inner.add(Text.translatable("better-removal.jade.preview").formatted(Formatting.GREEN));
		int count = 0;
		for (ItemStack stack : items) {
			if (count >= MAX_PREVIEW_ICONS) {
				break;
			}
			inner.append(helper.item(stack));
			count++;
		}

		BoxStyle.GradientBorder style = BoxStyle.GradientBorder.DEFAULT_VIEW_GROUP.clone();
		style.bgColor = 0x2200AA00;
		style.borderColor = new int[] { 0xFF55FF55 };
		style.borderWidth = 1;
		tooltip.add(helper.box(inner, style));
	}

	/**
	 * 优先读取服务端下发的真实物品；缺失时回退到客户端本地读取。
	 */
	private List<ItemStack> readItems(BlockAccessor accessor, BlockEntity blockEntity) {
		NbtCompound serverData = accessor.getServerData();
		if (serverData.contains(BetterRemovalServerData.DATA_KEY)) {
			NbtList list = serverData.getList(BetterRemovalServerData.DATA_KEY, NbtElement.COMPOUND_TYPE);
			List<ItemStack> items = new ArrayList<>();
			for (NbtElement element : list) {
				ItemStack.fromNbt(accessor.getLevel().getRegistryManager(), element).ifPresent(items::add);
			}
			return items;
		}
		ExtractionMode mode = ExtractionModeManager.getClientMode();
		return ExtractionPreviewItems.collect(blockEntity, mode);
	}

	private static boolean isModifierHeld(PlayerEntity player) {
		if (FabricLoader.getInstance().isModLoaded("carryon")) {
			return CarryOnKeyState.isPressed();
		}
		return player.isSneaking();
	}
}