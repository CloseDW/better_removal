package common;

import common.adastra.AdAstraMachineSupport;
import common.adastra.CompressorSupport;
import common.adastra.CryoFreezerSupport;
import common.adastra.EtrionicBlastFurnaceSupport;
import common.adastra.FuelRefinerySupport;
import common.adastra.OxygenLoaderSupport;
import common.aether.AltarSupport;
import common.aether.FreezerSupport;
import common.config.BetterRemovalConfig;
import common.cookingforblockheads.CookingForBlockheadsSupport;
import common.crabbersdelight.CrabTrapSupport;
import common.experimental.AutoDetectSupport;
import common.experimental.SlotProbeReport;
import common.farmandcharm.FarmAndCharmSupport;
import common.farmersdelight.FarmersDelightSupport;
import common.fossil.AnalyzerSupport;
import common.fossil.CultureVatSupport;
import common.fossil.SifterSupport;
import common.fossil.WorktableSupport;
import common.ftbultimine.FTBUltimineSupport;
import common.networking.ExtractKeyStateManager;
import common.vinery.ApplePressSupport;
import common.vinery.FermentationBarrelSupport;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 左Alt（可改键）+ 右键容器直接交互，不用打开容器的 GUI：
 * 空手=按当前行为/模式取出或补货；手持物品=按放入预设放入物品。
 * 模式通过 /br 指令或模式滚轮（按住模式键+滚轮）切换。
 */
public final class OutputSlotExtractor {

