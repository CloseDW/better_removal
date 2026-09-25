package closedw.br.container.support;

import closedw.br.container.access.InventoryAccess;
import closedw.br.container.detect.AutoDetectSupport;
import closedw.br.OutputSlotExtractor;
import closedw.br.slot.SlotSet;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;

/**
 * 实验性：自动探测（GUI 菜单语义 / 原版容器接口启发式）的适配器。
 * 需要实验性总开关打开且白名单命中；注册在硬编码容器与手写规则之后。
 */
public final class AutoDetectContainerSupport implements ContainerSupport {

	@Override
	public String configKey() {
		return null;
	}

	@Override
	public boolean enabled() {
		return OutputSlotExtractor.isExperimentalEnabled();
	}

	@Override
	public boolean matches(BlockEntity blockEntity) {
		return AutoDetectSupport.isEnabledFor(blockEntity);
	}

	@Override
	public ContainerAccess access(BlockEntity blockEntity) {
		if (blockEntity instanceof Inventory inventory) {
			return new InventoryAccess(blockEntity, inventory);
		}
		return null;
	}

	@Override
	public int[] extractSlots(BlockEntity blockEntity, SlotSet mode) {
		return AutoDetectSupport.getSlotsForMode(blockEntity, mode);
	}

	@Override
	public int[] depositSlots(BlockEntity blockEntity, SlotSet mode) {
		if (mode != SlotSet.INPUT && mode != SlotSet.FUEL) {
			return null;
		}
		return AutoDetectSupport.getDepositSlots(blockEntity, mode);
	}
}
