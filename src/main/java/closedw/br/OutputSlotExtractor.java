package closedw.br;

import closedw.br.adastra.AdAstraMachineSupport;
import closedw.br.adastra.CompressorSupport;
import closedw.br.adastra.CryoFreezerSupport;
import closedw.br.adastra.EtrionicBlastFurnaceSupport;
import closedw.br.adastra.FuelRefinerySupport;
import closedw.br.adastra.OxygenLoaderSupport;
import closedw.br.aether.AltarSupport;
import closedw.br.aether.FreezerSupport;
import closedw.br.config.BetterRemovalConfig;
import closedw.br.cookingforblockheads.CookingForBlockheadsSupport;
import closedw.br.crabbersdelight.CrabTrapSupport;
import closedw.br.ftbultimine.FTBUltimineSupport;
import closedw.br.farmersdelight.FarmersDelightSupport;
import closedw.br.fossil.AnalyzerSupport;
import closedw.br.fossil.CultureVatSupport;
import closedw.br.fossil.SifterSupport;
import closedw.br.fossil.WorktableSupport;
import closedw.br.networking.ExtractKeyStateManager;
import closedw.br.vinery.ApplePressSupport;
import closedw.br.vinery.FermentationBarrelSupport;
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
 * 空手=按当前槽位模式取出物品；手持物品=按当前槽位模式放入物品（放入预设）；
 * 补货预设=空手右击，从背包向容器输入槽和燃料槽补充已有同类物品（空槽不补）。
 * 模式通过 /br 指令或模式滚轮（按住模式键+滚轮）切换。
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
	 * 查询整数配置。未安装Configured时返回fallback。
	 */
	public static int getConfigInt(String key, int fallback) {
		try {
			return BetterRemovalConfig.get().getInt(key);
		}
		catch (LinkageError e) {
			return fallback;
		}
	}

	/**
	 * 修饰键是否按住：左Alt（可改键）。
	 */
	public static boolean isModifierHeld(PlayerEntity player) {
		return ExtractKeyStateManager.isAltKeyDown(player);
	}

	/**
	 * 返回指定方块实体在当前模式下要取出的槽位。
	 * 公有：服务端取物与 Jade 客户端预览共用。
	 */
	public static int[] getSlotsForMode(BlockEntity blockEntity, ExtractionMode mode) {
		// ---------- Cooking for Blockheads ----------
		// 烤炉不是Inventory（反射拿内部20格容器），且ALL模式需要在通用ALL拦截之前处理
		if (CookingForBlockheadsSupport.isOven(blockEntity)
				&& isContainerEnabled("oven")
				&& !CookingForBlockheadsSupport.isAutomationDisallowed()) {
			return ovenSlots(mode);
		}

		if (mode == ExtractionMode.ALL) {
			// ALL受容器开关约束，不允许绕过配置
			String key = getConfigKey(blockEntity);
			return key != null && isContainerEnabled(key) ? allSlots(blockEntity) : null;
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

	/**
	 * 返回容器对应的配置键（与getSlotsForMode中的分支一一对应），未列出的容器返回null。
	 * ALL 模式用它检查容器开关，避免绕过配置取出。
	 */
	private static String getConfigKey(BlockEntity blockEntity) {
		// ---------- 原版 ----------
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
		// Farmer's Delight木篮竹篮
		if (FarmersDelightSupport.isBasket(blockEntity)) {
			return "basket";
		}
		// ---------- Ad Astra ----------
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
		// ---------- Crabber's Delight ----------
		if (CrabTrapSupport.isCrabTrap(blockEntity)) {
			return "crab_trap";
		}
		// ---------- The Aether ----------
		if (FreezerSupport.isFreezer(blockEntity)) {
			return "freezer";
		}
		if (AltarSupport.isAltar(blockEntity)) {
			return "altar";
		}
		// ---------- Vinery ----------
		if (FermentationBarrelSupport.isFermentationBarrel(blockEntity)) {
			return "fermentation_barrel";
		}
		if (ApplePressSupport.isApplePress(blockEntity)) {
			return "apple_press";
		}
		// ---------- Fossils and Archeology: Revival ----------
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
		// ---------- Cooking for Blockheads ----------
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			return "oven";
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

	/**
	 * FTB Ultimine连锁（取出/放入共用）的容器位置列表。
	 * 条件：配置开启 + 按住Ultimine键 + Ultimine存在缓存的连锁形状。
	 * 点击的容器保证在列表中；数量受ftb_ultimine_max_containers限制。
	 * 返回null表示不适用连锁。
	 */
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

	/**
	 * 连锁取出：遍历Ultimine连锁形状内的所有容器，逐个按当前模式取出。
	 * 不适用的容器（未支持/被关闭）自动跳过。
	 */
	private static ActionResult handleChainExtraction(PlayerEntity player, World world, List<BlockPos> chain, ExtractionMode mode) {
		boolean any = false;
		for (BlockPos pos : chain) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be == null) {
				continue;
			}
			// 农夫乐事厨锅走反射路径
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
			// 烤炉走反射路径拿内部容器（不是Inventory）
			if (CookingForBlockheadsSupport.isOven(be)) {
				if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
					continue;
				}
				Inventory ovenInventory = CookingForBlockheadsSupport.getInternalInventory(be);
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
					// Ad Astra机器在玩家取走物品后需要同步
					AdAstraMachineSupport.sync(be);
				}
				any = true;
			}
		}
		if (!any) {
			return ActionResult.PASS;
		}
		// 取物音效只播放一次，避免连锁时刷屏
		finish(player, world, () -> {
		});
		return ActionResult.SUCCESS;
	}

	/**
	 * Jade预览：计算将要取出的物品。
	 * 空手+按住修饰键+按住Ultimine键时聚合整个连锁形状内所有支持容器；否则单容器。
	 */
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
			// 限制聚合大小，避免向客户端下发过大NBT
			if (items.size() >= 128) {
				break;
			}
		}
		return items;
	}

	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
		// 修饰键：左Alt（可改键）
		if (!isModifierHeld(player)) {
			return ActionResult.PASS;
		}
		// 只处理主手
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		// 只在服务端执行逻辑
		if (world.isClient) {
			return ActionResult.PASS;
		}

		ModeState state = ExtractionModeManager.getState(player);
		boolean emptyHands = player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty();

		// ---------- 取出（空手）/ 补货（空手）----------
		if (emptyHands) {
			BlockEntity blockEntity = world.getBlockEntity(hitResult.getBlockPos());
			if (blockEntity == null) {
				return ActionResult.PASS;
			}
			// 补货：从背包向容器输入/燃料槽补充已有同类物品（空槽不补）
			if (state.action() == ExtractionAction.RESTOCK) {
				return handleRestock(player, world, hitResult.getBlockPos(), blockEntity);
			}
			// 放入预设下空手右击不动作
			if (state.action() != ExtractionAction.EXTRACT) {
				return ActionResult.PASS;
			}
			return handleExtract(player, world, hitResult.getBlockPos(), blockEntity, state.mode());
		}

		// ---------- 放入（手持物品）----------
		if (state.action() != ExtractionAction.DEPOSIT) {
			return ActionResult.PASS;
		}
		if (!isContainerEnabled("deposit")) {
			return ActionResult.PASS;
		}
		// 放入预设只有 input/fuel
		if (state.mode() != ExtractionMode.INPUT && state.mode() != ExtractionMode.FUEL) {
			return ActionResult.PASS;
		}
		ItemStack held = player.getMainHandStack();
		if (held.isEmpty()) {
			return ActionResult.PASS;
		}

		// FTB Ultimine连锁：一排容器一起放入
		List<BlockPos> chain = getChainPositions(player, hitResult.getBlockPos());
		if (chain != null) {
			ActionResult chainResult = handleChainDeposit(player, world, chain, state.mode(), held);
			if (chainResult == ActionResult.SUCCESS) {
				return ActionResult.SUCCESS;
			}
			// 连锁没有放入任何物品时回退到单个容器处理
		}

		BlockEntity blockEntity = world.getBlockEntity(hitResult.getBlockPos());
		if (blockEntity == null) {
			return ActionResult.PASS;
		}
		// Farmer's Delight厨锅走反射路径不是Inventory
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
		// 烤炉走反射路径拿内部容器（不是Inventory），isValid过滤
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return ActionResult.PASS;
			}
			int[] ovenDepositSlots = getDepositSlots(blockEntity, state.mode());
			Inventory ovenInventory = CookingForBlockheadsSupport.getInternalInventory(blockEntity);
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

	/**
	 * 取出分支：连锁 → 厨锅 → 普通容器。
	 */
	private static ActionResult handleExtract(PlayerEntity player, World world, BlockPos pos, BlockEntity blockEntity, ExtractionMode mode) {
		// ---------- FTB Ultimine连锁取出 ----------
		// 修饰键+空手再按住Ultimine键时，一次性取出整个连锁形状内所有支持容器的对应槽位
		List<BlockPos> chain = getChainPositions(player, pos);
		if (chain != null) {
			return handleChainExtraction(player, world, chain, mode);
		}

		// Farmer's Delight厨锅
		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			if (!isContainerEnabled("cooking_pot")) {
				return ActionResult.PASS;
			}
			return handleCookingPot(player, world, blockEntity, mode);
		}

		// 烤炉走反射路径拿内部容器（不是Inventory）
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return ActionResult.PASS;
			}
			Inventory ovenInventory = CookingForBlockheadsSupport.getInternalInventory(blockEntity);
			if (ovenInventory == null) {
				return ActionResult.PASS;
			}
			return takeFromInventory(player, world, ovenInventory, ovenSlots(mode));
		}

		int[] slots = getSlotsForMode(blockEntity, mode);
		if (slots == null || !(blockEntity instanceof Inventory inventory)) {
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
	 * 判断两个堆叠是否为同种物品（含NBT）。
	 */
	private static boolean isSameItem(ItemStack a, ItemStack b) {
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		return a.isOf(b.getItem()) && java.util.Objects.equals(a.getNbt(), b.getNbt());
	}

	/**
	 * 把主手物品放入容器的指定槽位（同类堆叠优先合并，空槽其次）。
	 * 每个槽位都会先经过 Inventory.isValid 校验。
	 * @param maxTake 本次最多放入的数量（连锁均分时限制单容器份额）
	 * @return 是否放入了至少一个物品
	 */
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

	/**
	 * 连锁放入：把主手物品按权重分摊到Ultimine连锁形状内的所有可接收槽位。
	 * 权重=槽位剩余容量（空槽按整组计、同类槽按剩余空间计）
	 * 放不下的余量留在手上
	 */
	private static ActionResult handleChainDeposit(PlayerEntity player, World world, List<BlockPos> chain, ExtractionMode slotMode, ItemStack held) {
		// 第一遍：收集所有能接收手持物品的槽位及其权重
		List<DepositSlot> slots = new ArrayList<>();
		Set<BlockEntity> dirty = new HashSet<>();
		for (BlockPos pos : chain) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be == null) {
				continue;
			}
			// 农夫乐事厨锅走反射路径
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
			// 烤炉走反射路径拿内部容器（不是Inventory）
			if (CookingForBlockheadsSupport.isOven(be)) {
				if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
					continue;
				}
				Inventory ovenInventory = CookingForBlockheadsSupport.getInternalInventory(be);
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
			return ActionResult.PASS;
		}

		// 按权重比例分配手持物品（权重=槽位剩余容量）
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
		// 余数补足给权重最大且未填满的槽位
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

		// 第二遍：按份额放入，实际放入少于份额的余量留在手上
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
			return ActionResult.PASS;
		}
		for (BlockEntity be : dirty) {
			be.markDirty();
			if (AdAstraMachineSupport.isAdAstraMachine(be)) {
				// Ad Astra机器在物品变化后需要同步
				AdAstraMachineSupport.sync(be);
			}
		}
		player.getInventory().markDirty();
		// 放入音效只播放一次，避免连锁时刷屏
		finish(player, world, () -> {
		});
		return ActionResult.SUCCESS;
	}

	/** 连锁放入的可接收槽位（权重=槽位剩余容量） */
	private record DepositSlot(BlockEntity be, Inventory inv, int slot, boolean pot, int weight) {
	}

	/** 槽位剩余可放容量（权重）。空槽=手持堆叠上限；同类=剩余空间；其它=0 */
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

	/** 厨锅槽位剩余可放容量（反射simulate，按手持物品堆叠上限测真实空间） */
	private static int cookingPotSlotFree(World world, BlockEntity be, int slot, ItemStack held) {
		ItemStack probe = held.copy();
		probe.setCount(held.getMaxCount());
		return FarmersDelightSupport.insertToSlot(world, be.getPos(), be, slot, probe, true);
	}

	/** 放入单个Inventory槽位（空槽/同类合并），返回实际放入数量 */
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
			// 显式写回：部分模组容器的 getStack 返回副本，直接 increment 不会生效
			inv.setStack(slot, existing);
			held.decrement(canMove);
			return canMove;
		}
		return 0;
	}

	/** 放入单个厨锅槽位（反射），返回实际放入数量 */
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

	/** 把放不进的物品退还到玩家背包主背包（这些物品刚从中取出，一定有空间） */
	private static void giveBackToPlayer(PlayerEntity player, int count, ItemStack template) {
		if (count <= 0 || template == null || template.isEmpty()) {
			return;
		}
		ItemStack refund = template.copy();
		refund.setCount(count);
		player.getInventory().offer(refund, false);
	}

	/**
	 * 判断容器指定槽位中是否有至少一个能接收手持物品（空槽过isValid，或同类堆叠有剩余容量）。
	 */
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

	/**
	 * 把手持物品放入厨锅的指定槽位（反射insertItem，自动同类堆叠合并）。
	 * @param maxTake 本次最多放入的数量（连锁均分时限制单容器份额）
	 * @return 是否放入了至少一个物品
	 */
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

	/**
	 * 厨锅在放入模式下的槽位：仅食材输入槽（没有燃料槽，返回null）。
	 */
	private static int[] cookingPotDepositSlots(ExtractionMode mode) {
		if (mode == ExtractionMode.INPUT) {
			return new int[] { 0, 1, 2, 3, 4, 5 };
		}
		return null;
	}

	/**
	 * Jade放入预览：判断手持物品是否至少能放进一个目标槽位。
	 * 会逐槽检查容量与过滤，全部放不下时不显示预览。
	 */
	public static boolean canDepositTo(BlockEntity blockEntity, ExtractionMode mode, ItemStack held) {
		if (blockEntity == null || held == null || held.isEmpty()) {
			return false;
		}
		// 厨锅
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
		// 烤炉走反射路径拿内部容器（不是Inventory）
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return false;
			}
			int[] ovenDepositSlots = getDepositSlots(blockEntity, mode);
			Inventory ovenInventory = CookingForBlockheadsSupport.getInternalInventory(blockEntity);
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

	/**
	 * 返回该容器补货模式下的槽位：输入槽+燃料槽的并集（按放入模式的槽位语义推导，去重）。
	 * 厨锅返回食材槽（0-5，走反射路径）。不适用返回null。
	 */
	public static int[] getRestockSlots(BlockEntity blockEntity) {
		// 厨锅：食材槽0-5
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

	/**
	 * 补货分支：从背包向容器输入槽和燃料槽补充已有同类物品（空槽不补）。
	 */
	private static ActionResult handleRestock(PlayerEntity player, World world, BlockPos pos, BlockEntity blockEntity) {
		if (!isContainerEnabled("restock")) {
			return ActionResult.PASS;
		}
		// ---------- FTB Ultimine连锁补货 ----------
		// 修饰键+空手再按住Ultimine键时，对连锁形状内所有容器逐个补货（背包耗尽即止）
		List<BlockPos> chain = getChainPositions(player, pos);
		if (chain != null) {
			return handleChainRestock(player, world, chain);
		}

		ActionResult result = restockContainer(player, world, blockEntity, 1);
		if (result == ActionResult.SUCCESS) {
			player.getInventory().markDirty();
			finish(player, world, () -> {
			});
		}
		return result;
	}

	/**
	 * 对单个容器补货（连锁与单容器共用）。
	 * @param containersLeft 参与均分的容器数（单容器传1；连锁时背包存货按容器数均分）
	 */
	private static ActionResult restockContainer(PlayerEntity player, World world, BlockEntity blockEntity, int containersLeft) {
		int[] slots = getRestockSlots(blockEntity);
		if (slots == null) {
			return ActionResult.PASS;
		}
		// Farmer's Delight厨锅走反射路径
		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			if (!isContainerEnabled("cooking_pot")) {
				return ActionResult.PASS;
			}
			if (!restockToCookingPot(player, world, blockEntity, slots, containersLeft)) {
				return ActionResult.PASS;
			}
			blockEntity.markDirty();
			return ActionResult.SUCCESS;
		}
		// 烤炉走反射路径拿内部容器（不是Inventory）
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return ActionResult.PASS;
			}
			Inventory ovenInventory = CookingForBlockheadsSupport.getInternalInventory(blockEntity);
			if (ovenInventory == null || !restockFromInventory(player, world, ovenInventory, slots, containersLeft)) {
				return ActionResult.PASS;
			}
			ovenInventory.markDirty();
			return ActionResult.SUCCESS;
		}
		if (!(blockEntity instanceof Inventory inventory) || !restockFromInventory(player, world, inventory, slots, containersLeft)) {
			return ActionResult.PASS;
		}
		inventory.markDirty();
		if (AdAstraMachineSupport.isAdAstraMachine(blockEntity)) {
			// Ad Astra机器在物品变化后需要同步
			AdAstraMachineSupport.sync(blockEntity);
		}
		return ActionResult.SUCCESS;
	}

	/**
	 * 连锁补货：把背包存货按物品类型分组，组内按权重（槽位剩余容量）分摊到连锁形状内所有补货槽。
	 * 剩余越多的槽位补得越多，尽量补满；空槽不补；背包存货耗尽即止。
	 */
	private static ActionResult handleChainRestock(PlayerEntity player, World world, List<BlockPos> chain) {
		// 第一遍：收集所有可补货的槽位（非空且未满、背包有同类存货）
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
			return ActionResult.PASS;
		}

		// 按物品类型分组，组内按权重（剩余容量）分配背包存货
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
				share[k] = (int) ((long) stock * group.get(k).need() / totalNeed);
				allocated += share[k];
			}
			// 余数补足给需求最大且未填满的槽位
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
			// 应用份额
			for (int k = 0; k < group.size(); k++) {
				if (share[k] <= 0) {
					continue;
				}
				RestockSlot g = group.get(k);
				if (g.pot()) {
					// 厨锅：先模拟确认可放量，再扣背包并放入，避免丢物品
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
						// 槽位状态异常（理论上不会），退还
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
			return ActionResult.PASS;
		}
		for (BlockEntity be : dirty) {
			be.markDirty();
			if (AdAstraMachineSupport.isAdAstraMachine(be)) {
				// Ad Astra机器在物品变化后需要同步
				AdAstraMachineSupport.sync(be);
			}
		}
		player.getInventory().markDirty();
		finish(player, world, () -> {
		});
		return ActionResult.SUCCESS;
	}

	/** 连锁补货的可补货槽位（need=剩余容量） */
	private record RestockSlot(BlockEntity be, Inventory inv, int slot, boolean pot, ItemStack template, int need) {
	}

	/** 收集单个容器的可补货槽位，加入slots并登记dirty */
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
		// 烤炉走反射路径拿内部容器（不是Inventory）
		if (CookingForBlockheadsSupport.isOven(be)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return;
			}
			Inventory inv = CookingForBlockheadsSupport.getInternalInventory(be);
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

	/** 补货槽位的剩余容量；空槽/已满返回0 */
	private static int restockNeed(ItemStack existing) {
		if (existing == null || existing.isEmpty()) {
			return 0;
		}
		return Math.max(0, existing.getMaxCount() - existing.getCount());
	}

	/**
	 * 背包主背包中与template同类的物品总数。
	 */
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

	/**
	 * 从玩家主背包取出need个与template同类的物品。
	 */
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

	/**
	 * 补货：从背包向容器指定槽位补充已有同类物品到槽位上限，空槽不补。
	 * @param containersLeft 参与均分的容器数（每个槽位最多取背包剩余存货的1/N）
	 * @return 是否补充了至少一个物品
	 */
	private static boolean restockFromInventory(PlayerEntity player, World world, Inventory inventory, int[] slots, int containersLeft) {
		boolean movedAny = false;
		for (int slot : slots) {
			// 越界保护：模组更新可能改变槽位布局
			if (slot < 0 || slot >= inventory.size()) {
				continue;
			}
			ItemStack existing = inventory.getStack(slot);
			if (existing.isEmpty()) {
				// 空槽不补
				continue;
			}
			int need = existing.getMaxCount() - existing.getCount();
			if (need <= 0) {
				// 已满
				continue;
			}
			int stock = countInInventory(player, existing);
			if (stock <= 0) {
				// 背包没有同类存货
				continue;
			}
			// 均分：最多拿背包剩余存货的1/N（N=1即全部）
			int share = Math.max(1, stock / containersLeft);
			int taken = takeFromPlayerInventory(player, existing, Math.min(need, share));
			if (taken <= 0) {
				continue;
			}
			existing.increment(taken);
			// 显式写回：部分模组容器的 getStack 返回副本，直接 increment 不会生效
			inventory.setStack(slot, existing);
			movedAny = true;
		}
		return movedAny;
	}

	/**
	 * 补货：从背包向厨锅食材槽补充已有同类物品（反射路径，空槽不补）。
	 * @param containersLeft 参与均分的容器数
	 */
	private static boolean restockToCookingPot(PlayerEntity player, World world, BlockEntity blockEntity, int[] slots, int containersLeft) {
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
			int share = Math.max(1, stock / containersLeft);
			// 先模拟放入确认实际可放数量，避免先扣背包后放不进而丢物品
			ItemStack probe = existing.copy();
			probe.setCount(Math.min(need, share));
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

	/**
	 * Jade补货预览：列出可补充的已有物品（同类去重）。
	 * 按住Ultimine键（连锁生效）时汇总整个连锁形状的可补物品，否则只看当前容器。
	 */
	public static List<ItemStack> collectRestockPreview(BlockEntity blockEntity, PlayerEntity player) {
		List<ItemStack> items = new ArrayList<>();
		// 连锁补货：汇总整个连锁形状
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

	/** 单个容器的补货预览收集 */
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
		// 烤炉走反射路径拿内部容器（不是Inventory）
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return;
			}
			Inventory ovenInventory = CookingForBlockheadsSupport.getInternalInventory(blockEntity);
			int[] ovenRestockSlots = getRestockSlots(blockEntity);
			if (ovenInventory == null || ovenRestockSlots == null) {
				return;
			}
			for (int slot : ovenRestockSlots) {
				if (slot < 0 || slot >= ovenInventory.size()) {
					continue;
				}
				addRestockPreviewItem(items, ovenInventory.getStack(slot), player);
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

	/** 补货预览项：槽位非空、未满、背包有同类存货、同类尚未展示 */
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

	/** 玩家主背包是否存有与template同类的物品 */
	private static boolean hasStockInInventory(PlayerEntity player, ItemStack template) {
		DefaultedList<ItemStack> main = player.getInventory().main;
		for (int i = 0; i < main.size(); i++) {
			if (isSameItem(main.get(i), template)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 返回该容器在放入模式下可放入的槽位：
	 * 只给出合理的槽位——输出槽不收玩家物品；没有燃料槽的容器拒收燃料预设。
	 * 槽位最终还会经过 Inventory.isValid 校验。
	 */
	public static int[] getDepositSlots(BlockEntity blockEntity, ExtractionMode mode) {
		if (mode != ExtractionMode.INPUT && mode != ExtractionMode.FUEL) {
			return null;
		}
		// ---------- 原版 ----------
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
			// 3 材料槽，4 燃料槽（烈焰粉）
			return isContainerEnabled("brewing_stand") ? (mode == ExtractionMode.INPUT ? new int[] { 3 } : new int[] { 4 }) : null;
		}
		// 没有槽位语义的容器，input/fuel 预设都放入全部槽位
		if (blockEntity instanceof DropperBlockEntity) {
			return isContainerEnabled("dropper") ? allSlots(blockEntity) : null;
		}
		if (blockEntity instanceof DispenserBlockEntity) {
			return isContainerEnabled("dispenser") ? allSlots(blockEntity) : null;
		}
		if (blockEntity instanceof HopperBlockEntity) {
			return isContainerEnabled("hopper") ? allSlots(blockEntity) : null;
		}
		// Farmer's Delight木篮竹篮
		if (FarmersDelightSupport.isBasket(blockEntity)) {
			return isContainerEnabled("basket") ? allSlots(blockEntity) : null;
		}
		// Farmer's Delight厨锅：非Inventory，放入走 FarmersDelightSupport 反射路径（见cookingPotDepositSlots）
		if (FarmersDelightSupport.isCookingPot(blockEntity)) {
			return null;
		}

		// ---------- Ad Astra ----------
		if (CompressorSupport.isCompressor(blockEntity)) {
			// 0电 1输入 2输出：无燃料槽
			return isContainerEnabled("compressor") && mode == ExtractionMode.INPUT ? new int[] { 1 } : null;
		}
		if (EtrionicBlastFurnaceSupport.isEtrionicBlastFurnace(blockEntity)) {
			// 0电 1-4输入 5-8输出：无燃料槽
			return isContainerEnabled("etrionic_blast_furnace") && mode == ExtractionMode.INPUT ? new int[] { 1, 2, 3, 4 } : null;
		}
		if (FuelRefinerySupport.isFuelRefinery(blockEntity)) {
			// 0电 1输入(原油) 2输出(空桶) 3流体输入 4输出(满桶)：无燃料槽
			return isContainerEnabled("fuel_refinery") && mode == ExtractionMode.INPUT ? new int[] { 1, 3 } : null;
		}
		if (OxygenLoaderSupport.isOxygenLoader(blockEntity)) {
			// 0电池 1输入(水桶) 2输出(空桶) 3流体输入 4输出：无燃料槽
			return isContainerEnabled("oxygen_loader") && mode == ExtractionMode.INPUT ? new int[] { 1, 3 } : null;
		}
		if (CryoFreezerSupport.isCryoFreezer(blockEntity)) {
			// 0电 1输入 2流体输入 3输出：无燃料槽
			return isContainerEnabled("cryo_freezer") && mode == ExtractionMode.INPUT ? new int[] { 1, 2 } : null;
		}

		// ---------- Crabber's Delight ----------
		if (CrabTrapSupport.isCrabTrap(blockEntity)) {
			// 0诱饵 1-9捕获物：诱饵槽
			return isContainerEnabled("crab_trap") && mode == ExtractionMode.INPUT ? new int[] { 0 } : null;
		}

		// ---------- The Aether ----------
		if (FreezerSupport.isFreezer(blockEntity)) {
			return isContainerEnabled("freezer") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}
		if (AltarSupport.isAltar(blockEntity)) {
			return isContainerEnabled("altar") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}

		// ---------- Vinery ----------
		if (FermentationBarrelSupport.isFermentationBarrel(blockEntity)) {
			// 0葡萄汁 1-3食材 4酒瓶 5输出：酒瓶槽视作消耗品槽
			return isContainerEnabled("fermentation_barrel") ? (mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2, 3 } : new int[] { 4 }) : null;
		}
		if (ApplePressSupport.isApplePress(blockEntity)) {
			// 0压榨输入 1中间产物 2酒瓶输入 3输出：酒瓶输入视作消耗品槽
			return isContainerEnabled("apple_press") ? (mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2 } : new int[] { 2 }) : null;
		}

		// ---------- Fossils and Archeology: Revival ----------
		if (AnalyzerSupport.isAnalyzer(blockEntity)) {
			// 0-8输入 9-12输出：无燃料槽
			return isContainerEnabled("analyzer") && mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 } : null;
		}
		if (SifterSupport.isSifter(blockEntity)) {
			// 0输入 1-5输出：无燃料槽
			return isContainerEnabled("sifter") && mode == ExtractionMode.INPUT ? new int[] { 0 } : null;
		}
		if (CultureVatSupport.isCultureVat(blockEntity)) {
			return isContainerEnabled("culture_vat") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}
		if (WorktableSupport.isWorktable(blockEntity)) {
			return isContainerEnabled("worktable") ? (mode == ExtractionMode.INPUT ? new int[] { 0 } : new int[] { 1 }) : null;
		}

		// ---------- Cooking for Blockheads ----------
		// 烤炉：0-2输入 3燃料（走内部容器反射路径，isValid自动过滤非熔炼物/非燃料）
		if (CookingForBlockheadsSupport.isOven(blockEntity)) {
			if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
				return null;
			}
			return mode == ExtractionMode.INPUT ? CookingForBlockheadsSupport.getInputSlots() : CookingForBlockheadsSupport.getFuelSlots();
		}
		return null;
	}

	/**
	 * 处理农夫乐事厨锅。
	 * 厨锅槽位：0-5食材输入，6成品显示，7容器槽，8成品输出。
	 * 通过反射访问getInventory()返回的ItemStackHandler。
	 */
	private static ActionResult handleCookingPot(PlayerEntity player, World world, BlockEntity blockEntity, ExtractionMode mode) {
		if (!takeFromCookingPot(player, world, blockEntity, cookingPotSlots(mode))) {
			return ActionResult.PASS;
		}
		finish(player, world, blockEntity::markDirty);
		return ActionResult.SUCCESS;
	}

	/**
	 * 厨锅在指定模式下的槽位。
	 */
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
		// 燃料模式：容器槽
		return new int[] { 7 };
	}

	/**
	 * 烤炉在指定取出模式下的槽位（内部容器20格的全局索引）。
	 * 0-2输入 3燃料 4-6输出；ALL=输入+燃料+输出（不含加工格7-15与工具格16-19）。
	 */
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

	/**
	 * 从厨锅指定槽位取出物品到玩家背包（反射读写，逐槽处理）。
	 * @return 是否取出了物品
	 */
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
		if (!takeSlots(player, inventory, slots)) {
			return ActionResult.PASS;
		}
		finish(player, world, inventory::markDirty);
		return ActionResult.SUCCESS;
	}

	/**
	 * 从指定槽位取出物品到玩家背包。
	 * @return 是否取出了物品
	 */
	private static boolean takeSlots(PlayerEntity player, Inventory inventory, int[] slots) {
		boolean takenAny = false;
		for (int slot : slots) {
			// 越界保护：模组更新可能改变槽位布局，getStack越界抛出的异常会把玩家踢出服务器
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

	private static void finish(PlayerEntity player, World world, Runnable markDirty) {
		markDirty.run();
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, 1.0f);
	}
}