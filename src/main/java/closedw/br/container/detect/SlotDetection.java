package closedw.br.container.detect;

import closedw.br.BetterRemoval;
import closedw.br.container.rule.RuleDirection;
import closedw.br.container.rule.SlotRule;
import closedw.br.container.rule.SlotRuleRegistry;
import closedw.br.container.rule.SlotRules;
import closedw.br.slot.SlotRole;
import closedw.br.slot.SlotSet;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 槽位判定：把"这个容器的每个槽位是什么角色"算出来。
 */
public final class SlotDetection {

    private static final Logger LOGGER = BetterRemoval.LOGGER;

    /** 用于控制日志频率：同一种容器只在结果变化时打印一次。 */
    private static final Map<String, String> LAST_LOGGED = new ConcurrentHashMap<>();

    private SlotDetection() {
    }

    // ---------- 声明式规则 ----------

    /** 该方块是否有声明式槽位规则。 */
    public static boolean hasRule(BlockEntity blockEntity) {
        if (blockEntity == null || !(blockEntity instanceof Container)) {
            return false;
        }
        return SlotRuleRegistry.hasRule(blockEntity);
    }

    /**
     * 按声明式规则取「取出」方向的槽位。规则就是最终结果，不会再退回任何猜测：
     * 返回 null 表示「这条规则在该模式下没有槽位」。
     */
    public static int[] getRuleSlotsForMode(BlockEntity blockEntity, SlotSet mode) {
        if (mode == null || !(blockEntity instanceof Container)) {
            return null;
        }
        SlotRule rule = SlotRuleRegistry.find(blockEntity);
        if (rule == null) {
            return null;
        }
        String mismatch = SlotRuleRegistry.sizeMismatch(blockEntity, rule);
        if (mismatch != null) {
            logSizeMismatch(blockEntity, rule, mismatch);
        }
        int[] slots;
        switch (mode) {
            case OUTPUT -> slots = ruleSlots(blockEntity, rule, Set.of(SlotRole.OUTPUT));
            case INPUT -> slots = ruleSlots(blockEntity, rule, Set.of(SlotRole.INPUT));
            case FUEL -> slots = ruleSlots(blockEntity, rule, Set.of(SlotRole.FUEL));
            // ALL = 输入 ∪ 燃料 ∪ 输出（忽略项与未声明项都不算）
            // 先各自求值再合并，不依赖方法实参的求值顺序
            case ALL -> {
                int[] inputs = ruleSlots(blockEntity, rule, Set.of(SlotRole.INPUT));
                int[] fuels = ruleSlots(blockEntity, rule, Set.of(SlotRole.FUEL));
                int[] outputs = ruleSlots(blockEntity, rule, Set.of(SlotRole.OUTPUT));
                slots = union(inputs, fuels, outputs);
            }
            default -> slots = new int[0];
        }
        logDetection(blockEntity, "rule:" + rule.match(), toRoles(blockEntity, rule));
        return slots.length == 0 ? null : slots;
    }

    /** 按声明式规则取「放入」方向的槽位；规格与 {@link #getRuleSlotsForMode} 一致。 */
    public static int[] getRuleDepositSlots(BlockEntity blockEntity, SlotSet mode) {
        if (mode != SlotSet.INPUT && mode != SlotSet.FUEL) {
            return null;
        }
        if (!(blockEntity instanceof Container)) {
            return null;
        }
        SlotRule rule = SlotRuleRegistry.find(blockEntity);
        if (rule == null) {
            return null;
        }
        Set<SlotRole> wanted = mode == SlotSet.INPUT ? Set.of(SlotRole.INPUT) : Set.of(SlotRole.FUEL);
        int[] slots = ruleSlots(blockEntity, rule, wanted);
        return slots.length == 0 ? null : slots;
    }

    /** 该规则是否要求在取出前额外做「能否取出」的校验（规则里的 {@code sided} 字段）。 */
    public static boolean ruleRequiresSidedCheck(BlockEntity blockEntity) {
        SlotRule rule = SlotRuleRegistry.find(blockEntity);
        return rule != null && rule.sided();
    }

    /**
     * 「这个槽位现在能否被取出」的通用判定。
     * 只有实现了 {@link WorldlyContainer} 的容器才声明得起这件事：{@code canTakeItemThroughFace} 返回 false
     * 就表示该槽位不允许被自动化取出（升级槽、展示槽之类）。没有 sided 信息的容器一律视为可取出。
     * @param container 容器的原版视图，可为 null（此时无法判定，按可取出处理）
     */
    public static boolean canExtract(Container container, int slot, ItemStack stack) {
        if (!(container instanceof WorldlyContainer sided)) {
            return true;
        }
        ItemStack probe = stack == null || stack.isEmpty() ? new ItemStack(Items.STONE) : stack;
        for (Direction side : Direction.values()) {
            try {
                if (sided.canTakeItemThroughFace(slot, probe, side)) {
                    return true;
                }
            }
            catch (Throwable ignored) {
                // 模组自定义判定抛异常时按可取出处理，宁可照旧也不要静默失效
                return true;
            }
        }
        return false;
    }

