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
import common.crabbersdelight.CrabTrapSupport;
import common.farmersdelight.FarmersDelightSupport;
import common.fossil.AnalyzerSupport;
import common.fossil.CultureVatSupport;
import common.fossil.SifterSupport;
import common.fossil.WorktableSupport;
import common.networking.ExtractKeyStateManager;
import common.vinery.ApplePressSupport;
import common.vinery.FermentationBarrelSupport;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

/**
 * 潜行+空手右键容器可以直接取出物品（按当前取出模式）同时不用打开容器的 GUI。
 * 模式通过 /br 指令或按键切换（output/input/fuel/all）。
 * 容器可通过Configured的配置菜单开关。
 */
public final class OutputSlotExtractor {

	private OutputSlotExtractor() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(OutputSlotExtractor::onUseBlock);
	}

	/**
	 * 查询某个容器是否启用。未安装Configured时默认全部启用
	 */
	public static boolean isContainerEnabled(String key) {
		try {
			return BetterRemovalConfig.get().isEnabled(key);
		}
		catch (LinkageError e) {
			return true;
		}
	}

	/**
	 * 返回指定方块实体在当前模式下要取出的槽位。
	 */
	public static int[] getSlotsForMode(BlockEntity blockEntity, ExtractionMode mode) {
		if (mode == ExtractionMode.ALL) {
			return allSlots(blockEntity);
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
			// 0-2 药水槽，3 材料槽（下界疣等），4 燃料槽（烈焰粉）
			return mode == ExtractionMode.OUTPUT ? new int[] { 0, 1, 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 3 }
					: new int[] { 4 };
		}

		// 没有单独输入/燃料槽的容器，三种模式都取全部
		if (blockEntity instanceof DropperBlockEntity && isContainerEnabled("dropper")) {
			return allSlots(blockEntity);
		}
		if (blockEntity instanceof DispenserBlockEntity && isContainerEnabled("dispenser")) {
			return allSlots(blockEntity);
		}
		if (blockEntity instanceof HopperBlockEntity && isContainerEnabled("hopper")) {
			return allSlots(blockEntity);
		}
		// Farmer's Delight木篮竹篮
		if (FarmersDelightSupport.isBasket(blockEntity) && isContainerEnabled("basket")) {
			return allSlots(blockEntity);
		}

		// ---------- Ad Astra ----------
		// 压缩机：0电 1输入 2输出
		if (CompressorSupport.isCompressor(blockEntity) && isContainerEnabled("compressor")) {
			return mode == ExtractionMode.INPUT ? new int[] { 1 } : new int[] { 2 };
		}
		// 电力高炉：0电 1-4输入 5-8输出
		if (EtrionicBlastFurnaceSupport.isEtrionicBlastFurnace(blockEntity) && isContainerEnabled("etrionic_blast_furnace")) {
			return mode == ExtractionMode.INPUT ? new int[] { 1, 2, 3, 4 } : new int[] { 5, 6, 7, 8 };
		}
		// 燃料精炼机：0电 1输入(原油) 2输出(空桶) 3流体输入 4输出(满桶)
		if (FuelRefinerySupport.isFuelRefinery(blockEntity) && isContainerEnabled("fuel_refinery")) {
			return mode == ExtractionMode.INPUT ? new int[] { 1, 3 } : new int[] { 2, 4 };
		}
		// 氧气装载机：0电池 1输入(水桶) 2输出(空桶) 3流体输入 4输出
		if (OxygenLoaderSupport.isOxygenLoader(blockEntity) && isContainerEnabled("oxygen_loader")) {
			return mode == ExtractionMode.INPUT ? new int[] { 1, 3 } : new int[] { 2, 4 };
		}
		// 低温冷冻机：0电 1输入 2流体输入 3输出
		if (CryoFreezerSupport.isCryoFreezer(blockEntity) && isContainerEnabled("cryo_freezer")) {
			return mode == ExtractionMode.INPUT ? new int[] { 1, 2 } : new int[] { 3 };
		}

		// ---------- Crabber's Delight ----------
		// 捕蟹笼：0诱饵 1-9捕获物
		if (CrabTrapSupport.isCrabTrap(blockEntity) && isContainerEnabled("crab_trap")) {
			return mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		}

		// ---------- The Aether ----------
		// 冷冻器/神能炉：0输入 1燃料 2输出
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
		// 陈酿桶：0葡萄汁 1-3食材 4酒瓶 5输出
		if (FermentationBarrelSupport.isFermentationBarrel(blockEntity) && isContainerEnabled("fermentation_barrel")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 5 }
					: mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2, 3 }
					: new int[] { 4 };
		}
		// 苹果压榨器：0压榨输入 1中间产物 2酒瓶输入 3输出
		if (ApplePressSupport.isApplePress(blockEntity) && isContainerEnabled("apple_press")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 3 }
					: mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2 }
					: new int[] { 3 };
		}

		// ---------- Fossils and Archeology: Revival ----------
		// 分析仪：0-8输入 9-12输出
		if (AnalyzerSupport.isAnalyzer(blockEntity) && isContainerEnabled("analyzer")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 9, 10, 11, 12 }
					: mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 }
					: new int[] { 9, 10, 11, 12 };
		}
		// 筛子：0输入 1-5输出
		if (SifterSupport.isSifter(blockEntity) && isContainerEnabled("sifter")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 1, 2, 3, 4, 5 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1, 2, 3, 4, 5 };
		}
		// 培养槽：0输入 1燃料 2输出
		if (CultureVatSupport.isCultureVat(blockEntity) && isContainerEnabled("culture_vat")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1 };
		}
		// 考古工作台：0输入 1燃料 2输出
		if (WorktableSupport.isWorktable(blockEntity) && isContainerEnabled("worktable")) {
			return mode == ExtractionMode.OUTPUT ? new int[] { 2 }
					: mode == ExtractionMode.INPUT ? new int[] { 0 }
					: new int[] { 1 };
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

	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
		// 仅潜行；同时安装Carry On时改用左Alt键（避免与Carry On的Shift+右键搬起冲突）
		if (isCarryOnLoaded() ? !ExtractKeyStateManager.isAltKeyDown(player) : !player.isSneaking()) {
			return ActionResult.PASS;
		}
		// 只处理主手
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		// 主手和副手都必须为空
		if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) {
			return ActionResult.PASS;
		}
		// 只在服务端执行逻辑
		if (world.isClient) {
			return ActionResult.PASS;
		}

		BlockEntity blockEntity = world.getBlockEntity(hitResult.getBlockPos());
		ExtractionMode mode = ExtractionModeManager.getMode(player);

		// Farmer's Delight厨锅
		if (blockEntity != null && FarmersDelightSupport.isCookingPot(blockEntity)) {
			if (!isContainerEnabled("cooking_pot")) {
				return ActionResult.PASS;
			}
			return handleCookingPot(player, world, blockEntity, mode);
		}

		int[] slots = getSlotsForMode(blockEntity, mode);
		if (slots == null) {
			return ActionResult.PASS;
		}
		if (!(blockEntity instanceof Inventory inventory)) {
			return ActionResult.PASS;
		}

		ActionResult result = takeFromInventory(player, world, inventory, slots);
		if (result == ActionResult.SUCCESS && AdAstraMachineSupport.isAdAstraMachine(blockEntity)) {
			// Ad Astra机器在玩家取走物品后需要同步
			AdAstraMachineSupport.sync(blockEntity);
		}
		return result;
	}

	/**
	 * 处理农夫乐事厨锅。
	 * 厨锅槽位：0-5食材输入，6成品显示，7容器槽，8成品输出。
	 * 通过反射访问getInventory()返回的ItemStackHandler。
	 */
	private static ActionResult handleCookingPot(PlayerEntity player, World world, BlockEntity blockEntity, ExtractionMode mode) {
		int[] slots;
		if (mode == ExtractionMode.ALL) {
			slots = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
		}
		else if (mode == ExtractionMode.OUTPUT) {
			slots = new int[] { 8 };
		}
		else if (mode == ExtractionMode.INPUT) {
			slots = new int[] { 0, 1, 2, 3, 4, 5 };
		}
		else {
			// 燃料模式：容器槽
			slots = new int[] { 7 };
		}

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

		if (!takenAny) {
			return ActionResult.PASS;
		}
		finish(player, world, blockEntity::markDirty);
		return ActionResult.SUCCESS;
	}

	/**
	 * 把物品放入玩家背包
	 */
	private static int tryInsertToPlayer(PlayerEntity player, ItemStack stack) {
		int original = stack.getCount();
		ItemStack toInsert = stack.copy();
		player.getInventory().offer(toInsert, false);
		return original - toInsert.getCount();
	}

	private static ActionResult takeFromInventory(PlayerEntity player, World world, Inventory inventory, int[] slots) {
		boolean takenAny = false;
		for (int slot : slots) {
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

		if (!takenAny) {
			return ActionResult.PASS;
		}

		finish(player, world, inventory::markDirty);
		return ActionResult.SUCCESS;
	}

	/**
	 * 使用 insertStack
	 * 不使用offer，offer在背包放不下时会dropItem直接扔到地上
	 * 额外限制：单次最多取出到物品堆叠上限，防止容器中存在超过堆叠上限的物品
	 *  Vinery 苹果压榨器的输出槽 BUG被原样塞进玩家背包。
	 */
	private static int tryTakeSlot(PlayerEntity player, ItemStack result) {
		int maxCount = result.getMaxCount();
		int takeCount = Math.min(result.getCount(), maxCount);
		ItemStack toInsert = result.copy();
		toInsert.setCount(takeCount);
		player.getInventory().insertStack(toInsert);
		return takeCount - toInsert.getCount();
	}

	private static boolean isCarryOnLoaded() {
		return FabricLoader.getInstance().isModLoaded("carryon");
	}

	private static void finish(PlayerEntity player, World world, Runnable markDirty) {
		markDirty.run();
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, 1.0f);
	}
}