package com.example;

import com.example.adastra.AdAstraMachineSupport;
import com.example.adastra.CompressorSupport;
import com.example.adastra.CryoFreezerSupport;
import com.example.adastra.EtrionicBlastFurnaceSupport;
import com.example.adastra.FuelRefinerySupport;
import com.example.adastra.OxygenLoaderSupport;
import com.example.aether.AltarSupport;
import com.example.aether.FreezerSupport;
import com.example.config.BetterRemovalConfig;
import com.example.crabbersdelight.CrabTrapSupport;
import com.example.farmersdelight.FarmersDelightSupport;
import com.example.fossil.AnalyzerSupport;
import com.example.fossil.CultureVatSupport;
import com.example.fossil.SifterSupport;
import com.example.fossil.WorktableSupport;
import com.example.networking.ExtractKeyStateManager;
import com.example.vinery.ApplePressSupport;
import com.example.vinery.FermentationBarrelSupport;
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
 * 潜行+空手右键容器可以直接把输出槽的物品全部取出同时不用打开容器的 GUI。
 * 容器可通过Configured的配置菜单开关
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
	private static boolean isContainerEnabled(String key) {
		try {
			return BetterRemovalConfig.get().isEnabled(key);
		}
		catch (LinkageError e) {
			return true;
		}
	}

	/**
	 * 返回指定方块实体的输出槽
	 */
	private static int[] getOutputSlots(BlockEntity blockEntity) {
		if (blockEntity instanceof FurnaceBlockEntity && isContainerEnabled("furnace")) {
			return new int[] { 2 };
		}
		if (blockEntity instanceof BlastFurnaceBlockEntity && isContainerEnabled("blast_furnace")) {
			return new int[] { 2 };
		}
		if (blockEntity instanceof SmokerBlockEntity && isContainerEnabled("smoker")) {
			return new int[] { 2 };
		}
		if (blockEntity instanceof BrewingStandBlockEntity && isContainerEnabled("brewing_stand")) {
			return new int[] { 0, 1, 2 };
		}

		// 没有单独输入槽的容器取出全部槽位
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

		// Ad Astra压缩机：输出槽2
		if (CompressorSupport.isCompressor(blockEntity) && isContainerEnabled("compressor")) {
			return CompressorSupport.getOutputSlots();
		}

		// Ad Astra电力高炉：输出槽5-8
		if (EtrionicBlastFurnaceSupport.isEtrionicBlastFurnace(blockEntity) && isContainerEnabled("etrionic_blast_furnace")) {
			return EtrionicBlastFurnaceSupport.getOutputSlots();
		}

		// Ad Astra燃料精炼机：输出槽2，4
		if (FuelRefinerySupport.isFuelRefinery(blockEntity) && isContainerEnabled("fuel_refinery")) {
			return FuelRefinerySupport.getOutputSlots();
		}

		// Ad Astra氧气装载机：输出槽2，4
		if (OxygenLoaderSupport.isOxygenLoader(blockEntity) && isContainerEnabled("oxygen_loader")) {
			return OxygenLoaderSupport.getOutputSlots();
		}

		// Ad Astra低温冷冻机：输出槽3
		if (CryoFreezerSupport.isCryoFreezer(blockEntity) && isContainerEnabled("cryo_freezer")) {
			return CryoFreezerSupport.getOutputSlots();
		}

		// Crabber's Delight捕蟹笼：捕获物槽1-9
		if (CrabTrapSupport.isCrabTrap(blockEntity) && isContainerEnabled("crab_trap")) {
			return CrabTrapSupport.getCatchSlots();
		}

		// The Aether冷冻器：输出槽2
		if (FreezerSupport.isFreezer(blockEntity) && isContainerEnabled("freezer")) {
			return FreezerSupport.getOutputSlots();
		}

		// The Aether神能炉：输出槽2
		if (AltarSupport.isAltar(blockEntity) && isContainerEnabled("altar")) {
			return AltarSupport.getOutputSlots();
		}

		// Vinery陈酿桶：输出槽5
		if (FermentationBarrelSupport.isFermentationBarrel(blockEntity) && isContainerEnabled("fermentation_barrel")) {
			return FermentationBarrelSupport.getOutputSlots();
		}

		// Vinery苹果压榨器：输出槽3
		if (ApplePressSupport.isApplePress(blockEntity) && isContainerEnabled("apple_press")) {
			return ApplePressSupport.getOutputSlots();
		}

		// Fossils and Archeology: Revival分析仪：输出槽9-12
		if (AnalyzerSupport.isAnalyzer(blockEntity) && isContainerEnabled("analyzer")) {
			return AnalyzerSupport.getOutputSlots();
		}

		// Fossils and Archeology: Revival筛子：输出槽1-5
		if (SifterSupport.isSifter(blockEntity) && isContainerEnabled("sifter")) {
			return SifterSupport.getOutputSlots();
		}

		// Fossils and Archeology: Revival培养槽：输出槽2
		if (CultureVatSupport.isCultureVat(blockEntity) && isContainerEnabled("culture_vat")) {
			return CultureVatSupport.getOutputSlots();
		}

		// Fossils and Archeology: Revival考古工作台：输出槽2
		if (WorktableSupport.isWorktable(blockEntity) && isContainerEnabled("worktable")) {
			return WorktableSupport.getOutputSlots();
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

		// Farmer's Delight厨锅
		if (blockEntity != null && FarmersDelightSupport.isCookingPot(blockEntity)) {
			if (!isContainerEnabled("cooking_pot")) {
				return ActionResult.PASS;
			}
			ItemStack output = FarmersDelightSupport.getOutputSlot(world, hitResult.getBlockPos(), blockEntity);
			if (output == null || output.isEmpty()) {
				return ActionResult.PASS;
			}
			int placed = tryInsertToPlayer(player, output);
			if (placed <= 0) {
				return ActionResult.PASS;
			}
			FarmersDelightSupport.removeFromOutputSlot(world, hitResult.getBlockPos(), blockEntity, placed);
			finish(player, world, blockEntity::markDirty);
			return ActionResult.SUCCESS;
		}

		int[] outputSlots = getOutputSlots(blockEntity);
		if (outputSlots == null) {
			return ActionResult.PASS;
		}
		if (!(blockEntity instanceof Inventory inventory)) {
			return ActionResult.PASS;
		}

		ActionResult result = takeFromInventory(player, world, inventory, outputSlots);
		if (result == ActionResult.SUCCESS && AdAstraMachineSupport.isAdAstraMachine(blockEntity)) {
			// Ad Astra机器在玩家取走物品后需要同步
			AdAstraMachineSupport.sync(blockEntity);
		}
		return result;
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
	 */
	private static int tryTakeSlot(PlayerEntity player, ItemStack result) {
		int originalCount = result.getCount();
		ItemStack toInsert = result.copy();
		player.getInventory().insertStack(toInsert);
		return originalCount - toInsert.getCount();
	}

	private static boolean isCarryOnLoaded() {
		return FabricLoader.getInstance().isModLoaded("carryon");
	}

	private static void finish(PlayerEntity player, World world, Runnable markDirty) {
		markDirty.run();
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, 1.0f);
	}
}
