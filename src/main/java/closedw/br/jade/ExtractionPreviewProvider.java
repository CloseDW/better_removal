package closedw.br.jade;

import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionMode;
import closedw.br.behavior.InteractionModeManager;
import closedw.br.BetterRemoval;
import closedw.br.BetterRemovalClient;
import closedw.br.ExtractionPreviewItems;
import closedw.br.OutputSlotExtractor;
import closedw.br.slot.SlotSet;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Jade联动：当玩家按住修饰键（左Alt，可改键）看向容器时，
 * 在Jade提示框中添加一行高亮预览，圈出将要取出的物品（空手）
 * 或将要放入的物品（手持物品+放入模式）。
 * 物品优先读取服务端通过 {@link BetterRemovalServerData} 下发的数据，
 * 缺失时回退到客户端本地读取。
 */
public class ExtractionPreviewProvider implements IBlockComponentProvider {

	private static final int MAX_PREVIEW_ICONS = 24;

	@Override
	public Identifier getUid() {
		return closedw.br.BetterRemoval.id("extraction_preview");
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
		// 按住修饰键（左Alt，可改键）
		if (!BetterRemovalClient.isExtractKeyPressed()) {
			return;
		}

		BlockEntity blockEntity = accessor.getBlockEntity();
		if (blockEntity == null) {
			return;
		}

		InteractionMode state = InteractionModeManager.getClientState();

		// 主动探测 / 手动指认模式：都不涉及取出/放入，不显示预览
		// （手动指认时槽位模式被归一化成 ALL，若不拦截会显示一行假的"即将取出"）
		if (state.behavior() == Behavior.PROBE || state.behavior() == Behavior.MAPPING) {
			return;
		}

		// 放入模式：显示主手物品（无需服务端数据；但"放不放得进"要按槽位逐一校验）
		if (state.behavior() == Behavior.DEPOSIT) {
			ItemStack held = player.getMainHandStack();
			if (held.isEmpty()) {
				return;
			}
			// 槽位过滤/容量逐槽校验：全部放不下时不显示预览
			if (!canDeposit(player, blockEntity, state.slots(), held)) {
				return;
			}
			addBox(tooltip, Text.translatable("better-removal.jade.deposit").formatted(Formatting.GREEN), List.of(held));
			return;
		}

		// 补货模式：空手，显示可补充的已有物品（服务端下发，回退客户端本地计算）
		if (state.behavior() == Behavior.RESTOCK) {
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

		// 取出模式：空手
		if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) {
			return;
		}

		List<ItemStack> items = readItems(accessor, blockEntity, state);
		if (items == null || items.isEmpty()) {
			return;
		}
		addBox(tooltip, Text.translatable("better-removal.jade.preview").formatted(Formatting.GREEN), items);
	}

	/**
	 * 放入预览的「放得进吗」判定缓存。
	 * <p>
	 * 手动指认的容器（菜单槽位）要判断放不放得进，必须临时构造菜单——工具提示是每帧渲染的，
	 * 每帧建一次菜单太浪费。这里按（方块、槽位模式、手持物）缓存一小段时间：
	 * 手持物一换（滚轮/切换物品）立刻重算，容器内容的变化最多晚 {@link #DEPOSIT_CHECK_TTL_MS} 毫秒反映。
	 */
	private static final long DEPOSIT_CHECK_TTL_MS = 250L;

	private static BlockPos depositCheckPos;
	private static SlotSet depositCheckSlots;
	private static ItemStack depositCheckItem = ItemStack.EMPTY;
	private static boolean depositCheckResult;
	private static long depositCheckAt;

	/** 语义同 {@link OutputSlotExtractor#canDepositTo}，只是带一层短时缓存。 */
	private static boolean canDeposit(PlayerEntity player, BlockEntity blockEntity, SlotSet slots, ItemStack held) {
		long now = System.currentTimeMillis();
		boolean cached = now - depositCheckAt < DEPOSIT_CHECK_TTL_MS
				&& blockEntity.getPos().equals(depositCheckPos)
				&& slots == depositCheckSlots
				&& held.getCount() == depositCheckItem.getCount()
				&& ItemStack.canCombine(held, depositCheckItem);
		if (cached) {
			return depositCheckResult;
		}
		boolean result = OutputSlotExtractor.canDepositTo(player, blockEntity, slots, held);
		depositCheckPos = blockEntity.getPos();
		depositCheckSlots = slots;
		depositCheckItem = held.copy();
		depositCheckResult = result;
		depositCheckAt = now;
		return result;
	}

	/** 高亮预览行：标签 + 物品图标，外层用彩色边框包裹 */
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

		BoxStyle style = new BoxStyle();
		style.borderColor = 0xFF55FF55;
		style.borderWidth = 1;
		style.bgColor = 0x2200AA00;
		tooltip.add(helper.box(inner, style));
	}

	/**
	 * 优先读取服务端下发的真实物品；缺失时按行为回退到客户端本地读取
	 */
	private List<ItemStack> readItems(BlockAccessor accessor, BlockEntity blockEntity, InteractionMode state) {
		NbtCompound serverData = accessor.getServerData();
		if (serverData != null && serverData.contains(BetterRemovalServerData.DATA_KEY)) {
			NbtList list = serverData.getList(BetterRemovalServerData.DATA_KEY, 10);
			List<ItemStack> items = new ArrayList<>();
			for (NbtElement element : list) {
				ItemStack stack = ItemStack.fromNbt((NbtCompound) element);
				// 坏 NBT 会解析出空物品，别让它占掉一个图标位
				if (!stack.isEmpty()) {
					items.add(stack);
				}
			}
			return items;
		}
		if (state.behavior() == Behavior.RESTOCK) {
			return OutputSlotExtractor.collectRestockPreview(blockEntity, accessor.getPlayer());
		}
		return ExtractionPreviewItems.collect(accessor.getPlayer(), blockEntity, state.slots());
	}
}