	private OutputSlotExtractor() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(OutputSlotExtractor::onUseBlock);
	}

	public static boolean isContainerEnabled(String key) {
		try {
			return BetterRemovalConfig.get().isEnabled(key);
		}
		catch (LinkageError e) {
			return true;
		}
	}

	public static int getConfigInt(String key, int fallback) {
		try {
			return BetterRemovalConfig.get().getInt(key);
		}
		catch (LinkageError e) {
			return fallback;
		}
	}

	/** 实验性功能总开关（默认关闭）。未安装 Configured 时按“关闭”处理。 */
	public static boolean isExperimentalEnabled() {
		try {
			return BetterRemovalConfig.get().isEnabled("experimental_auto_detect");
		}
		catch (LinkageError e) {
			return false;
		}
	}

	/** 查询字符串列表配置。未安装Configured时返回空列表。 */
	public static List<String> getConfigList(String key) {
		try {
			return BetterRemovalConfig.get().getList(key);
		}
		catch (LinkageError e) {
			return List.of();
		}
	}

	/**
	 * 修饰键是否按住：左Alt（可改键，按下状态由客户端同步到服务端）。
	 */
	public static boolean isModifierHeld(PlayerEntity player) {
		return ExtractKeyStateManager.isAltKeyDown(player);
	}

	/**
	 * 返回指定方块实体在当前模式下要取出的槽位。
	 */
	public static int[] getSlotsForMode(BlockEntity blockEntity, ExtractionMode mode) {
		// ---------- Cooking for Blockheads ----------
		// 烤炉不是Inventory（反射拿内部20格容器），且ALL模式需要在通用ALL拦截之前处理
		if (CookingForBlockheadsSupport.isOven(blockEntity)
				&& isContainerEnabled("oven")
				&& !CookingForBlockheadsSupport.isAutomationDisallowed()) {
			return ovenSlots(mode);
		}

		if (mode == ExtractionMode.ALL && getConfigKey(blockEntity) != null) {
			return isContainerEnabled(getConfigKey(blockEntity)) ? allSlots(blockEntity) : null;
		}

		// ---------- 原版 ----------
		if (blockEntity instanceof FurnaceBlockEntity && isContainerEnabled("furnace")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1 };
		}
		if (blockEntity instanceof BlastFurnaceBlockEntity && isContainerEnabled("blast_furnace")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1 };
		}
		if (blockEntity instanceof SmokerBlockEntity && isContainerEnabled("smoker")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1 };
		}
		if (blockEntity instanceof BrewingStandBlockEntity && isContainerEnabled("brewing_stand")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 0, 1, 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 3 }
					: new int[] { 4 };
		}

		if (blockEntity instanceof DropperBlockEntity && isContainerEnabled("dropper")) {
			return allSlots(blockEntity);
		}
		if (blockEntity instanceof DispenserBlockEntity && isContainerEnabled("dispenser")) {
			return allSlots(blockEntity);
		}
		if (blockEntity instanceof HopperBlockEntity && isContainerEnabled("hopper")) {
			return allSlots(blockEntity);
		}
		if (FarmersDelightSupport.isBasket(blockEntity) && isContainerEnabled("basket")) {
			return allSlots(blockEntity);
		}

		// ---------- Ad Astra ----------
		if (CompressorSupport.isCompressor(blockEntity) && isContainerEnabled("compressor")) {
			return mode == ExtractionMode.INPUT ? new int[] { 1 } : new int[] { 2 };
		}
		if (EtrionicBlastFurnaceSupport.isEtrionicBlastFurnace(blockEntity) && isContainerEnabled("etrionic_blast_furnace")) {
			return mode == ExtractionMode.INPUT ? new int[] { 1, 2, 3, 4 } : new int[] { 5, 6, 7, 8 };
		}
		if (FuelRefinerySupport.isFuelRefinery(blockEntity) && isContainerEnabled("fuel_refinery")) {
			return mode == ExtractionMode.INPUT ? new int[] { 1, 3 } : new int[] { 2, 4 };
		}
		if (OxygenLoaderSupport.isOxygenLoader(blockEntity) && isContainerEnabled("oxygen_loader")) {
			return mode == ExtractionMode.INPUT ? new int[] { 1, 3 } : new int[] { 2, 4 };
		}
		if (CryoFreezerSupport.isCryoFreezer(blockEntity) && isContainerEnabled("cryo_freezer")) {
			return mode == ExtractionMode.INPUT ? new int[] { 1, 2 } : new int[] { 3 };
		}

		// ---------- Crabber's Delight ----------
		if (CrabTrapSupport.isCrabTrap(blockEntity) && isContainerEnabled("crab_trap")) {
			return mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		}

		// ---------- The Aether ----------
		if (FreezerSupport.isFreezer(blockEntity) && isContainerEnabled("freezer")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1 };
		}
		if (AltarSupport.isAltar(blockEntity) && isContainerEnabled("altar")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1 };
		}

		// ---------- Vinery ----------
		if (FermentationBarrelSupport.isFermentationBarrel(blockEntity) && isContainerEnabled("fermentation_barrel")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 5 }
					: mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2, 3 }
					: new int[] { 4 };
		}
		if (ApplePressSupport.isApplePress(blockEntity) && isContainerEnabled("apple_press")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 3 }
					: mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2 }
					: new int[] { 3 };
		}

		// ---------- Fossils and Archeology: Revival ----------
		if (AnalyzerSupport.isAnalyzer(blockEntity) && isContainerEnabled("analyzer")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 9, 10, 11, 12 }
					: mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 }
					: new int[] { 9, 10, 11, 12 };
		}
		if (SifterSupport.isSifter(blockEntity) && isContainerEnabled("sifter")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 1, 2, 3, 4, 5 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1, 2, 3, 4, 5 };
		}
		if (CultureVatSupport.isCultureVat(blockEntity) && isContainerEnabled("culture_vat")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1 };
		}
		if (WorktableSupport.isWorktable(blockEntity) && isContainerEnabled("worktable")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1 };
		}

		// ---------- Farm & Charm ----------
		String farmAndCharmKey = FarmAndCharmSupport.getConfigKey(blockEntity);
		if (farmAndCharmKey != null && isContainerEnabled(farmAndCharmKey)) {
			return mode == ExtractionMode.OUTPUT ? FarmAndCharmSupport.getOutputSlots(blockEntity)
					: mode == ExtractionMode.INPUT ? FarmAndCharmSupport.getInputSlots(blockEntity)
					: FarmAndCharmSupport.getFuelSlots(blockEntity);
		}

		// ---------- 实验性：通用容器支持（总开关默认关闭）----------
		if (getConfigKey(blockEntity) == null && AutoDetectSupport.hasRule(blockEntity)) {
			return AutoDetectSupport.getRuleSlotsForMode(blockEntity, mode);
		}
		if (getConfigKey(blockEntity) == null && AutoDetectSupport.isEnabledFor(blockEntity)) {
			int[] auto = AutoDetectSupport.getSlotsForMode(blockEntity, mode);
			if (auto != null) {
				return auto;
			}
		}

		return null;
	}

	/**
	 * 返回容器对应的配置键，未列出的容器返回null。
	 */
	private static String getConfigKey(BlockEntity blockEntity) {
		if (blockEntity instanceof FurnaceBlockEntity) {
			return "furnace";
		}
		if (blockEntity instanceof BlastFurnaceBlockEntity) {
			return "blast_furnace";
		}
		if (blockEntity instanceof SmokerBlockEntity) {
			return "smoker";
		}
		if (blockEntity instanceof BrewingStandBlockEntity) {
			return "brewing_stand";
		}
		if (blockEntity instanceof DropperBlockEntity) {
			return "dropper";
		}
		if (blockEntity instanceof DispenserBlockEntity) {
			return "dispenser";
		}
		if (blockEntity instanceof HopperBlockEntity) {
			return "hopper";
		}
		if (FarmersDelightSupport.isBasket(blockEntity)) {
			return "basket";
		}
		if (CompressorSupport.isCompressor(blockEntity)) {
			return "compressor";
		}
		if (EtrionicBlastFurnaceSupport.isEtrionicBlastFurnace(blockEntity)) {
			return "etrionic_blast_furnace";
		}
		if (FuelRefinerySupport.isFuelRefinery(blockEntity)) {
			return "fuel_refinery";
		}
		if (OxygenLoaderSupport.isOxygenLoader(blockEntity)) {
			return "oxygen_loader";
		}
		if (CryoFreezerSupport.isCryoFreezer(blockEntity)) {
			return "cryo_freezer";
		}
		if (CrabTrapSupport.isCrabTrap(blockEntity)) {
			return "crab_trap";
		}
		if (FreezerSupport.isFreezer(blockEntity)) {
			return "freezer";
		}
		if (AltarSupport.isAltar(blockEntity)) {
			return "altar";
		}
		if (FermentationBarrelSupport.isFermentationBarrel(blockEntity)) {
			return "fermentation_barrel";
		}
		if (ApplePressSupport.isApplePress(blockEntity)) {
			return "apple_press";
		}
		if (AnalyzerSupport.isAnalyzer(blockEntity)) {
			return "analyzer";
		}
		if (SifterSupport.isSifter(blockEntity)) {
			return "sifter";
		}
		if (CultureVatSupport.isCultureVat(blockEntity)) {
			return "culture_vat";
		}
		if (WorktableSupport.isWorktable(blockEntity)) {
			return "worktable";
		}
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			return "oven";
		}
		String farmAndCharmKey = FarmAndCharmSupport.getConfigKey(blockEntity);
		if (farmAndCharmKey != null) {
			return farmAndCharmKey;
		}
		return null;
	}

	private static int[] allSlots(BlockEntity blockEntity) {
		if (blockEntity instanceof Inventory inventory) {
			int size = inventory.size();
			int[] slots = new int[size];
			for (int i = 0; i < size; i++) {
				slots[i] = i;
			}
			return slots;
		}
		return null;
	}

	public static List<BlockPos> getChainPositions(PlayerEntity player, BlockPos clicked) {
		if (!isContainerEnabled("ftb_ultimine") || !FTBUltimineSupport.isKeyHeld(player)) {
			return null;
		}
		Collection<BlockPos> shape = FTBUltimineSupport.getShapePositions(player);
		if (shape == null || shape.isEmpty()) {
			return null;
		}
		int max = getConfigInt("ftb_ultimine_max_containers", 64);
		if (max <= 0) {
			return null;
		}
		List<BlockPos> positions = new ArrayList<>();
		if (!shape.contains(clicked)) {
			positions.add(clicked);
		}
		for (BlockPos pos : shape) {
			if (positions.size() >= max) {
				break;
			}
			positions.add(pos);
		}
		return positions;
	}

	private static boolean handleChainExtraction(PlayerEntity player, World world, List<BlockPos> chain, ExtractionMode mode) {
		boolean any = false;
		for (BlockPos pos : chain) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be == null) {
				continue;
			}
			if (FarmersDelightSupport.isCookingPot(be)) {
				if (!isContainerEnabled("cooking_pot")) {
					continue;
				}
				if (takeFromCookingPot(player, world, be, cookingPotSlots(mode))) {
					be.markDirty();
					any = true;
				}
				continue;
			}
			if (CookingForBlockheadsSupport.isOven(be)) {
				if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
					continue;
				}
				Inventory ovenInventory = CookingForBlockheadsSupport.getInternalContainer(be);
				if (ovenInventory != null && takeSlots(player, ovenInventory, ovenSlots(mode))) {
					ovenInventory.markDirty();
					any = true;
				}
				continue;
			}
			int[] slots = getSlotsForMode(be, mode);
			if (slots == null || !(be instanceof Inventory inventory)) {
				continue;
			}
			if (takeSlots(player, inventory, slots)) {
				inventory.markDirty();
				if (AdAstraMachineSupport.isAdAstraMachine(be)) {
					AdAstraMachineSupport.sync(be);
				}
				any = true;
			}
		}
		if (!any) {
			return false;
		}
		finish(player, world, () -> {
		});
		return true;
	}

	public static List<ItemStack> collectPreview(PlayerEntity player, BlockEntity blockEntity, ExtractionMode mode) {
		if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty() || !isModifierHeld(player)) {
			return ExtractionPreviewItems.collect(blockEntity, mode);
		}
		World world = blockEntity.getWorld();
		if (world == null) {
			return ExtractionPreviewItems.collect(blockEntity, mode);
		}
		List<BlockPos> chain = getChainPositions(player, blockEntity.getPos());
		if (chain == null) {
			return ExtractionPreviewItems.collect(blockEntity, mode);
		}
		List<ItemStack> items = new ArrayList<>();
		for (BlockPos pos : chain) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be == null) {
				continue;
			}
			List<ItemStack> part = ExtractionPreviewItems.collect(be, mode);
			if (part != null && !part.isEmpty()) {
				items.addAll(part);
			}
			if (items.size() >= 128) {
				break;
			}
		}
		return items;
	}

	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
		if (!isModifierHeld(player)) {
			return ActionResult.PASS;
		}
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			return ActionResult.PASS;
		}

		BlockPos pos = hitResult.getBlockPos();
		ModeState state = ExtractionModeManager.getState(player);

		// ---------- 主动探测（实验性）----------
		if (state.action() == ExtractionAction.PROBE) {
			if (!ExtractionModeManager.isProbeAvailable()) {
				return ActionResult.PASS;
			}
			if (!(player instanceof ServerPlayerEntity serverPlayer)) {
				return ActionResult.PASS;
			}
			BlockEntity probeTarget = world.getBlockEntity(pos);
			if (probeTarget == null) {
				return ActionResult.PASS;
			}
			SlotProbeReport.send(serverPlayer, probeTarget);
			return ActionResult.SUCCESS;
		}

		boolean emptyHands = player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty();

		if (emptyHands) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity == null) {
				return ActionResult.PASS;
			}
			if (state.action() == ExtractionAction.RESTOCK) {
				return handleRestock(player, world, pos, blockEntity) ? ActionResult.SUCCESS : ActionResult.PASS;
			}
			if (state.action() != ExtractionAction.EXTRACT) {
				return ActionResult.PASS;
			}
			return handleExtract(player, world, pos, blockEntity, state.mode()) ? ActionResult.SUCCESS : ActionResult.PASS;
		}

		if (state.action() != ExtractionAction.DEPOSIT) {
			return ActionResult.PASS;
		}
		if (!isContainerEnabled("deposit")) {
			return ActionResult.PASS;
		}
		if (state.mode() != ExtractionMode.INPUT && state.mode() != ExtractionMode.FUEL) {
			return ActionResult.PASS;
		}
		ItemStack held = player.getMainHandStack();
		if (held.isEmpty()) {
			return ActionResult.PASS;
		}

		List<BlockPos> chain = getChainPositions(player, pos);
		if (chain != null) {
			boolean chainMoved = handleChainDeposit(player, world, chain, state.mode(), held);
			if (chainMoved) {
				return ActionResult.SUCCESS;
			}
		}

		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity == null) {
			return ActionResult.PASS;
		}
		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			if (!isContainerEnabled("cooking_pot")) {
				return ActionResult.PASS;
			}
			int[] potSlots = cookingPotDepositSlots(state.mode());
			if (potSlots == null) {
				return ActionResult.PASS;
			}
			if (!depositToCookingPot(player, world, blockEntity, potSlots, held, Integer.MAX_VALUE)) {
				return ActionResult.PASS;
			}
			finish(player, world, () -> {
				blockEntity.markDirty();
				player.getInventory().markDirty();
			});
			return ActionResult.SUCCESS;
		}
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return ActionResult.PASS;
			}
			int[] ovenDepositSlots = getDepositSlots(blockEntity, state.mode());
			Inventory ovenInventory = CookingForBlockheadsSupport.getInternalContainer(blockEntity);
			if (ovenDepositSlots == null || ovenInventory == null) {
				return ActionResult.PASS;
			}
			if (!depositToInventory(player, world, ovenInventory, ovenDepositSlots, held, Integer.MAX_VALUE)) {
				return ActionResult.PASS;
			}
			finish(player, world, () -> {
				ovenInventory.markDirty();
				player.getInventory().markDirty();
			});
			return ActionResult.SUCCESS;
		}
		int[] slots = getDepositSlots(blockEntity, state.mode());
		if (slots == null || !(blockEntity instanceof Inventory inventory)) {
			return ActionResult.PASS;
		}
		if (!depositToInventory(player, world, inventory, slots, held, Integer.MAX_VALUE)) {
			return ActionResult.PASS;
		}
		finish(player, world, () -> {
			inventory.markDirty();
			player.getInventory().markDirty();
		});
		return ActionResult.SUCCESS;
	}

	private static boolean handleExtract(PlayerEntity player, World world, BlockPos pos, BlockEntity blockEntity, ExtractionMode mode) {
		List<BlockPos> chain = getChainPositions(player, pos);
		if (chain != null) {
			return handleChainExtraction(player, world, chain, mode);
		}

		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			if (!isContainerEnabled("cooking_pot")) {
				return false;
			}
			return handleCookingPot(player, world, blockEntity, mode);
		}

		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return false;
			}
			Inventory ovenInventory = CookingForBlockheadsSupport.getInternalContainer(blockEntity);
			if (ovenInventory == null) {
				return false;
			}
			return takeFromInventory(player, world, ovenInventory, ovenSlots(mode));
		}

		int[] slots = getSlotsForMode(blockEntity, mode);
		if (slots == null || !(blockEntity instanceof Inventory inventory)) {
			return false;
		}

		boolean result = takeFromInventory(player, world, inventory, slots);
		if (result && AdAstraMachineSupport.isAdAstraMachine(blockEntity)) {
			AdAstraMachineSupport.sync(blockEntity);
		}
		return result;
	}

	/** 判断两个堆叠是否为同种物品（含数据组件）。 */
	private static boolean isSameItem(ItemStack a, ItemStack b) {
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		return ItemStack.areItemsAndComponentsEqual(a, b);
	}

	private static boolean depositToInventory(PlayerEntity player, World world, Inventory inventory, int[] slots, ItemStack held, int maxTake) {
		boolean movedAny = false;
		int taken = 0;
		for (int slot : slots) {
			if (held.isEmpty() || taken >= maxTake) {
				break;
			}
			int put = depositToInventorySlot(player, inventory, slot, held, maxTake - taken);
			if (put > 0) {
				taken += put;
				movedAny = true;
			}
		}
		return movedAny;
	}

	private static boolean handleChainDeposit(PlayerEntity player, World world, List<BlockPos> chain, ExtractionMode slotMode, ItemStack held) {
		List<DepositSlot> slots = new ArrayList<>();
		Set<BlockEntity> dirty = new HashSet<>();
		for (BlockPos pos : chain) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be == null) {
				continue;
			}
			if (FarmersDelightSupport.isCookingPot(be)) {
				if (!isContainerEnabled("cooking_pot")) {
					continue;
				}
				int[] potSlots = cookingPotDepositSlots(slotMode);
				if (potSlots == null) {
					continue;
				}
				for (int slot : potSlots) {
					int weight = cookingPotSlotFree(world, be, slot, held);
					if (weight > 0) {
						slots.add(new DepositSlot(be, null, slot, true, weight));
						dirty.add(be);
					}
				}
				continue;
			}
			if (CookingForBlockheadsSupport.isOven(be)) {
				if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
					continue;
				}
				Inventory ovenInventory = CookingForBlockheadsSupport.getInternalContainer(be);
				int[] ovenDepositSlots = getDepositSlots(be, slotMode);
				if (ovenInventory == null || ovenDepositSlots == null) {
					continue;
				}
				for (int slot : ovenDepositSlots) {
					int weight = slotFreeCapacity(ovenInventory, slot, held);
					if (weight > 0) {
						slots.add(new DepositSlot(be, ovenInventory, slot, false, weight));
						dirty.add(be);
					}
				}
				continue;
			}
			int[] depositSlots = getDepositSlots(be, slotMode);
			if (depositSlots == null || !(be instanceof Inventory inventory)) {
				continue;
			}
			for (int slot : depositSlots) {
				int weight = slotFreeCapacity(inventory, slot, held);
				if (weight > 0) {
					slots.add(new DepositSlot(be, inventory, slot, false, weight));
					dirty.add(be);
				}
			}
		}
		if (slots.isEmpty()) {
			return false;
		}

		long totalWeight = 0;
		for (DepositSlot s : slots) {
			totalWeight += s.weight();
		}
		int remaining = held.getCount();
		int[] share = new int[slots.size()];
		long allocated = 0;
		for (int i = 0; i < slots.size(); i++) {
			share[i] = (int) ((long) remaining * slots.get(i).weight() / totalWeight);
			allocated += share[i];
		}
		int leftover = remaining - (int) allocated;
		while (leftover > 0) {
			int best = -1;
			for (int i = 0; i < slots.size(); i++) {
				if (share[i] < slots.get(i).weight() && (best == -1 || slots.get(i).weight() > slots.get(best).weight())) {
					best = i;
				}
			}
			if (best == -1) {
				break;
			}
			share[best]++;
			leftover--;
		}

		boolean any = false;
		for (int i = 0; i < slots.size(); i++) {
			if (held.isEmpty()) {
				break;
			}
			if (share[i] <= 0) {
				continue;
			}
			DepositSlot s = slots.get(i);
			int actual = s.pot()
					? depositToCookingPotSlot(player, world, s.be(), s.slot(), held, share[i])
					: depositToInventorySlot(player, s.inv(), s.slot(), held, share[i]);
			if (actual > 0) {
				any = true;
			}
		}
		if (!any) {
			return false;
		}
		for (BlockEntity be : dirty) {
			be.markDirty();
			if (AdAstraMachineSupport.isAdAstraMachine(be)) {
				AdAstraMachineSupport.sync(be);
			}
		}
		player.getInventory().markDirty();
		finish(player, world, () -> {
		});
		return true;
	}

	private record DepositSlot(BlockEntity be, Inventory inv, int slot, boolean pot, int weight) {
	}

	private static int slotFreeCapacity(Inventory inv, int slot, ItemStack held) {
		if (slot < 0 || slot >= inv.size()) {
			return 0;
		}
		ItemStack existing = inv.getStack(slot);
		if (existing.isEmpty()) {
			return inv.isValid(slot, held) ? held.getMaxCount() : 0;
		}
		if (isSameItem(existing, held)) {
			return Math.max(0, existing.getMaxCount() - existing.getCount());
		}
		return 0;
	}

	private static int cookingPotSlotFree(World world, BlockEntity be, int slot, ItemStack held) {
		ItemStack probe = held.copy();
		probe.setCount(held.getMaxCount());
		return FarmersDelightSupport.insertToSlot(world, be.getPos(), be, slot, probe, true);
	}

	private static int depositToInventorySlot(PlayerEntity player, Inventory inv, int slot, ItemStack held, int maxTake) {
		if (slot < 0 || slot >= inv.size() || held.isEmpty() || maxTake <= 0) {
			return 0;
		}
		ItemStack existing = inv.getStack(slot);
		if (existing.isEmpty()) {
			if (!inv.isValid(slot, held)) {
				return 0;
			}
			int put = Math.min(Math.min(held.getCount(), held.getMaxCount()), maxTake);
			if (put <= 0) {
				return 0;
			}
			ItemStack copy = held.copy();
			copy.setCount(put);
			inv.setStack(slot, copy);
			held.decrement(put);
			return put;
		}
		if (isSameItem(existing, held)) {
			int canMove = Math.min(Math.min(held.getCount(), existing.getMaxCount() - existing.getCount()), maxTake);
			if (canMove <= 0) {
				return 0;
			}
			existing.increment(canMove);
			inv.setStack(slot, existing);
			held.decrement(canMove);
			return canMove;
		}
		return 0;
	}

	private static int depositToCookingPotSlot(PlayerEntity player, World world, BlockEntity be, int slot, ItemStack held, int maxTake) {
		if (held.isEmpty() || maxTake <= 0) {
			return 0;
		}
		ItemStack portion = held.copy();
		portion.setCount(Math.min(held.getCount(), maxTake));
		int put = FarmersDelightSupport.insertToSlot(world, be.getPos(), be, slot, portion, false);
		if (put > 0) {
			held.decrement(put);
		}
		return put;
	}

	private static void giveBackToPlayer(PlayerEntity player, int count, ItemStack template) {
		if (count <= 0 || template == null || template.isEmpty()) {
			return;
		}
		ItemStack refund = template.copy();
		refund.setCount(count);
		player.getInventory().offer(refund, false);
	}

	private static boolean canAcceptAny(Inventory inventory, int[] slots, ItemStack held) {
		for (int slot : slots) {
			if (slot < 0 || slot >= inventory.size()) {
				continue;
			}
			ItemStack existing = inventory.getStack(slot);
			if (existing.isEmpty()) {
				if (inventory.isValid(slot, held)) {
					return true;
				}
			}
			else if (isSameItem(existing, held) && existing.getCount() < existing.getMaxCount()) {
				return true;
			}
		}
		return false;
	}

	private static boolean depositToCookingPot(PlayerEntity player, World world, BlockEntity blockEntity, int[] slots, ItemStack held, int maxTake) {
		boolean movedAny = false;
		int taken = 0;
		for (int slot : slots) {
			if (held.isEmpty() || taken >= maxTake) {
				break;
			}
			int put = depositToCookingPotSlot(player, world, blockEntity, slot, held, maxTake - taken);
			if (put > 0) {
				taken += put;
				movedAny = true;
			}
		}
		return movedAny;
	}

	private static int[] cookingPotDepositSlots(ExtractionMode mode) {
		if (mode == ExtractionMode.INPUT) {
			return new int[] { 0, 1, 2, 3, 4, 5 };
		}
		return null;
	}

	public static boolean canDepositTo(BlockEntity blockEntity, ExtractionMode mode, ItemStack held) {
		if (blockEntity == null || held == null || held.isEmpty()) {
			return false;
		}
		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			if (!isContainerEnabled("cooking_pot")) {
				return false;
			}
			int[] potSlots = cookingPotDepositSlots(mode);
			if (potSlots == null || blockEntity.getWorld() == null) {
				return false;
			}
			for (int slot : potSlots) {
				if (FarmersDelightSupport.insertToSlot(blockEntity.getWorld(), blockEntity.getPos(), blockEntity, slot, held, true) > 0) {
					return true;
				}
			}
			return false;
		}
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return false;
			}
			int[] ovenDepositSlots = getDepositSlots(blockEntity, mode);
			Inventory ovenInventory = CookingForBlockheadsSupport.getInternalContainer(blockEntity);
			if (ovenDepositSlots == null || ovenInventory == null) {
				return false;
			}
			return canAcceptAny(ovenInventory, ovenDepositSlots, held);
		}
		int[] slots = getDepositSlots(blockEntity, mode);
		if (slots == null || !(blockEntity instanceof Inventory inventory)) {
			return false;
		}
		for (int slot : slots) {
			if (slot < 0 || slot >= inventory.size()) {
				continue;
			}
			ItemStack existing = inventory.getStack(slot);
			if (existing.isEmpty()) {
				if (inventory.isValid(slot, held)) {
					return true;
				}
			}
			else if (isSameItem(existing, held) && existing.getCount() < existing.getMaxCount()) {
				return true;
			}
		}
		return false;
	}

	public static int[] getRestockSlots(BlockEntity blockEntity) {
		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			return cookingPotDepositSlots(ExtractionMode.INPUT);
		}
		Set<Integer> unique = new LinkedHashSet<>();
		int[] input = getDepositSlots(blockEntity, ExtractionMode.INPUT);
		if (input != null) {
			for (int slot : input) {
				unique.add(slot);
			}
		}
		int[] fuel = getDepositSlots(blockEntity, ExtractionMode.FUEL);
		if (fuel != null) {
			for (int slot : fuel) {
				unique.add(slot);
			}
		}
		if (unique.isEmpty()) {
			return null;
		}
		int[] result = new int[unique.size()];
		int i = 0;
		for (int slot : unique) {
			result[i++] = slot;
		}
		return result;
	}

	public static List<ItemStack> collectRestockPreview(BlockEntity blockEntity, PlayerEntity player) {
		List<ItemStack> items = new ArrayList<>();
		List<BlockPos> chain = player == null ? null : getChainPositions(player, blockEntity.getPos());
		if (chain != null && blockEntity.getWorld() != null) {
			for (BlockPos pos : chain) {
				BlockEntity be = blockEntity.getWorld().getBlockEntity(pos);
				if (be != null) {
					collectRestockPreviewSingle(items, be, player);
				}
			}
			return items;
		}
		collectRestockPreviewSingle(items, blockEntity, player);
		return items;
	}

	private static void collectRestockPreviewSingle(List<ItemStack> items, BlockEntity blockEntity, PlayerEntity player) {
		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			if (!isContainerEnabled("cooking_pot") || blockEntity.getWorld() == null) {
				return;
			}
			for (int slot : cookingPotDepositSlots(ExtractionMode.INPUT)) {
				addRestockPreviewItem(items, FarmersDelightSupport.getSlot(blockEntity.getWorld(), blockEntity.getPos(), blockEntity, slot), player);
			}
			return;
		}
		int[] slots = getRestockSlots(blockEntity);
		if (slots == null || !(blockEntity instanceof Inventory inventory)) {
			return;
		}
		for (int slot : slots) {
			if (slot < 0 || slot >= inventory.size()) {
				continue;
			}
			addRestockPreviewItem(items, inventory.getStack(slot), player);
		}
	}

	private static void addRestockPreviewItem(List<ItemStack> items, ItemStack existing, PlayerEntity player) {
		if (existing == null || existing.isEmpty() || existing.getCount() >= existing.getMaxCount()) {
			return;
		}
		if (!hasStockInInventory(player, existing)) {
			return;
		}
		for (ItemStack shown : items) {
			if (isSameItem(shown, existing)) {
				return;
			}
		}
		items.add(existing.copy());
	}

	private static boolean hasStockInInventory(PlayerEntity player, ItemStack template) {
		DefaultedList<ItemStack> main = player.getInventory().main;
		for (int i = 0; i < main.size(); i++) {
			if (isSameItem(main.get(i), template)) {
				return true;
			}
		}
		return false;
	}

	private static boolean handleRestock(PlayerEntity player, World world, BlockPos pos, BlockEntity blockEntity) {
		if (!isContainerEnabled("restock")) {
			return false;
		}
		List<BlockPos> chain = getChainPositions(player, pos);
		if (chain != null) {
			return handleChainRestock(player, world, chain);
		}
		boolean result = restockContainer(player, world, blockEntity);
		if (result) {
			player.getInventory().markDirty();
			finish(player, world, () -> {
			});
		}
		return result;
	}

	private static boolean restockContainer(PlayerEntity player, World world, BlockEntity blockEntity) {
		int[] slots = getRestockSlots(blockEntity);
		if (slots == null) {
			return false;
		}
		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			if (!isContainerEnabled("cooking_pot")) {
				return false;
			}
			if (!restockToCookingPot(player, world, blockEntity, slots)) {
				return false;
			}
			blockEntity.markDirty();
			return true;
		}
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return false;
			}
			Inventory ovenInventory = CookingForBlockheadsSupport.getInternalContainer(blockEntity);
			if (ovenInventory == null || !restockFromInventory(player, world, ovenInventory, slots)) {
				return false;
			}
			ovenInventory.markDirty();
			return true;
		}
		if (!(blockEntity instanceof Inventory inventory) || !restockFromInventory(player, world, inventory, slots)) {
			return false;
		}
		inventory.markDirty();
		if (AdAstraMachineSupport.isAdAstraMachine(blockEntity)) {
			AdAstraMachineSupport.sync(blockEntity);
		}
		return true;
	}

	private static boolean handleChainRestock(PlayerEntity player, World world, List<BlockPos> chain) {
		List<RestockSlot> slots = new ArrayList<>();
		Set<BlockEntity> dirty = new HashSet<>();
		for (BlockPos pos : chain) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be == null) {
				continue;
			}
			collectRestockSlots(player, be, slots, dirty);
		}
		if (slots.isEmpty()) {
			return false;
		}

		boolean any = false;
		for (int i = 0; i < slots.size(); i++) {
			RestockSlot first = slots.get(i);
			if (first == null) {
				continue;
			}
			slots.set(i, null);
			List<RestockSlot> group = new ArrayList<>();
			group.add(first);
			for (int j = i + 1; j < slots.size(); j++) {
				RestockSlot other = slots.get(j);
				if (other != null && isSameItem(first.template(), other.template())) {
					group.add(other);
					slots.set(j, null);
				}
			}
			int stock = countInInventory(player, first.template());
			if (stock <= 0) {
				continue;
			}
			long totalNeed = 0;
			for (RestockSlot g : group) {
				totalNeed += g.need();
			}
			int[] share = new int[group.size()];
			long allocated = 0;
			for (int k = 0; k < group.size(); k++) {
				// 每个槽位最多补到剩余容量（need），避免背包存货多于总需求时超量取出被容器上限截断而吞物品
				share[k] = (int) Math.min(group.get(k).need(), (long) stock * group.get(k).need() / totalNeed);
				allocated += share[k];
			}
			int leftover = stock - (int) allocated;
			while (leftover > 0) {
				int best = -1;
				for (int k = 0; k < group.size(); k++) {
					if (share[k] < group.get(k).need() && (best == -1 || group.get(k).need() > group.get(best).need())) {
						best = k;
					}
				}
				if (best == -1) {
					break;
				}
				share[best]++;
				leftover--;
			}
			for (int k = 0; k < group.size(); k++) {
				if (share[k] <= 0) {
					continue;
				}
				RestockSlot g = group.get(k);
				if (g.pot()) {
					ItemStack probe = g.template().copy();
					probe.setCount(share[k]);
					int canInsert = FarmersDelightSupport.insertToSlot(world, g.be().getPos(), g.be(), g.slot(), probe, true);
					if (canInsert <= 0) {
						continue;
					}
					int taken = takeFromPlayerInventory(player, g.template(), canInsert);
					if (taken <= 0) {
						continue;
					}
					ItemStack portion = g.template().copy();
					portion.setCount(taken);
					int put = FarmersDelightSupport.insertToSlot(world, g.be().getPos(), g.be(), g.slot(), portion, false);
					if (put < taken) {
						giveBackToPlayer(player, taken - put, g.template());
					}
					any = true;
				}
				else {
					int taken = takeFromPlayerInventory(player, g.template(), share[k]);
					if (taken <= 0) {
						continue;
					}
					ItemStack existing = g.inv().getStack(g.slot());
					if (existing.isEmpty() || !isSameItem(existing, g.template())) {
						giveBackToPlayer(player, taken, g.template());
						continue;
					}
					existing.increment(taken);
					g.inv().setStack(g.slot(), existing);
					any = true;
				}
			}
		}
		if (!any) {
			return false;
		}
		for (BlockEntity be : dirty) {
			be.markDirty();
			if (AdAstraMachineSupport.isAdAstraMachine(be)) {
				AdAstraMachineSupport.sync(be);
			}
		}
		player.getInventory().markDirty();
		finish(player, world, () -> {
		});
		return true;
	}

	private record RestockSlot(BlockEntity be, Inventory inv, int slot, boolean pot, ItemStack template, int need) {
	}

	private static void collectRestockSlots(PlayerEntity player, BlockEntity be, List<RestockSlot> slots, Set<BlockEntity> dirty) {
		int[] restockSlots = getRestockSlots(be);
		if (restockSlots == null) {
			return;
		}
		if (FarmersDelightSupport.isCookingPot(be)) {
			if (!isContainerEnabled("cooking_pot") || be.getWorld() == null) {
				return;
			}
			for (int slot : restockSlots) {
				ItemStack existing = FarmersDelightSupport.getSlot(be.getWorld(), be.getPos(), be, slot);
				int need = restockNeed(existing);
				if (need > 0 && countInInventory(player, existing) > 0) {
					slots.add(new RestockSlot(be, null, slot, true, existing.copy(), need));
					dirty.add(be);
				}
			}
			return;
		}
		if (CookingForBlockheadsSupport.isOven(be)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return;
			}
			Inventory inv = CookingForBlockheadsSupport.getInternalContainer(be);
			if (inv == null) {
				return;
			}
			for (int slot : restockSlots) {
				if (slot < 0 || slot >= inv.size()) {
					continue;
				}
				ItemStack existing = inv.getStack(slot);
				int need = restockNeed(existing);
				if (need > 0 && countInInventory(player, existing) > 0) {
					slots.add(new RestockSlot(be, inv, slot, false, existing.copy(), need));
					dirty.add(be);
				}
			}
			return;
		}
		if (!(be instanceof Inventory inv)) {
			return;
		}
		for (int slot : restockSlots) {
			if (slot < 0 || slot >= inv.size()) {
				continue;
			}
			ItemStack existing = inv.getStack(slot);
			int need = restockNeed(existing);
			if (need > 0 && countInInventory(player, existing) > 0) {
				slots.add(new RestockSlot(be, inv, slot, false, existing.copy(), need));
				dirty.add(be);
			}
		}
	}

	private static int restockNeed(ItemStack existing) {
		if (existing == null || existing.isEmpty()) {
			return 0;
		}
		return Math.max(0, existing.getMaxCount() - existing.getCount());
	}

	private static int countInInventory(PlayerEntity player, ItemStack template) {
		int count = 0;
		DefaultedList<ItemStack> main = player.getInventory().main;
		for (int i = 0; i < main.size(); i++) {
			ItemStack stack = main.get(i);
			if (!stack.isEmpty() && isSameItem(stack, template)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static int takeFromPlayerInventory(PlayerEntity player, ItemStack template, int need) {
		int taken = 0;
		DefaultedList<ItemStack> main = player.getInventory().main;
		for (int i = 0; i < main.size() && taken < need; i++) {
			ItemStack stack = main.get(i);
			if (stack.isEmpty() || !isSameItem(stack, template)) {
				continue;
			}
			int take = Math.min(need - taken, stack.getCount());
			stack.decrement(take);
			taken += take;
		}
		return taken;
	}

	private static boolean restockFromInventory(PlayerEntity player, World world, Inventory inventory, int[] slots) {
		boolean movedAny = false;
		for (int slot : slots) {
			if (slot < 0 || slot >= inventory.size()) {
				continue;
			}
			ItemStack existing = inventory.getStack(slot);
			if (existing.isEmpty()) {
				continue;
			}
			int need = existing.getMaxCount() - existing.getCount();
			if (need <= 0) {
				continue;
			}
			int stock = countInInventory(player, existing);
			if (stock <= 0) {
				continue;
			}
			int taken = takeFromPlayerInventory(player, existing, Math.min(need, stock));
			if (taken <= 0) {
				continue;
			}
			existing.increment(taken);
			inventory.setStack(slot, existing);
			movedAny = true;
		}
		return movedAny;
	}

	private static boolean restockToCookingPot(PlayerEntity player, World world, BlockEntity blockEntity, int[] slots) {
		boolean movedAny = false;
		for (int slot : slots) {
			ItemStack existing = FarmersDelightSupport.getSlot(world, blockEntity.getPos(), blockEntity, slot);
			if (existing == null || existing.isEmpty()) {
				continue;
			}
			int need = existing.getMaxCount() - existing.getCount();
			if (need <= 0) {
				continue;
			}
			int stock = countInInventory(player, existing);
			if (stock <= 0) {
				continue;
			}
			ItemStack probe = existing.copy();
			probe.setCount(Math.min(need, stock));
			int canInsert = FarmersDelightSupport.insertToSlot(world, blockEntity.getPos(), blockEntity, slot, probe, true);
			if (canInsert <= 0) {
				continue;
			}
			int taken = takeFromPlayerInventory(player, existing, canInsert);
			if (taken <= 0) {
				continue;
			}
			ItemStack portion = existing.copy();
			portion.setCount(taken);
			FarmersDelightSupport.insertToSlot(world, blockEntity.getPos(), blockEntity, slot, portion, false);
			movedAny = true;
		}
		return movedAny;
	}

	public static int[] getDepositSlots(BlockEntity blockEntity, ExtractionMode mode) {
		if (mode != ExtractionMode.INPUT && mode != ExtractionMode.FUEL) {
			return null;
		}
		if (blockEntity instanceof FurnaceBlockEntity) {
			return isContainerEnabled("furnace") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}
		if (blockEntity instanceof BlastFurnaceBlockEntity) {
			return isContainerEnabled("blast_furnace") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}
		if (blockEntity instanceof SmokerBlockEntity) {
			return isContainerEnabled("smoker") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}
		if (blockEntity instanceof BrewingStandBlockEntity) {
			return isContainerEnabled("brewing_stand") ? (mode == ExtractionMode.INPUT ? new int[] { 3 } : new int[] { 4 }) : null;
		}
		if (blockEntity instanceof DropperBlockEntity) {
			return isContainerEnabled("dropper") ? allSlots(blockEntity) : null;
		}
		if (blockEntity instanceof DispenserBlockEntity) {
			return isContainerEnabled("dispenser") ? allSlots(blockEntity) : null;
		}
		if (blockEntity instanceof HopperBlockEntity) {
			return isContainerEnabled("hopper") ? allSlots(blockEntity) : null;
		}
		if (FarmersDelightSupport.isBasket(blockEntity)) {
			return isContainerEnabled("basket") ? allSlots(blockEntity) : null;
		}
		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			return null;
		}
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return null;
			}
			return mode == ExtractionMode.INPUT ? CookingForBlockheadsSupport.getInputSlots() : CookingForBlockheadsSupport.getFuelSlots();
		}

		if (CompressorSupport.isCompressor(blockEntity)) {
			return isContainerEnabled("compressor") && mode == ExtractionMode.INPUT ? new int[] { 1 } : null;
		}
		if (EtrionicBlastFurnaceSupport.isEtrionicBlastFurnace(blockEntity)) {
			return isContainerEnabled("etrionic_blast_furnace") && mode == ExtractionMode.INPUT ? new int[] { 1, 2, 3, 4 } : null;
		}
		if (FuelRefinerySupport.isFuelRefinery(blockEntity)) {
			return isContainerEnabled("fuel_refinery") && mode == ExtractionMode.INPUT ? new int[] { 1, 3 } : null;
		}
		if (OxygenLoaderSupport.isOxygenLoader(blockEntity)) {
			return isContainerEnabled("oxygen_loader") && mode == ExtractionMode.INPUT ? new int[] { 1, 3 } : null;
		}
		if (CryoFreezerSupport.isCryoFreezer(blockEntity)) {
			return isContainerEnabled("cryo_freezer") && mode == ExtractionMode.INPUT ? new int[] { 1, 2 } : null;
		}
		if (CrabTrapSupport.isCrabTrap(blockEntity)) {
			return isContainerEnabled("crab_trap") && mode == ExtractionMode.INPUT ? new int[] { 0 } : null;
		}
		if (FreezerSupport.isFreezer(blockEntity)) {
			return isContainerEnabled("freezer") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}
		if (AltarSupport.isAltar(blockEntity)) {
			return isContainerEnabled("altar") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}
		if (FermentationBarrelSupport.isFermentationBarrel(blockEntity)) {
			return isContainerEnabled("fermentation_barrel") ? (mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2, 3 } : new int[] { 4 }) : null;
		}
		if (ApplePressSupport.isApplePress(blockEntity)) {
			return isContainerEnabled("apple_press") ? (mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2 } : new int[] { 2 }) : null;
		}
		if (AnalyzerSupport.isAnalyzer(blockEntity)) {
			return isContainerEnabled("analyzer") && mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 } : null;
		}
		if (SifterSupport.isSifter(blockEntity)) {
			return isContainerEnabled("sifter") && mode == ExtractionMode.INPUT ? new int[] { 0 } : null;
		}
		if (CultureVatSupport.isCultureVat(blockEntity)) {
			return isContainerEnabled("culture_vat") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}
		if (WorktableSupport.isWorktable(blockEntity)) {
			return isContainerEnabled("worktable") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}
		String farmAndCharmKey = FarmAndCharmSupport.getConfigKey(blockEntity);
		if (farmAndCharmKey != null) {
			return isContainerEnabled(farmAndCharmKey)
					? (mode == ExtractionMode.INPUT ? FarmAndCharmSupport.getInputSlots(blockEntity) : FarmAndCharmSupport.getFuelSlots(blockEntity))
					: null;
		}

		if (getConfigKey(blockEntity) == null && AutoDetectSupport.hasRule(blockEntity)) {
			return AutoDetectSupport.getRuleSlotsForMode(blockEntity, mode);
		}
		if (getConfigKey(blockEntity) == null && AutoDetectSupport.isEnabledFor(blockEntity)) {
			return AutoDetectSupport.getDepositSlots(blockEntity, mode);
		}

		return null;
	}

	private static int[] ovenSlots(ExtractionMode mode) {
		if (mode == ExtractionMode.ALL) {
			return CookingForBlockheadsSupport.getAllSlots();
		}
		if (mode == ExtractionMode.OUTPUT) {
			return CookingForBlockheadsSupport.getOutputSlots();
		}
		if (mode == ExtractionMode.INPUT) {
			return CookingForBlockheadsSupport.getInputSlots();
		}
		return CookingForBlockheadsSupport.getFuelSlots();
	}

	private static boolean handleCookingPot(PlayerEntity player, World world, BlockEntity blockEntity, ExtractionMode mode) {
		if (!takeFromCookingPot(player, world, blockEntity, cookingPotSlots(mode))) {
			return false;
		}
		finish(player, world, blockEntity::markDirty);
		return true;
	}

	private static int[] cookingPotSlots(ExtractionMode mode) {
		if (mode == ExtractionMode.ALL) {
			return new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
		}
		if (mode == ExtractionMode.OUTPUT) {
			return new int[] { 8 };
		}
		if (mode == ExtractionMode.INPUT) {
			return new int[] { 0, 1, 2, 3, 4, 5 };
		}
		return new int[] { 7 };
	}

	private static boolean takeFromCookingPot(PlayerEntity player, World world, BlockEntity blockEntity, int[] slots) {
		boolean takenAny = false;
		for (int slot : slots) {
			ItemStack output = FarmersDelightSupport.getSlot(world, blockEntity.getPos(), blockEntity, slot);
			if (output == null || output.isEmpty()) {
				continue;
			}
			int placed = tryInsertToPlayer(player, output);
			if (placed <= 0) {
				continue;
			}
			FarmersDelightSupport.removeFromSlot(world, blockEntity.getPos(), blockEntity, slot, placed);
			takenAny = true;
		}
		return takenAny;
	}

	private static int tryInsertToPlayer(PlayerEntity player, ItemStack stack) {
		int original = stack.getCount();
		ItemStack toInsert = stack.copy();
		player.getInventory().offer(toInsert, false);
		return original - toInsert.getCount();
	}

	private static boolean takeFromInventory(PlayerEntity player, World world, Inventory inventory, int[] slots) {
		if (!takeSlots(player, inventory, slots)) {
			return false;
		}
		finish(player, world, inventory::markDirty);
		return true;
	}

	private static boolean takeSlots(PlayerEntity player, Inventory inventory, int[] slots) {
		boolean takenAny = false;
		for (int slot : slots) {
			if (slot < 0 || slot >= inventory.size()) {
				continue;
			}
			ItemStack result = inventory.getStack(slot);
			if (result.isEmpty()) {
				continue;
			}
			int taken = tryTakeSlot(player, result);
			if (taken <= 0) {
				continue;
			}
			result.decrement(taken);
			if (result.isEmpty()) {
				inventory.setStack(slot, ItemStack.EMPTY);
			}
			takenAny = true;
		}
		return takenAny;
	}

	/**
	 * 单次最多取出到物品堆叠上限，防止容器中存在超过堆叠上限的物品被原样塞进玩家背包。
	 */
	private static int tryTakeSlot(PlayerEntity player, ItemStack result) {
		int maxCount = result.getMaxCount();
		int takeCount = Math.min(result.getCount(), maxCount);
		ItemStack toInsert = result.copy();
		toInsert.setCount(takeCount);
		player.getInventory().insertStack(toInsert);
		return takeCount - toInsert.getCount();
	}

	private static void finish(PlayerEntity player, World world, Runnable markDirty) {
		markDirty.run();
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, 1.0f);
	}
}
