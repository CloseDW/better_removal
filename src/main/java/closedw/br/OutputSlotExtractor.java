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
import closedw.br.crabbersdelight.CrabTrapSupport;
import closedw.br.farmersdelight.FarmersDelightSupport;
import closedw.br.fossil.AnalyzerSupport;
import closedw.br.fossil.CultureVatSupport;
import closedw.br.fossil.SifterSupport;
import closedw.br.fossil.WorktableSupport;
import closedw.br.vinery.ApplePressSupport;
import closedw.br.vinery.FermentationBarrelSupport;
import net.minecraft.core.BlockPos;
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

/**
 * 潜行+空手右键容器可以直接取出物品（按当前取出模式）同时不用打开容器的 GUI。
 * 模式通过 /br 指令或按键切换（output/input/fuel/all）。
 * 容器可通过Configured的配置菜单开关。
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
     * 返回指定方块实体在当前模式下要取出的槽位。
     * 公有：服务端取物与 Jade 客户端预览共用。
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
        // 主手和副手都必须为空
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return;
        }
        // 仅潜行；同时安装Carry On时改用左Alt键（避免与Carry On的Shift+右键搬起冲突）
        if (isCarryOnLoaded() ? !CarryOnCompat.isAltKeyDown(player) : !player.isShiftKeyDown()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ExtractionMode mode = ExtractionModeManager.getMode(player);

        // Farmer's Delight厨锅
        if (blockEntity != null && FarmersDelightSupport.isCookingPot(blockEntity)) {
            if (!isContainerEnabled("cooking_pot")) {
                return;
            }
            if (handleCookingPot(player, level, blockEntity, mode)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        int[] slots = getSlotsForMode(blockEntity, mode);
        if (slots == null) {
            return;
        }
        if (!(blockEntity instanceof Container container)) {
            return;
        }

        boolean handled = takeFromContainer(player, level, container, slots);
        if (handled && AdAstraMachineSupport.isAdAstraMachine(blockEntity)) {
            // Ad Astra机器在玩家取走物品后需要同步
            AdAstraMachineSupport.sync(blockEntity);
        }
        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * 处理农夫乐事厨锅。
     * 厨锅槽位：0-5食材输入，6成品显示，7容器槽，8成品输出。
     * 通过反射访问getInventory()返回的ItemStackHandler。
     */
    private static boolean handleCookingPot(Player player, Level level, BlockEntity blockEntity, ExtractionMode mode) {
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
            slots = new int[] { 7 };
        }

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

        if (!takenAny) {
            return false;
        }
        finish(player, level, blockEntity::setChanged);
        return true;
    }

    /**
     * 把物品放入玩家背包
     */
    private static int tryInsertToPlayer(Player player, ItemStack stack) {
        int original = stack.getCount();
        ItemStack toInsert = stack.copy();
        player.getInventory().add(toInsert);
        return original - toInsert.getCount();
    }

    private static boolean takeFromContainer(Player player, Level level, Container container, int[] slots) {
        boolean takenAny = false;
        for (int slot : slots) {
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

        if (!takenAny) {
            return false;
        }

        finish(player, level, container::setChanged);
        return true;
    }

    /**
     * 使用 Inventory.add
     * 额外限制：单次最多取出到物品堆叠上限，防止容器中存在超过堆叠上限的物品
     * （如 Vinery 苹果压榨器的输出槽 BUG）被原样塞进玩家背包。
     */
    private static int tryTakeSlot(Player player, ItemStack result) {
        int maxCount = result.getMaxStackSize();
        int takeCount = Math.min(result.getCount(), maxCount);
        ItemStack toInsert = result.copy();
        toInsert.setCount(takeCount);
        player.getInventory().add(toInsert);
        return takeCount - toInsert.getCount();
    }

    private static boolean isCarryOnLoaded() {
        return ModList.get().isLoaded("carryon");
    }

    private static void finish(Player player, Level level, Runnable markDirty) {
        markDirty.run();
        level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.0f);
    }
}