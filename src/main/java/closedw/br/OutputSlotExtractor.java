package closedw.br;

import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionMode;
import closedw.br.behavior.InteractionModeManager;
import closedw.br.config.BetterRemovalConfig;
import closedw.br.container.Stacks;
import closedw.br.container.access.MenuSlotAccess;
import closedw.br.container.detect.SlotDetection;
import closedw.br.container.detect.SlotProbeReport;
import closedw.br.container.support.ContainerAccess;
import closedw.br.container.support.ContainerRegistry;
import closedw.br.container.support.ContainerSupport;
import closedw.br.ftbultimine.FTBUltimineSupport;
import closedw.br.mapping.MappingSessionManager;
import closedw.br.networking.ExtractKeyStateManager;
import closedw.br.slot.SlotSet;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 左Alt（可改键）+ 右键容器直接交互，不用打开容器的 GUI：
 * 空手=按当前槽位模式取出物品；手持物品=按当前槽位模式放入物品（放入预设）；
 * 补货预设=空手右击，从背包向容器输入槽和燃料槽补充已有同类物品（空槽不补）。
 * 模式通过模式滚轮（按住模式键+滚轮）切换。
 * 容器类型统一由 {@link ContainerRegistry} 描述，容器可通过Configured的配置菜单开关。
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
     * 查询字符串列表配置（声明式槽位规则）。未安装Configured时返回空列表。
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
     * 修饰键是否按住：左Alt（可改键）。
     */
    public static boolean isModifierHeld(Player player) {
        return ExtractKeyStateManager.isAltKeyDown(player);
    }

    // 容器槽位查询统一走 ContainerRegistry.find(...)，不再提供冗余的包装方法

    // FTB Ultimine 连锁
    /**
     * FTB Ultimine连锁（取出/放入共用）的容器位置列表。
     * 条件：配置开启 + 按住Ultimine键 + Ultimine存在缓存的连锁形状。
     * 点击的容器保证在列表中；数量受ftb_ultimine_max_containers限制。
     * 返回null表示不适用连锁。
     */
    public static List<BlockPos> getChainPositions(Player player, BlockPos clicked) {
        if (!isContainerEnabled("ftb_ultimine") || !FTBUltimineSupport.isKeyHeld(player)) {
            return null;
        }
        java.util.Collection<BlockPos> shape = FTBUltimineSupport.getShapePositions(player);
        if (shape == null || shape.isEmpty()) {
            return null;
        }
        int max = getConfigInt("ftb_ultimine_max_containers", 64);
        if (max <= 0) {
            return null;
        }
        // 点击的容器优先入列（并且占用一个名额），避免形状遍历顺序把它挤出 max 之外
        Set<BlockPos> unique = new LinkedHashSet<>();
        unique.add(clicked);
        for (BlockPos pos : shape) {
            if (unique.size() >= max) {
                break;
            }
            unique.add(pos);
        }
        return new ArrayList<>(unique);
    }

    /**
     * 连锁取出：遍历Ultimine连锁形状内的所有容器，逐个按当前模式取出。
     * 不适用的容器（未支持/被关闭）自动跳过。
     */
    private static boolean handleChainExtraction(Player player, Level level, List<BlockPos> chain, SlotSet mode) {
        boolean any = false;
        for (BlockPos pos : chain) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                continue;
            }
            ContainerSupport support = ContainerRegistry.find(blockEntity);
            if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
                continue;
            }
            int[] slots = support.extractSlots(blockEntity, mode);
            ContainerAccess access = support.access(blockEntity, player);
            if (slots == null || access == null) {
                continue;
            }
            // 规则可以要求额外校验「该槽位能否被取出」
            boolean sided = SlotDetection.ruleRequiresSidedCheck(blockEntity);
            if (takeSlots(player, access, slots, blockEntity instanceof Container container ? container : null, sided)) {
                access.markDirty();
                any = true;
            }
        }
        if (!any) {
            return false;
        }
        // 取物音效只播放一次，避免连锁时刷屏（各容器的 markDirty 已在循环里逐个处理）
        playPickupSound(player, level);
        return true;
    }

    /**
     * Jade预览：计算将要取出的物品。
     * 空手+按住修饰键+按住Ultimine键时聚合整个连锁形状内所有支持容器；否则单容器。
     */
    public static List<ItemStack> collectPreview(Player player, BlockEntity blockEntity, SlotSet mode) {
        boolean emptyHands = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
        Level level = blockEntity.getLevel();
        // 空手 + 按住修饰键且连锁生效时，聚合整个连锁形状内所有支持容器的物品
        if (emptyHands && isModifierHeld(player) && level != null) {
            List<BlockPos> chain = getChainPositions(player, blockEntity.getBlockPos());
            if (chain != null) {
                List<ItemStack> items = new ArrayList<>();
                for (BlockPos pos : chain) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be == null) {
                        continue;
                    }
                    List<ItemStack> part = ExtractionPreviewItems.collect(player, be, mode);
                    if (part != null && !part.isEmpty()) {
                        items.addAll(part);
                    }
                    // 限制聚合大小，避免向客户端下发过大数据
                    if (items.size() >= 128) {
                        break;
                    }
                }
                return items;
            }
        }
        return ExtractionPreviewItems.collect(player, blockEntity, mode);
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

        try {
            if (handleInteraction(player, event.getLevel(), event.getPos())) {
                succeed(event);
            }
        }
        finally {
            // 菜单槽位（手动指认映射）会临时构造菜单：操作结束统一关闭，别漏掉 removed() 钩子
            MenuSlotAccess.closeAll(player);
        }
    }

    /**
     * 一次右键交互的实际逻辑。
     * @return true 表示本次由本模组处理完，需要取消原版交互
     */
    private static boolean handleInteraction(Player player, Level level, BlockPos pos) {
        InteractionMode state = InteractionModeManager.getState(player);

        // ---------- 主动探测（实验性）：槽位探测的结果打印到聊天框，不动容器 ----------
        if (state.behavior() == Behavior.PROBE) {
            if (!InteractionModeManager.isProbeAvailable()) {
                // 开关关掉后玩家可能还停在"主动探测"模式
                return false;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return false;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                return false;
            }
            SlotProbeReport.send(serverPlayer, blockEntity);
            return true;
        }

        // ---------- 手动指认：不取消交互，让原版照常打开 GUI，玩家在界面里标槽位 ----------
        if (state.behavior() == Behavior.MAPPING) {
            if (!isContainerEnabled("menu_mapping")) {
                return false;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                // 记下目标容器（并提前验证它的菜单能不能在没有 GUI 的情况下重建）
                MappingSessionManager.begin(serverPlayer, pos);
            }
            return false;
        }

        boolean emptyHands = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();

        // ---------- 取出（空手）/ 补货（空手）----------
        if (emptyHands) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                return false;
            }
            // 补货：从背包向容器输入/燃料槽补充已有同类物品（空槽不补）
            if (state.behavior() == Behavior.RESTOCK) {
                return handleRestock(player, level, pos, blockEntity);
            }
            // 放入预设下空手右击不动作
            if (state.behavior() != Behavior.EXTRACT) {
                return false;
            }
            return handleExtract(player, level, pos, blockEntity, state.slots());
        }

        // ---------- 放入（手持物品）----------
        if (state.behavior() != Behavior.DEPOSIT) {
            return false;
        }
        if (!isContainerEnabled("deposit")) {
            return false;
        }
        // 放入预设只有 input/fuel
        if (state.slots() != SlotSet.INPUT && state.slots() != SlotSet.FUEL) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }

        // FTB Ultimine连锁：一排容器一起放入
        List<BlockPos> chain = getChainPositions(player, pos);
        if (chain != null) {
            if (handleChainDeposit(player, level, chain, state.slots(), held)) {
                return true;
            }
            // 连锁没有放入任何物品时回退到单个容器处理
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }
        ContainerSupport support = ContainerRegistry.find(blockEntity);
        if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
            return false;
        }
        int[] slots = support.depositSlots(blockEntity, state.slots());
        ContainerAccess access = support.access(blockEntity, player);
        if (slots == null || access == null) {
            return false;
        }
        if (!depositToContainer(access, slots, held)) {
            return false;
        }
        access.markDirty();
        player.getInventory().setChanged();
        playPickupSound(player, level);
        return true;
    }

    /**
     * 取出分支：连锁 → 单容器（统一走注册表）。
     */
    private static boolean handleExtract(Player player, Level level, BlockPos pos, BlockEntity blockEntity, SlotSet mode) {
        // ---------- FTB Ultimine连锁取出 ----------
        // 修饰键+空手再按住Ultimine键时，一次性取出整个连锁形状内所有支持容器的对应槽位
        List<BlockPos> chain = getChainPositions(player, pos);
        if (chain != null) {
            return handleChainExtraction(player, level, chain, mode);
        }

        ContainerSupport support = ContainerRegistry.find(blockEntity);
        if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
            return false;
        }
        int[] slots = support.extractSlots(blockEntity, mode);
        ContainerAccess access = support.access(blockEntity, player);
        if (slots == null || access == null) {
            return false;
        }
        // 规则可以要求额外校验「该槽位能否被取出」
        boolean sided = SlotDetection.ruleRequiresSidedCheck(blockEntity);
        if (!takeSlots(player, access, slots, blockEntity instanceof Container container ? container : null, sided)) {
            return false;
        }
        access.markDirty();
        playPickupSound(player, level);
        return true;
    }

    /**
     * 判断两个堆叠是否为同种物品。
     */
    private static boolean isSameItem(ItemStack a, ItemStack b) {
        return Stacks.isSameItem(a, b);
    }

    /**
     * 把主手物品放入容器的指定槽位（同类堆叠优先合并，空槽其次）。
     * 每个槽位都会先经过 canPlaceItem 校验。
     * @return 是否放入了至少一个物品
     */
    private static boolean depositToContainer(ContainerAccess access, int[] slots, ItemStack held) {
        boolean movedAny = false;
        for (int slot : slots) {
            if (held.isEmpty()) {
                break;
            }
            if (depositToContainerSlot(access, slot, held, Integer.MAX_VALUE) > 0) {
                movedAny = true;
            }
        }
        return movedAny;
    }

    /**
     * 放入单个槽位，返回实际放入数量。
     * @param maxTake 本次最多放入的数量
     */
    private static int depositToContainerSlot(ContainerAccess access, int slot, ItemStack held, int maxTake) {
        if (held.isEmpty() || maxTake <= 0) {
            return 0;
        }
        ItemStack portion = held.copy();
        portion.setCount(Math.min(held.getCount(), maxTake));
        int put = access.insert(slot, portion, false);
        if (put > 0) {
            held.shrink(put);
        }
        return put;
    }

    /**
     * 连锁放入：把主手物品按权重分摊到Ultimine连锁形状内的所有可接收槽位。
     * 权重=槽位剩余容量（空槽按整组计、同类槽按剩余空间计）
     * 放不下的余量留在手上
     */
    private static boolean handleChainDeposit(Player player, Level level, List<BlockPos> chain, SlotSet slotMode, ItemStack held) {
        // 第一遍：收集所有能接收手持物品的槽位及其权重
        List<DepositSlot> slots = new ArrayList<>();
        Set<ContainerAccess> dirty = new LinkedHashSet<>();
        for (BlockPos pos : chain) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                continue;
            }
            ContainerSupport support = ContainerRegistry.find(blockEntity);
            if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
                continue;
            }
            int[] depositSlots = support.depositSlots(blockEntity, slotMode);
            ContainerAccess access = support.access(blockEntity, player);
            if (depositSlots == null || access == null) {
                continue;
            }
            for (int slot : depositSlots) {
                int weight = slotFreeCapacity(access, slot, held);
                if (weight > 0) {
                    slots.add(new DepositSlot(access, slot, weight));
                    dirty.add(access);
                }
            }
        }
        if (slots.isEmpty()) {
            return false;
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
            if (depositToContainerSlot(s.access(), s.slot(), held, share[i]) > 0) {
                any = true;
            }
        }
        if (!any) {
            return false;
        }
        for (ContainerAccess access : dirty) {
            access.markDirty();
        }
        player.getInventory().setChanged();
        // 放入音效只播放一次，避免连锁时刷屏
        playPickupSound(player, level);
        return true;
    }

    /** 连锁放入的可接收槽位（权重=槽位剩余容量） */
    private record DepositSlot(ContainerAccess access, int slot, int weight) {
    }

    /** 槽位剩余可放容量（权重）。空槽=手持堆叠上限；同类=剩余空间；其它=0 */
    private static int slotFreeCapacity(ContainerAccess access, int slot, ItemStack held) {
        ItemStack probe = held.copy();
        probe.setCount(held.getMaxStackSize());
        return access.insert(slot, probe, true);
    }

    /** 把放不进的物品退还到玩家背包主背包（这些物品刚从中取出，一定有空间） */
    private static void giveBackToPlayer(Player player, int count, ItemStack template) {
        if (count <= 0 || template == null || template.isEmpty()) {
            return;
        }
        ItemStack refund = template.copy();
        refund.setCount(count);
        insertIntoPlayerInventory(player, refund);
    }

    /**
     * Jade放入预览：判断手持物品是否至少能放进一个目标槽位。
     * 会逐槽检查容量与过滤，全部放不下时不显示预览。
     * 和前两处预览一样必须带上玩家：手动指认的容器要按这个玩家构造临时菜单。
     */
    public static boolean canDepositTo(Player player, BlockEntity blockEntity, SlotSet mode, ItemStack held) {
        if (blockEntity == null || held == null || held.isEmpty()) {
            return false;
        }
        ContainerSupport support = ContainerRegistry.find(blockEntity);
        if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
            return false;
        }
        int[] slots = support.depositSlots(blockEntity, mode);
        if (slots == null) {
            return false;
        }
        try {
            ContainerAccess access = support.access(blockEntity, player);
            if (access == null) {
                return false;
            }
            for (int slot : slots) {
                if (access.insert(slot, held, true) > 0) {
                    return true;
                }
            }
            return false;
        }
        finally {
            MenuSlotAccess.closeAll(player);
        }
    }

    // 补货
    private static boolean handleRestock(Player player, Level level, BlockPos pos, BlockEntity blockEntity) {
        if (!isContainerEnabled("restock")) {
            return false;
        }
        // ---------- FTB Ultimine连锁补货 ----------
        // 修饰键+空手再按住Ultimine键时，对连锁形状内所有容器逐个补货（背包耗尽即止）
        List<BlockPos> chain = getChainPositions(player, pos);
        if (chain != null) {
            return handleChainRestock(player, level, chain);
        }

        ContainerSupport support = ContainerRegistry.find(blockEntity);
        if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
            return false;
        }
        int[] slots = support.restockSlots(blockEntity);
        ContainerAccess access = support.access(blockEntity, player);
        if (slots == null || access == null) {
            return false;
        }
        if (!restockAccess(player, access, slots)) {
            return false;
        }
        access.markDirty();
        player.getInventory().setChanged();
        playPickupSound(player, level);
        return true;
    }

    /**
     * 补货：从背包向容器指定槽位补充已有同类物品到槽位上限，空槽不补。
     * @return 是否补充了至少一个物品
     */
    private static boolean restockAccess(Player player, ContainerAccess access, int[] slots) {
        boolean movedAny = false;
        for (int slot : slots) {
            ItemStack existing = access.get(slot);
            if (existing.isEmpty()) {
                // 空槽不补
                continue;
            }
            int need = existing.getMaxStackSize() - existing.getCount();
            if (need <= 0) {
                // 已满
                continue;
            }
            int stock = countInInventory(player, existing);
            if (stock <= 0) {
                // 背包没有同类存货
                continue;
            }
            // 单容器补货：需要量与背包存货取小者
            int want = Math.min(need, stock);
            // 先模拟放入确认实际可放数量，避免先扣背包后放不进而丢物品
            ItemStack probe = existing.copy();
            probe.setCount(want);
            int canInsert = access.insert(slot, probe, true);
            if (canInsert <= 0) {
                continue;
            }
            int taken = takeFromPlayerInventory(player, existing, canInsert);
            if (taken <= 0) {
                continue;
            }
            ItemStack portion = existing.copy();
            portion.setCount(taken);
            int put = access.insert(slot, portion, false);
            if (put < taken) {
                giveBackToPlayer(player, taken - put, existing);
            }
            movedAny = true;
        }
        return movedAny;
    }

    /**
     * 连锁补货：把背包存货按物品类型分组，组内按权重（槽位剩余容量）分摊到连锁形状内所有补货槽。
     * 剩余越多的槽位补得越多，尽量补满；空槽不补；背包存货耗尽即止。
     */
    private static boolean handleChainRestock(Player player, Level level, List<BlockPos> chain) {
        // 第一遍：收集所有可补货的槽位（非空且未满、背包有同类存货）
        List<RestockSlot> slots = new ArrayList<>();
        Set<ContainerAccess> dirty = new LinkedHashSet<>();
        for (BlockPos pos : chain) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                continue;
            }
            ContainerSupport support = ContainerRegistry.find(blockEntity);
            if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
                continue;
            }
            int[] restockSlots = support.restockSlots(blockEntity);
            ContainerAccess access = support.access(blockEntity, player);
            if (restockSlots == null || access == null) {
                continue;
            }
            collectRestockSlots(player, access, restockSlots, slots, dirty);
        }
        if (slots.isEmpty()) {
            return false;
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
                // 每个槽位最多补到剩余容量（need），避免背包存货多于总需求时超量取出被容器上限截断而吞物品
                share[k] = (int) Math.min(group.get(k).need(), (long) stock * group.get(k).need() / totalNeed);
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
            // 应用份额：先模拟确认可放量，再扣背包并放入，避免丢物品
            for (int k = 0; k < group.size(); k++) {
                if (share[k] <= 0) {
                    continue;
                }
                RestockSlot g = group.get(k);
                ItemStack probe = g.template().copy();
                probe.setCount(share[k]);
                int canInsert = g.access().insert(g.slot(), probe, true);
                if (canInsert <= 0) {
                    continue;
                }
                int taken = takeFromPlayerInventory(player, g.template(), canInsert);
                if (taken <= 0) {
                    continue;
                }
                ItemStack portion = g.template().copy();
                portion.setCount(taken);
                int put = g.access().insert(g.slot(), portion, false);
                if (put < taken) {
                    giveBackToPlayer(player, taken - put, g.template());
                }
                any = true;
            }
        }
        if (!any) {
            return false;
        }
        for (ContainerAccess access : dirty) {
            access.markDirty();
        }
        player.getInventory().setChanged();
        playPickupSound(player, level);
        return true;
    }

    /** 连锁补货的可补货槽位（need=剩余容量） */
    private record RestockSlot(ContainerAccess access, int slot, ItemStack template, int need) {
    }

    /** 收集单个容器的可补货槽位，加入slots并登记dirty */
    private static void collectRestockSlots(Player player, ContainerAccess access, int[] restockSlots, List<RestockSlot> slots, Set<ContainerAccess> dirty) {
        for (int slot : restockSlots) {
            ItemStack existing = access.get(slot);
            int need = restockNeed(existing);
            if (need > 0 && countInInventory(player, existing) > 0) {
                slots.add(new RestockSlot(access, slot, existing.copy(), need));
                dirty.add(access);
            }
        }
    }

    /** 补货槽位的剩余容量；空槽/已满返回0 */
    private static int restockNeed(ItemStack existing) {
        if (existing == null || existing.isEmpty()) {
            return 0;
        }
        return Math.max(0, existing.getMaxStackSize() - existing.getCount());
    }

    /**
     * 背包主背包中与template同类的物品总数。
     */
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

    /**
     * 从玩家主背包取出need个与template同类的物品。
     */
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

    // Jade 补货预览
    /**
     * Jade补货预览：列出将从玩家背包补入容器的物品及数量（同类去重）。
     * 数量 = min(同类槽位剩余容量总和, 背包同类存货)，与补货实际移动数一致。
     * 按住Ultimine键（连锁生效）时汇总整个连锁形状，否则只看当前容器。
     */
    public static List<ItemStack> collectRestockPreview(BlockEntity blockEntity, Player player) {
        if (player == null) {
            return List.of();
        }
        // 收集整个连锁形状（或单个容器）内所有可补货槽位
        List<RestockCandidate> candidates = new ArrayList<>();
        try {
            List<BlockPos> chain = getChainPositions(player, blockEntity.getBlockPos());
            if (chain != null && blockEntity.getLevel() != null) {
                for (BlockPos pos : chain) {
                    BlockEntity be = blockEntity.getLevel().getBlockEntity(pos);
                    if (be != null) {
                        collectRestockCandidates(player, be, candidates);
                    }
                }
            }
            else {
                collectRestockCandidates(player, blockEntity, candidates);
            }
        }
        finally {
            // 菜单槽位容器会在收集过程中临时构造菜单（只关这个玩家的）
            MenuSlotAccess.closeAll(player);
        }

        // 按物品类型分组，累加槽位需求量，补货量 = min(总需求量, 背包同类存货)
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            RestockCandidate first = candidates.get(i);
            if (first == null) {
                continue;
            }
            candidates.set(i, null);
            long totalNeed = first.need();
            for (int j = i + 1; j < candidates.size(); j++) {
                RestockCandidate other = candidates.get(j);
                if (other != null && isSameItem(first.template(), other.template())) {
                    totalNeed += other.need();
                    candidates.set(j, null);
                }
            }
            int stock = countInInventory(player, first.template());
            if (stock <= 0) {
                continue;
            }
            int toAdd = (int) Math.min(totalNeed, stock);
            if (toAdd <= 0) {
                continue;
            }
            ItemStack display = first.template().copy();
            display.setCount(toAdd);
            items.add(display);
        }
        return items;
    }

    /** 补货预览的可补货槽位 */
    private record RestockCandidate(ItemStack template, int need) {
    }

    /** 收集单个容器的可补货槽位（槽位非空且未满），加入candidates */
    private static void collectRestockCandidates(Player player, BlockEntity blockEntity, List<RestockCandidate> candidates) {
        ContainerSupport support = ContainerRegistry.find(blockEntity);
        if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
            return;
        }
        int[] slots = support.restockSlots(blockEntity);
        ContainerAccess access = support.access(blockEntity, player);
        if (slots == null || access == null) {
            return;
        }
        for (int slot : slots) {
            ItemStack existing = access.get(slot);
            int need = restockNeed(existing);
            if (need > 0) {
                candidates.add(new RestockCandidate(existing.copy(), need));
            }
        }
    }

    // 取物
    /**
     * 从指定槽位取出物品到玩家背包。
     * @param container 该容器对应的原版容器视图；用于 sided 校验，可为 null
     * @param sided 是否额外经过「该槽位能否被取出」的校验（规则里的 sided 字段）
     * @return 是否取出了物品
     */
    private static boolean takeSlots(Player player, ContainerAccess access, int[] slots, Container container, boolean sided) {
        boolean takenAny = false;
        for (int slot : slots) {
            ItemStack result = access.get(slot);
            if (result.isEmpty()) {
                continue;
            }
            if (sided && !SlotDetection.canExtract(container, slot, result)) {
                // 容器明确声明这个槽位不允许被取出（升级槽/展示槽之类）
                continue;
            }
            // 单次最多取到物品堆叠上限，防止容器中存在超过堆叠上限的物品（Vinery 苹果压榨器的输出槽）
            // 被原样塞进玩家背包
            int want = Math.min(result.getCount(), result.getMaxStackSize());
            // 先模拟背包容量：取出来的物品不会再放回容器（输出槽通常不允许放回），所以放不下就一点都别取
            int room = insertIntoPlayerInventory(player, result, true);
            if (room <= 0) {
                continue;
            }
            ItemStack portion = access.take(slot, Math.min(want, room), player);
            if (portion.isEmpty()) {
                continue;
            }
            if (insertIntoPlayerInventory(player, portion) <= 0) {
                continue;
            }
            takenAny = true;
        }
        return takenAny;
    }

    /**
     * 把 stack 放入玩家主背包（优先与同类堆叠合并，其次放入空槽），返回实际放入数量。
     * 只按真实容量放置，绝不多取，放不下的部分留在传入的堆叠里由调用方按实际数量处理。
     */
    private static int insertIntoPlayerInventory(Player player, ItemStack stack) {
        return insertIntoPlayerInventory(player, stack, false);
    }

    /**
     * @param simulate true 时只计算能放入多少、不改动背包（取物前先探容量用）
     */
    private static int insertIntoPlayerInventory(Player player, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0;
        }
        NonNullList<ItemStack> main = player.getInventory().items;
        int inventoryMax = player.getInventory().getMaxStackSize();
        int remaining = stack.getCount();
        // 1) 先与背包含同类物品的堆叠合并
        for (int i = 0; i < main.size() && remaining > 0; i++) {
            ItemStack existing = main.get(i);
            if (existing.isEmpty() || !isSameItem(existing, stack)) {
                continue;
            }
            int room = Math.min(existing.getMaxStackSize(), inventoryMax) - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int move = Math.min(room, remaining);
            if (!simulate) {
                existing.grow(move);
            }
            remaining -= move;
        }
        // 2) 再放入空槽
        for (int i = 0; i < main.size() && remaining > 0; i++) {
            if (!main.get(i).isEmpty()) {
                continue;
            }
            int move = Math.min(Math.min(stack.getMaxStackSize(), inventoryMax), remaining);
            if (!simulate) {
                ItemStack copy = stack.copy();
                copy.setCount(move);
                main.set(i, copy);
            }
            remaining -= move;
        }
        int inserted = stack.getCount() - remaining;
        if (!simulate && inserted > 0) {
            player.getInventory().setChanged();
        }
        return inserted;
    }

    private static void playPickupSound(Player player, Level level) {
        level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.0f);
    }

    private static void succeed(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