    private static int[] ruleSlots(BlockEntity blockEntity, SlotRule rule, Set<SlotRole> wanted) {
        return SlotRuleRegistry.slotsFor(blockEntity, rule, RuleDirection.EXTRACT, wanted);
    }

    /** 合并多个槽位号（有序去重）。 */
    private static int[] union(int[]... groups) {
        Set<Integer> merged = new LinkedHashSet<>();
        for (int[] group : groups) {
            for (int slot : group) {
                merged.add(slot);
            }
        }
        int[] result = new int[merged.size()];
        int cursor = 0;
        for (int slot : merged) {
            result[cursor++] = slot;
        }
        return result;
    }

    /** 规则声明的角色表；仅用于日志。 */
    private static SlotRole[] toRoles(BlockEntity blockEntity, SlotRule rule) {
        if (!(blockEntity instanceof Container container)) {
            return new SlotRole[0];
        }
        try {
            int size = container.getContainerSize();
            SlotRole[] extract = rule.extract().toRoles(size);
            SlotRole[] deposit = rule.deposit().toRoles(size);
            SlotRole[] merged = new SlotRole[size];
            for (int slot = 0; slot < size; slot++) {
                merged[slot] = extract[slot] != SlotRole.NONE ? extract[slot] : deposit[slot];
            }
            return merged;
        }
        catch (Throwable t) {
            return new SlotRole[0];
        }
    }

    private static void logSizeMismatch(BlockEntity blockEntity, SlotRule rule, String message) {
        ResourceLocation id = SlotRules.blockId(blockEntity);
        String key = (id != null ? id.toString() : blockEntity.getClass().getName()) + "|" + rule.match();
        if (message.equals(LAST_LOGGED.put(key, message))) {
            return;
        }
        LOGGER.warn("[container] rule for {} ({}): {}", key, rule.match(), message);
    }

    private static void logDetection(BlockEntity blockEntity, String source, SlotRole[] roles) {
        ResourceLocation id = SlotRules.blockId(blockEntity);
        String key = (id != null ? id.toString() : blockEntity.getClass().getName());
        StringBuilder signature = new StringBuilder(source).append(" |");
        for (int slot = 0; slot < roles.length; slot++) {
            signature.append(' ').append(slot).append('=').append(roles[slot].name());
        }
        String value = signature.toString();
        if (value.equals(LAST_LOGGED.put(key, value))) {
            return;
        }
        LOGGER.info("[container] {} -> {}", key, value);
    }

    // ---------- inventory接口启发式（只给主动探测用） ----------

    /**
     * 只用原版容器接口推断：接受通用探测物 → 输入/燃料；
     * 全部拒绝但能取出 → 输出；
     * 只认自己已有的特定物品 → 受限输入槽，忽略。
     */
    static SlotRole[] inventoryRoles(Container container) {
        int size;
        try {
            size = container.getContainerSize();
        }
        catch (Throwable t) {
            return new SlotRole[0];
        }
        SlotRole[] roles = new SlotRole[size];
        WorldlyContainer sided = container instanceof WorldlyContainer s ? s : null;
        for (int slot = 0; slot < size; slot++) {
            int index = slot;
            Predicate<ItemStack> accepts = stack -> canPlaceItem(container, index, stack);
            if (Probes.anyOf(accepts, Probes.GENERIC)) {
                roles[slot] = Probes.fuelOrInput(accepts);
                continue;
            }
            // 通用探测物全被拒，但接受自己已有的物品 => 只认特定物品的受限槽位（升级件/工具/模板）
            ItemStack existing = getStack(container, slot);
            if (!existing.isEmpty() && accepts.test(existing)) {
                roles[slot] = SlotRole.NONE;
                continue;
            }
            // 有 sided 信息时以 canTakeItemThroughFace 为准（可以排除只放不取的升级槽）；
            // 否则退化为“不能放入 => 视为输出”的推测。
            boolean extractable = sided != null ? canExtractAnySide(sided, slot) : true;
            roles[slot] = extractable ? SlotRole.OUTPUT : SlotRole.NONE;
        }
        return roles;
    }

    private static boolean canPlaceItem(Container container, int slot, ItemStack stack) {
        try {
            return container.canPlaceItem(slot, stack);
        }
        catch (Throwable ignored) {
            // 模组自定义过滤逻辑可能抛异常，视为不接受
            return false;
        }
    }

    private static ItemStack getStack(Container container, int slot) {
        try {
            ItemStack stack = container.getItem(slot);
            return stack == null ? ItemStack.EMPTY : stack;
        }
        catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean canExtractAnySide(WorldlyContainer container, int slot) {
        ItemStack probe = new ItemStack(Items.STONE);
        for (Direction side : Direction.values()) {
            try {
                if (container.canTakeItemThroughFace(slot, probe, side)) {
                    return true;
                }
            }
            catch (Throwable ignored) {
                // 同上
            }
        }
        return false;
    }
}
