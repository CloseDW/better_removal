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
import closedw.br.experimental.AutoDetectSupport;
import closedw.br.experimental.SlotProbeReport;
import closedw.br.farmandcharm.FarmAndCharmSupport;
import closedw.br.farmersdelight.FarmersDelightSupport;
import closedw.br.fossil.AnalyzerSupport;
import closedw.br.fossil.CultureVatSupport;
import closedw.br.fossil.SifterSupport;
import closedw.br.fossil.WorktableSupport;
import closedw.br.ftbultimine.FTBUltimineSupport;
import closedw.br.vinery.ApplePressSupport;
import closedw.br.vinery.FermentationBarrelSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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
@EventBusSubscriber(modid = BetterRemoval.MODID)
public final class OutputSlotExtractor {

    private OutputSlotExtractor() {
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
     * 实验性功能总开关（Configured 中的 experimental_auto_detect，默认关闭）。
     * 未安装 Configured 时按“关闭”处理。
     */
    public static boolean isExperimentalEnabled() {
        try {
            return BetterRemovalConfig.get().isEnabled("experimental_auto_detect");
        }
        catch (LinkageError e) {
            return false;
        }
    }

    /**
     * 查询字符串列表配置（实验性自动探测白名单 / 手写槽位规则）。未安装Configured时返回空列表。
     */
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
    public static boolean isModifierHeld(Player player) {
        return CarryOnCompat.isAltKeyDown(player);
    }

    /**
     * 返回指定方块实体在当前模式下要取出的槽位。
     * 公有：服务端取物与 Jade 客户端预览共用。
     */
    public static int[] getSlotsForMode(BlockEntity blockEntity, ExtractionMode mode) {
        // ---------- Cooking for Blockheads ----------
        // 烤炉不是Container（反射拿内部20格容器），且ALL模式需要在通用ALL拦截之前处理
        if (CookingForBlockheadsSupport.isOven(blockEntity)
                && isContainerEnabled("oven")
                && !CookingForBlockheadsSupport.isAutomationDisallowed()) {
            return ovenSlots(mode);
        }

        if (mode == ExtractionMode.ALL && getConfigKey(blockEntity) != null) {
            // ALL受容器开关约束，不允许绕过配置（未知容器不在此拦截，交给下面的实验性分支）
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

        // ---------- Farm & Charm ----------
        String farmAndCharmKey = FarmAndCharmSupport.getConfigKey(blockEntity);
        if (farmAndCharmKey != null && isContainerEnabled(farmAndCharmKey)) {
            return mode == ExtractionMode.OUTPUT ? FarmAndCharmSupport.getOutputSlots(blockEntity)
                    : mode == ExtractionMode.INPUT ? FarmAndCharmSupport.getInputSlots(blockEntity)
                    : FarmAndCharmSupport.getFuelSlots(blockEntity);
        }

        // ---------- 实验性：通用容器支持（总开关默认关闭）----------
        // 优先级1：手写槽位规则（显式声明，精确，不需要白名单）
        if (getConfigKey(blockEntity) == null && AutoDetectSupport.hasRule(blockEntity)) {
            return AutoDetectSupport.getRuleSlotsForMode(blockEntity, mode);
        }

        // 优先级2：自动探测（需要白名单命中）
        if (getConfigKey(blockEntity) == null && AutoDetectSupport.isEnabledFor(blockEntity)) {
            int[] auto = AutoDetectSupport.getSlotsForMode(blockEntity, mode);
            if (auto != null) {
                return auto;
            }
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
        // ---------- Farm & Charm ----------
        String farmAndCharmKey = FarmAndCharmSupport.getConfigKey(blockEntity);
        if (farmAndCharmKey != null) {
            return farmAndCharmKey;
        }
        return null;
    }

    private static int[] allSlots(BlockEntity blockEntity) {
        if (blockEntity instanceof Container container) {
            int size = container.getContainerSize();
            int[] slots = new int[size];
            for (int i = 0; i < size; i++) {
                slots[i] = i;
            }
            return slots;
        }
        return null;
    }

    /**
     * FTB Ultimine连锁（取出/放入/补货共用）的容器位置列表。
     */
    public static List<BlockPos> getChainPositions(Player player, BlockPos clicked) {
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
     */
    private static boolean handleChainExtraction(Player player, Level level, List<BlockPos> chain, ExtractionMode mode) {
        boolean any = false;
        for (BlockPos pos : chain) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                continue;
            }
            // 农夫乐事厨锅走反射路径
            if (FarmersDelightSupport.isCookingPot(be)) {
                if (!isContainerEnabled("cooking_pot")) {
                    continue;
                }
                if (takeFromCookingPot(player, level, be, cookingPotSlots(mode))) {
                    be.setChanged();
                    any = true;
                }
                continue;
            }
            // 烤炉走反射路径拿内部容器（不是Container）
            if (CookingForBlockheadsSupport.isOven(be)) {
                if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
                    continue;
                }
                Container ovenContainer = CookingForBlockheadsSupport.getInternalContainer(be);
                if (ovenContainer != null && takeSlots(player, ovenContainer, ovenSlots(mode))) {
                    ovenContainer.setChanged();
                    any = true;
                }
                continue;
            }
            int[] slots = getSlotsForMode(be, mode);
            if (slots == null || !(be instanceof Container container)) {
                continue;
            }
            if (takeSlots(player, container, slots)) {
                container.setChanged();
                if (AdAstraMachineSupport.isAdAstraMachine(be)) {
                    AdAstraMachineSupport.sync(be);
                }
                any = true;
            }
        }
        if (!any) {
            return false;
        }
        finish(player, level, () -> {
        });
        return true;
    }

    /**
     * Jade预览：计算将要取出的物品。
     */
    public static List<ItemStack> collectPreview(Player player, BlockEntity blockEntity, ExtractionMode mode) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty() || !isModifierHeld(player)) {
            return ExtractionPreviewItems.collect(blockEntity, mode);
        }
        Level level = blockEntity.getLevel();
        if (level == null) {
            return ExtractionPreviewItems.collect(blockEntity, mode);
        }
        List<BlockPos> chain = getChainPositions(player, blockEntity.getBlockPos());
        if (chain == null) {
            return ExtractionPreviewItems.collect(blockEntity, mode);
        }
        List<ItemStack> items = new ArrayList<>();
        for (BlockPos pos : chain) {
            BlockEntity be = level.getBlockEntity(pos);
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

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 只在服务端执行逻辑
        if (event.getLevel().isClientSide) {
            return;
        }
        Player player = event.getEntity();
        // 只处理主手
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        // 修饰键：左Alt（可改键）
        if (!isModifierHeld(player)) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ModeState state = ExtractionModeManager.getState(player);

        // ---------- 主动探测（实验性）：槽位探测的结果打印到聊天框，不动容器 ----------
        if (state.action() == ExtractionAction.PROBE) {
            if (!ExtractionModeManager.isProbeAvailable()) {
                return;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            BlockEntity probeTarget = level.getBlockEntity(pos);
            if (probeTarget == null) {
                return;
            }
            SlotProbeReport.send(serverPlayer, probeTarget);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        boolean emptyHands = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();

        // ---------- 取出（空手）/ 补货（空手）----------
        if (emptyHands) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                return;
            }
            // 补货：从背包向容器输入/燃料槽补充已有同类物品（空槽不补）
            if (state.action() == ExtractionAction.RESTOCK) {
                if (handleRestock(player, level, pos, blockEntity)) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
                return;
            }
            // 放入预设下空手右击不动作
            if (state.action() != ExtractionAction.EXTRACT) {
                return;
            }
            if (handleExtract(player, level, pos, blockEntity, state.mode())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        // ---------- 放入（手持物品）----------
        if (state.action() != ExtractionAction.DEPOSIT) {
            return;
        }
        if (!isContainerEnabled("deposit")) {
            return;
        }
        // 放入预设只有 input/fuel
        if (state.mode() != ExtractionMode.INPUT && state.mode() != ExtractionMode.FUEL) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }

        // FTB Ultimine连锁：一排容器一起放入
        List<BlockPos> chain = getChainPositions(player, pos);
        if (chain != null) {
            boolean chainMoved = handleChainDeposit(player, level, chain, state.mode(), held);
            if (chainMoved) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            // 连锁没有放入任何物品时回退到单个容器处理
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        // Farmer's Delight厨锅走反射路径不是Container
        if (FarmersDelightSupport.isCookingPot(blockEntity)) {
            if (!isContainerEnabled("cooking_pot")) {
                return;
            }
            int[] potSlots = cookingPotDepositSlots(state.mode());
            if (potSlots == null) {
                return;
            }
            if (!depositToCookingPot(player, level, blockEntity, potSlots, held, Integer.MAX_VALUE)) {
                return;
            }
            finish(player, level, () -> {
                blockEntity.setChanged();
                player.getInventory().setChanged();
            });
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // 烤炉走反射路径拿内部容器（不是Container），canPlaceItem过滤
        if (CookingForBlockheadsSupport.isOven(blockEntity)) {
            if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
                return;
            }
            int[] ovenDepositSlots = getDepositSlots(blockEntity, state.mode());
            Container ovenContainer = CookingForBlockheadsSupport.getInternalContainer(blockEntity);
            if (ovenDepositSlots == null || ovenContainer == null) {
                return;
            }
            if (!depositToInventory(player, level, ovenContainer, ovenDepositSlots, held, Integer.MAX_VALUE)) {
                return;
            }
            finish(player, level, () -> {
                ovenContainer.setChanged();
                player.getInventory().setChanged();
            });
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        int[] slots = getDepositSlots(blockEntity, state.mode());
        if (slots == null || !(blockEntity instanceof Container inventory)) {
            return;
        }
        if (!depositToInventory(player, level, inventory, slots, held, Integer.MAX_VALUE)) {
            return;
        }
        finish(player, level, () -> {
            inventory.setChanged();
            player.getInventory().setChanged();
        });
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * 取出分支：连锁 → 厨锅/烤炉 → 普通容器。
     */
    private static boolean handleExtract(Player player, Level level, BlockPos pos, BlockEntity blockEntity, ExtractionMode mode) {
        List<BlockPos> chain = getChainPositions(player, pos);
        if (chain != null) {
            return handleChainExtraction(player, level, chain, mode);
        }

        // Farmer's Delight厨锅
        if (FarmersDelightSupport.isCookingPot(blockEntity)) {
            if (!isContainerEnabled("cooking_pot")) {
                return false;
            }
            return handleCookingPot(player, level, blockEntity, mode);
        }

        // 烤炉走反射路径拿内部容器（不是Container）
        if (CookingForBlockheadsSupport.isOven(blockEntity)) {
            if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
                return false;
            }
            Container ovenContainer = CookingForBlockheadsSupport.getInternalContainer(blockEntity);
            if (ovenContainer == null) {
                return false;
            }
            return takeFromContainer(player, level, ovenContainer, ovenSlots(mode));
        }

        int[] slots = getSlotsForMode(blockEntity, mode);
        if (slots == null || !(blockEntity instanceof Container container)) {
            return false;
        }

        boolean result = takeFromContainer(player, level, container, slots);
        if (result && AdAstraMachineSupport.isAdAstraMachine(blockEntity)) {
            AdAstraMachineSupport.sync(blockEntity);
        }
        return result;
    }

    /**
     * 判断两个堆叠是否为同种物品（含数据组件）。
     */
    private static boolean isSameItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(a, b);
    }

    private static boolean depositToInventory(Player player, Level level, Container inventory, int[] slots, ItemStack held, int maxTake) {
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

    private static boolean handleChainDeposit(Player player, Level level, List<BlockPos> chain, ExtractionMode slotMode, ItemStack held) {
        List<DepositSlot> slots = new ArrayList<>();
        Set<BlockEntity> dirty = new HashSet<>();
        for (BlockPos pos : chain) {
            BlockEntity be = level.getBlockEntity(pos);
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
                    int weight = cookingPotSlotFree(level, be, slot, held);
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
                Container ovenContainer = CookingForBlockheadsSupport.getInternalContainer(be);
                int[] ovenDepositSlots = getDepositSlots(be, slotMode);
                if (ovenContainer == null || ovenDepositSlots == null) {
                    continue;
                }
                for (int slot : ovenDepositSlots) {
                    int weight = slotFreeCapacity(ovenContainer, slot, held);
                    if (weight > 0) {
                        slots.add(new DepositSlot(be, ovenContainer, slot, false, weight));
                        dirty.add(be);
                    }
                }
                continue;
            }
            int[] depositSlots = getDepositSlots(be, slotMode);
            if (depositSlots == null || !(be instanceof Container inventory)) {
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
                    ? depositToCookingPotSlot(player, level, s.be(), s.slot(), held, share[i])
                    : depositToInventorySlot(player, s.inv(), s.slot(), held, share[i]);
            if (actual > 0) {
                any = true;
            }
        }
        if (!any) {
            return false;
        }
        for (BlockEntity be : dirty) {
            be.setChanged();
            if (AdAstraMachineSupport.isAdAstraMachine(be)) {
                AdAstraMachineSupport.sync(be);
            }
        }
        player.getInventory().setChanged();
        finish(player, level, () -> {
        });
        return true;
    }

    private record DepositSlot(BlockEntity be, Container inv, int slot, boolean pot, int weight) {
    }

    private static int slotFreeCapacity(Container inv, int slot, ItemStack held) {
        if (slot < 0 || slot >= inv.getContainerSize()) {
            return 0;
        }
        ItemStack existing = inv.getItem(slot);
        if (existing.isEmpty()) {
            return inv.canPlaceItem(slot, held) ? held.getMaxStackSize() : 0;
        }
        if (isSameItem(existing, held)) {
            return Math.max(0, existing.getMaxStackSize() - existing.getCount());
        }
        return 0;
    }

    private static int cookingPotSlotFree(Level level, BlockEntity be, int slot, ItemStack held) {
        ItemStack probe = held.copy();
        probe.setCount(held.getMaxStackSize());
        return FarmersDelightSupport.insertToSlot(level, be.getBlockPos(), be, slot, probe, true);
    }

    private static int depositToInventorySlot(Player player, Container inv, int slot, ItemStack held, int maxTake) {
        if (slot < 0 || slot >= inv.getContainerSize() || held.isEmpty() || maxTake <= 0) {
            return 0;
        }
        ItemStack existing = inv.getItem(slot);
        if (existing.isEmpty()) {
            if (!inv.canPlaceItem(slot, held)) {
                return 0;
            }
            int put = Math.min(Math.min(held.getCount(), held.getMaxStackSize()), maxTake);
            if (put <= 0) {
                return 0;
            }
            ItemStack copy = held.copy();
            copy.setCount(put);
            inv.setItem(slot, copy);
            held.shrink(put);
            return put;
        }
        if (isSameItem(existing, held)) {
            int canMove = Math.min(Math.min(held.getCount(), existing.getMaxStackSize() - existing.getCount()), maxTake);
            if (canMove <= 0) {
                return 0;
            }
            existing.grow(canMove);
            inv.setItem(slot, existing);
            held.shrink(canMove);
            return canMove;
        }
        return 0;
    }

    private static int depositToCookingPotSlot(Player player, Level level, BlockEntity be, int slot, ItemStack held, int maxTake) {
        if (held.isEmpty() || maxTake <= 0) {
            return 0;
        }
        ItemStack portion = held.copy();
        portion.setCount(Math.min(held.getCount(), maxTake));
        int put = FarmersDelightSupport.insertToSlot(level, be.getBlockPos(), be, slot, portion, false);
        if (put > 0) {
            held.shrink(put);
        }
        return put;
    }

    private static void giveBackToPlayer(Player player, int count, ItemStack template) {
        if (count <= 0 || template == null || template.isEmpty()) {
            return;
        }
        ItemStack refund = template.copy();
        refund.setCount(count);
        player.getInventory().add(refund);
    }

    private static boolean canAcceptAny(Container inventory, int[] slots, ItemStack held) {
        for (int slot : slots) {
            if (slot < 0 || slot >= inventory.getContainerSize()) {
                continue;
            }
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                if (inventory.canPlaceItem(slot, held)) {
                    return true;
                }
            }
            else if (isSameItem(existing, held) && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private static boolean depositToCookingPot(Player player, Level level, BlockEntity blockEntity, int[] slots, ItemStack held, int maxTake) {
        boolean movedAny = false;
        int taken = 0;
        for (int slot : slots) {
            if (held.isEmpty() || taken >= maxTake) {
                break;
            }
            int put = depositToCookingPotSlot(player, level, blockEntity, slot, held, maxTake - taken);
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
            if (potSlots == null || blockEntity.getLevel() == null) {
                return false;
            }
            for (int slot : potSlots) {
                if (FarmersDelightSupport.insertToSlot(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity, slot, held, true) > 0) {
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
            Container ovenContainer = CookingForBlockheadsSupport.getInternalContainer(blockEntity);
            if (ovenDepositSlots == null || ovenContainer == null) {
                return false;
            }
            return canAcceptAny(ovenContainer, ovenDepositSlots, held);
        }
        int[] slots = getDepositSlots(blockEntity, mode);
        if (slots == null || !(blockEntity instanceof Container inventory)) {
            return false;
        }
        for (int slot : slots) {
            if (slot < 0 || slot >= inventory.getContainerSize()) {
                continue;
            }
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                if (inventory.canPlaceItem(slot, held)) {
                    return true;
                }
            }
            else if (isSameItem(existing, held) && existing.getCount() < existing.getMaxStackSize()) {
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

    public static List<ItemStack> collectRestockPreview(BlockEntity blockEntity, Player player) {
        List<ItemStack> items = new ArrayList<>();
        List<BlockPos> chain = player == null ? null : getChainPositions(player, blockEntity.getBlockPos());
        if (chain != null && blockEntity.getLevel() != null) {
            for (BlockPos pos : chain) {
                BlockEntity be = blockEntity.getLevel().getBlockEntity(pos);
                if (be != null) {
                    collectRestockPreviewSingle(items, be, player);
                }
            }
            return items;
        }
        collectRestockPreviewSingle(items, blockEntity, player);
        return items;
    }

    private static void collectRestockPreviewSingle(List<ItemStack> items, BlockEntity blockEntity, Player player) {
        if (FarmersDelightSupport.isCookingPot(blockEntity)) {
            if (!isContainerEnabled("cooking_pot") || blockEntity.getLevel() == null) {
                return;
            }
            for (int slot : cookingPotDepositSlots(ExtractionMode.INPUT)) {
                addRestockPreviewItem(items, FarmersDelightSupport.getSlot(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity, slot), player);
            }
            return;
        }
        int[] slots = getRestockSlots(blockEntity);
        if (slots == null || !(blockEntity instanceof Container container)) {
            return;
        }
        for (int slot : slots) {
            if (slot < 0 || slot >= container.getContainerSize()) {
                continue;
            }
            addRestockPreviewItem(items, container.getItem(slot), player);
        }
    }

    private static void addRestockPreviewItem(List<ItemStack> items, ItemStack existing, Player player) {
        if (existing == null || existing.isEmpty() || existing.getCount() >= existing.getMaxStackSize()) {
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

    private static boolean hasStockInInventory(Player player, ItemStack template) {
        NonNullList<ItemStack> main = player.getInventory().items;
        for (int i = 0; i < main.size(); i++) {
            if (isSameItem(main.get(i), template)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleRestock(Player player, Level level, BlockPos pos, BlockEntity blockEntity) {
        if (!isContainerEnabled("restock")) {
            return false;
        }
        List<BlockPos> chain = getChainPositions(player, pos);
        if (chain != null) {
            return handleChainRestock(player, level, chain);
        }

        boolean result = restockContainer(player, level, blockEntity);
        if (result) {
            player.getInventory().setChanged();
            finish(player, level, () -> {
            });
        }
        return result;
    }

    private static boolean restockContainer(Player player, Level level, BlockEntity blockEntity) {
        int[] slots = getRestockSlots(blockEntity);
        if (slots == null) {
            return false;
        }
        if (FarmersDelightSupport.isCookingPot(blockEntity)) {
            if (!isContainerEnabled("cooking_pot")) {
                return false;
            }
            if (!restockToCookingPot(player, level, blockEntity, slots)) {
                return false;
            }
            blockEntity.setChanged();
            return true;
        }
        if (CookingForBlockheadsSupport.isOven(blockEntity)) {
            if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
                return false;
            }
            Container ovenContainer = CookingForBlockheadsSupport.getInternalContainer(blockEntity);
            if (ovenContainer == null || !restockFromInventory(player, level, ovenContainer, slots)) {
                return false;
            }
            ovenContainer.setChanged();
            return true;
        }
        if (!(blockEntity instanceof Container inventory) || !restockFromInventory(player, level, inventory, slots)) {
            return false;
        }
        inventory.setChanged();
        if (AdAstraMachineSupport.isAdAstraMachine(blockEntity)) {
            AdAstraMachineSupport.sync(blockEntity);
        }
        return true;
    }

    private static boolean handleChainRestock(Player player, Level level, List<BlockPos> chain) {
        List<RestockSlot> slots = new ArrayList<>();
        Set<BlockEntity> dirty = new HashSet<>();
        for (BlockPos pos : chain) {
            BlockEntity be = level.getBlockEntity(pos);
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
                    int canInsert = FarmersDelightSupport.insertToSlot(level, g.be().getBlockPos(), g.be(), g.slot(), probe, true);
                    if (canInsert <= 0) {
                        continue;
                    }
                    int taken = takeFromPlayerInventory(player, g.template(), canInsert);
                    if (taken <= 0) {
                        continue;
                    }
                    ItemStack portion = g.template().copy();
                    portion.setCount(taken);
                    int put = FarmersDelightSupport.insertToSlot(level, g.be().getBlockPos(), g.be(), g.slot(), portion, false);
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
                    ItemStack existing = g.inv().getItem(g.slot());
                    if (existing.isEmpty() || !isSameItem(existing, g.template())) {
                        giveBackToPlayer(player, taken, g.template());
                        continue;
                    }
                    existing.grow(taken);
                    g.inv().setItem(g.slot(), existing);
                    any = true;
                }
            }
        }
        if (!any) {
            return false;
        }
        for (BlockEntity be : dirty) {
            be.setChanged();
            if (AdAstraMachineSupport.isAdAstraMachine(be)) {
                AdAstraMachineSupport.sync(be);
            }
        }
        player.getInventory().setChanged();
        finish(player, level, () -> {
        });
        return true;
    }

    private record RestockSlot(BlockEntity be, Container inv, int slot, boolean pot, ItemStack template, int need) {
    }

    private static void collectRestockSlots(Player player, BlockEntity be, List<RestockSlot> slots, Set<BlockEntity> dirty) {
        int[] restockSlots = getRestockSlots(be);
        if (restockSlots == null) {
            return;
        }
        if (FarmersDelightSupport.isCookingPot(be)) {
            if (!isContainerEnabled("cooking_pot") || be.getLevel() == null) {
                return;
            }
            for (int slot : restockSlots) {
                ItemStack existing = FarmersDelightSupport.getSlot(be.getLevel(), be.getBlockPos(), be, slot);
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
            Container inv = CookingForBlockheadsSupport.getInternalContainer(be);
            if (inv == null) {
                return;
            }
            for (int slot : restockSlots) {
                if (slot < 0 || slot >= inv.getContainerSize()) {
                    continue;
                }
                ItemStack existing = inv.getItem(slot);
                int need = restockNeed(existing);
                if (need > 0 && countInInventory(player, existing) > 0) {
                    slots.add(new RestockSlot(be, inv, slot, false, existing.copy(), need));
                    dirty.add(be);
                }
            }
            return;
        }
        if (!(be instanceof Container inv)) {
            return;
        }
        for (int slot : restockSlots) {
            if (slot < 0 || slot >= inv.getContainerSize()) {
                continue;
            }
            ItemStack existing = inv.getItem(slot);
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
        return Math.max(0, existing.getMaxStackSize() - existing.getCount());
    }

    private static int countInInventory(Player player, ItemStack template) {
        int count = 0;
        NonNullList<ItemStack> main = player.getInventory().items;
        for (int i = 0; i < main.size(); i++) {
            ItemStack stack = main.get(i);
            if (!stack.isEmpty() && isSameItem(stack, template)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int takeFromPlayerInventory(Player player, ItemStack template, int need) {
        int taken = 0;
        NonNullList<ItemStack> main = player.getInventory().items;
        for (int i = 0; i < main.size() && taken < need; i++) {
            ItemStack stack = main.get(i);
            if (stack.isEmpty() || !isSameItem(stack, template)) {
                continue;
            }
            int take = Math.min(need - taken, stack.getCount());
            stack.shrink(take);
            taken += take;
        }
        return taken;
    }

    private static boolean restockFromInventory(Player player, Level level, Container inventory, int[] slots) {
        boolean movedAny = false;
        for (int slot : slots) {
            if (slot < 0 || slot >= inventory.getContainerSize()) {
                continue;
            }
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                continue;
            }
            int need = existing.getMaxStackSize() - existing.getCount();
            if (need <= 0) {
                continue;
            }
            int stock = countInInventory(player, existing);
            if (stock <= 0) {
                continue;
            }
            int taken = takeFromPlayerInventory(player, existing, need);
            if (taken <= 0) {
                continue;
            }
            existing.grow(taken);
            inventory.setItem(slot, existing);
            movedAny = true;
        }
        return movedAny;
    }

    private static boolean restockToCookingPot(Player player, Level level, BlockEntity blockEntity, int[] slots) {
        boolean movedAny = false;
        for (int slot : slots) {
            ItemStack existing = FarmersDelightSupport.getSlot(level, blockEntity.getBlockPos(), blockEntity, slot);
            if (existing == null || existing.isEmpty()) {
                continue;
            }
            int need = existing.getMaxStackSize() - existing.getCount();
            if (need <= 0) {
                continue;
            }
            int stock = countInInventory(player, existing);
            if (stock <= 0) {
                continue;
            }
            ItemStack probe = existing.copy();
            probe.setCount(need);
            int canInsert = FarmersDelightSupport.insertToSlot(level, blockEntity.getBlockPos(), blockEntity, slot, probe, true);
            if (canInsert <= 0) {
                continue;
            }
            int taken = takeFromPlayerInventory(player, existing, canInsert);
            if (taken <= 0) {
                continue;
            }
            ItemStack portion = existing.copy();
            portion.setCount(taken);
            FarmersDelightSupport.insertToSlot(level, blockEntity.getBlockPos(), blockEntity, slot, portion, false);
            movedAny = true;
        }
        return movedAny;
    }

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
        // Cooking for Blockheads烤炉：内部容器，0-2输入 3燃料
        if (CookingForBlockheadsSupport.isOven(blockEntity)) {
            if (!isContainerEnabled("oven") || CookingForBlockheadsSupport.isAutomationDisallowed()) {
                return null;
            }
            return mode == ExtractionMode.INPUT ? CookingForBlockheadsSupport.getInputSlots() : CookingForBlockheadsSupport.getFuelSlots();
        }

        // ---------- Ad Astra ----------
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

        // ---------- Crabber's Delight ----------
        if (CrabTrapSupport.isCrabTrap(blockEntity)) {
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
            return isContainerEnabled("fermentation_barrel") ? (mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2, 3 } : new int[] { 4 }) : null;
        }
        if (ApplePressSupport.isApplePress(blockEntity)) {
            return isContainerEnabled("apple_press") ? (mode == ExtractionMode.INPUT ? new int[] { 0, 1, 2 } : new int[] { 2 }) : null;
        }

        // ---------- Fossils and Archeology: Revival ----------
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

        // ---------- Farm & Charm ----------
        String farmAndCharmKey = FarmAndCharmSupport.getConfigKey(blockEntity);
        if (farmAndCharmKey != null) {
            return isContainerEnabled(farmAndCharmKey)
                    ? (mode == ExtractionMode.INPUT ? FarmAndCharmSupport.getInputSlots(blockEntity) : FarmAndCharmSupport.getFuelSlots(blockEntity))
                    : null;
        }

        // ---------- 实验性：通用容器支持（总开关默认关闭）----------
        if (getConfigKey(blockEntity) == null && AutoDetectSupport.hasRule(blockEntity)) {
            return AutoDetectSupport.getRuleSlotsForMode(blockEntity, mode);
        }
        if (getConfigKey(blockEntity) == null && AutoDetectSupport.isEnabledFor(blockEntity)) {
            return AutoDetectSupport.getDepositSlots(blockEntity, mode);
        }

        return null;
    }

    /**
     * 烤炉在指定取出模式下的槽位（内部容器20格的全局索引）。
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

    private static boolean handleCookingPot(Player player, Level level, BlockEntity blockEntity, ExtractionMode mode) {
        if (!takeFromCookingPot(player, level, blockEntity, cookingPotSlots(mode))) {
            return false;
        }
        finish(player, level, blockEntity::setChanged);
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

    private static boolean takeFromCookingPot(Player player, Level level, BlockEntity blockEntity, int[] slots) {
        boolean takenAny = false;
        for (int slot : slots) {
            ItemStack output = FarmersDelightSupport.getSlot(level, blockEntity.getBlockPos(), blockEntity, slot);
            if (output == null || output.isEmpty()) {
                continue;
            }
            int placed = tryInsertToPlayer(player, output);
            if (placed <= 0) {
                continue;
            }
            FarmersDelightSupport.removeFromSlot(level, blockEntity.getBlockPos(), blockEntity, slot, placed);
            takenAny = true;
        }
        return takenAny;
    }

    private static int tryInsertToPlayer(Player player, ItemStack stack) {
        int original = stack.getCount();
        ItemStack toInsert = stack.copy();
        player.getInventory().add(toInsert);
        return original - toInsert.getCount();
    }

    private static boolean takeFromContainer(Player player, Level level, Container container, int[] slots) {
        if (!takeSlots(player, container, slots)) {
            return false;
        }
        finish(player, level, container::setChanged);
        return true;
    }

    private static boolean takeSlots(Player player, Container container, int[] slots) {
        boolean takenAny = false;
        for (int slot : slots) {
            if (slot < 0 || slot >= container.getContainerSize()) {
                continue;
            }
            ItemStack result = container.getItem(slot);
            if (result.isEmpty()) {
                continue;
            }

            int taken = tryTakeSlot(player, result);
            if (taken <= 0) {
                continue;
            }

            result.shrink(taken);
            if (result.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            takenAny = true;
        }
        return takenAny;
    }

    /**
     * 单次最多取出到物品堆叠上限，防止容器中存在超过堆叠上限的物品被原样塞进玩家背包。
     */
    private static int tryTakeSlot(Player player, ItemStack result) {
        int maxCount = result.getMaxStackSize();
        int takeCount = Math.min(result.getCount(), maxCount);
        ItemStack toInsert = result.copy();
        toInsert.setCount(takeCount);
        player.getInventory().add(toInsert);
        return takeCount - toInsert.getCount();
    }

    private static void finish(Player player, Level level, Runnable markDirty) {
        markDirty.run();
        level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.0f);
    }
}
