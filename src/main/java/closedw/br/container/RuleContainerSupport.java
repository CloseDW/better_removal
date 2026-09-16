package closedw.br.container;

import closedw.br.ExtractionMode;
import closedw.br.OutputSlotExtractor;
import closedw.br.experimental.AutoDetectSupport;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;

/**
 * 实验性：手写槽位规则（Configured / 用户 json / 模组自带 json）的适配器。
 * 注册在硬编码容器之后，只对未知容器生效。
 */
public final class RuleContainerSupport implements ContainerSupport {

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
		return AutoDetectSupport.hasRule(blockEntity);
	}

	@Override
	public ContainerAccess access(BlockEntity blockEntity) {
		if (blockEntity instanceof Inventory inventory) {
			return new InventoryAccess(blockEntity, inventory);
		}
		return null;
	}

	@Override
	public int[] extractSlots(BlockEntity blockEntity, ExtractionMode mode) {
		return AutoDetectSupport.getRuleSlotsForMode(blockEntity, mode);
	}

	@Override
	public int[] depositSlots(BlockEntity blockEntity, ExtractionMode mode) {
		if (mode != ExtractionMode.INPUT && mode != ExtractionMode.FUEL) {
			return null;
		}
		return AutoDetectSupport.getRuleSlotsForMode(blockEntity, mode);
	}
}
