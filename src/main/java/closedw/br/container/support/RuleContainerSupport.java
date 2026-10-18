package closedw.br.container.support;

import closedw.br.OutputSlotExtractor;
import closedw.br.container.access.InventoryAccess;
import closedw.br.container.detect.SlotDetection;
import closedw.br.container.rule.SlotRule;
import closedw.br.container.rule.SlotRuleRegistry;
import closedw.br.slot.SlotSet;

import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 声明式槽位规则（内置 json / Configured）的适配器。
 * 每条规则可以带自己的配置键（{@code "config": "compressor"}）决定它开不开。
 * 取出与放入分别走规则里的 {@code extract} / {@code deposit} 两个方向，因此
 * Vinery 苹果压榨器那种「取出燃料槽 ≠ 放入燃料槽」的不对称布局也能表达。
 */
public final class RuleContainerSupport implements ContainerSupport {

    @Override
    public String configKey() {
        // 配置键随规则走，由每个方块自己决定，适配器本身没有统一的键
        return null;
    }

    @Override
    public boolean enabled() {
        // 单条规则的开与关由 explicitlyDisabled 表达（这样才不会被后续兜底实现接管）
        return true;
    }

    @Override
    public boolean matches(BlockEntity blockEntity) {
        return SlotDetection.hasRule(blockEntity);
    }

    @Override
    public boolean explicitlyDisabled(BlockEntity blockEntity) {
        SlotRule rule = SlotRuleRegistry.find(blockEntity);
        if (rule == null) {
            return false;
        }
        String key = rule.configKey();
        if (key == null || key.isBlank()) {
            // 没有独立开关的规则（例如内置的原版容器规则）永远生效
            return false;
        }
        return !OutputSlotExtractor.isContainerEnabled(key);
    }

    @Override
    public ContainerAccess access(BlockEntity blockEntity) {
        if (blockEntity instanceof Container container) {
            return new InventoryAccess(blockEntity, container);
        }
        return null;
    }

    @Override
    public int[] extractSlots(BlockEntity blockEntity, SlotSet mode) {
        return SlotDetection.getRuleSlotsForMode(blockEntity, mode);
    }

    @Override
    public int[] depositSlots(BlockEntity blockEntity, SlotSet mode) {
        return SlotDetection.getRuleDepositSlots(blockEntity, mode);
    }
}
