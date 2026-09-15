package common.jade;

import common.BetterRemoval;
import common.CarryOnKeyState;
import common.ExtractionAction;
import common.ExtractionModeManager;
import common.ExtractionPreviewItems;
import common.ModeState;
import common.OutputSlotExtractor;
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
 * Jade 联动：玩家按住修饰键（左Alt，可改键）看向容器时，
 * 在提示框中添加一行高亮预览（取出/放入/补货）。
 */
public class ExtractionPreviewProvider implements IBlockComponentProvider {

	private static final int MAX_PREVIEW_ICONS = 24;

	@Override
	public Identifier getUid() {
		return BetterRemoval.id("extraction_preview");
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		if (!OutputSlotExtractor.isContainerEnabled("jade_preview")) {
			return;
		}
		PlayerEntity player = accessor.getPlayer();
		if (player == null) {
			return;
		}
		if (!CarryOnKeyState.isPressed()) {
			return;
		}

		BlockEntity blockEntity = accessor.getBlockEntity();
		if (blockEntity == null) {
			return;
		}

		ModeState state = ExtractionModeManager.getClientState();

		// 主动探测模式：不涉及取出/放入，不显示预览
		if (state.action() == ExtractionAction.PROBE) {
			return;
		}

		if (state.action() == ExtractionAction.DEPOSIT) {
			ItemStack held = player.getMainHandStack();
			if (held.isEmpty()) {
				return;
			}
			if (!OutputSlotExtractor.canDepositTo(blockEntity, state.mode(), held)) {
				return;
			}
			addBox(tooltip, Text.translatable("better-removal.jade.deposit").formatted(Formatting.GREEN), List.of(held));
			return;
		}

		if (state.action() == ExtractionAction.RESTOCK) {
			if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) {
				return;
			}
			List<ItemStack> items = readItems(accessor, blockEntity, state);
			if (items == null || items.isEmpty()) {
				return;
			}
			addBox(tooltip, Text.translatable("better-removal.jade.restock").formatted(Formatting.GREEN), items);
			return;
		}

		if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) {
			return;
		}

		List<ItemStack> items = readItems(accessor, blockEntity, state);
		if (items == null || items.isEmpty()) {
			return;
		}
		addBox(tooltip, Text.translatable("better-removal.jade.preview").formatted(Formatting.GREEN), items);
	}

	private static void addBox(ITooltip tooltip, Text label, List<ItemStack> items) {
		IElementHelper helper = IElementHelper.get();
		ITooltip inner = helper.tooltip();
		inner.add(label);
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
		style.borderColor = new int[] { 0xFF55FF55, 0xFF55FF55, 0xFF55FF55, 0xFF55FF55 };
		style.borderWidth = 1;
		tooltip.add(helper.box(inner, style));
	}

	private List<ItemStack> readItems(BlockAccessor accessor, BlockEntity blockEntity, ModeState state) {
		NbtCompound serverData = accessor.getServerData();
		if (serverData != null && serverData.contains(BetterRemovalServerData.DATA_KEY)) {
			NbtList list = serverData.getList(BetterRemovalServerData.DATA_KEY, NbtElement.COMPOUND_TYPE);
			List<ItemStack> items = new ArrayList<>();
			for (NbtElement element : list) {
				ItemStack.fromNbt(accessor.getLevel().getRegistryManager(), element).ifPresent(items::add);
			}
			return items;
		}
		if (state.action() == ExtractionAction.RESTOCK) {
			return OutputSlotExtractor.collectRestockPreview(blockEntity, accessor.getPlayer());
		}
		return ExtractionPreviewItems.collect(blockEntity, state.mode());
	}
}
