package closedw.br.container;

import closedw.br.ExtractionMode;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;

/**
 * 基于原版 {@link Inventory} 的容器基类：
 * 访问方式（InventoryAccess）与 ALL=全部槽位的通用规则在这里统一，子类只描述自己的槽位表。
 */
public abstract class InventoryContainerSupport implements ContainerSupport {

	private final String configKey;

	protected InventoryContainerSupport(String configKey) {
		this.configKey = configKey;
	}

	@Override
	public String configKey() {
		return this.configKey;
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
		ContainerAccess access = access(blockEntity);
		if (access == null) {
			return null;
		}
		if (mode == ExtractionMode.ALL) {
			return ContainerSupport.allSlots(access);
		}
		return extractModeSlots(mode, access);
	}

	/** INPUT/FUEL/OUTPUT（不含 ALL）的槽位表。 */
	protected int[] extractModeSlots(ExtractionMode mode, ContainerAccess access) {
		return null;
	}

	@Override
	public int[] depositSlots(BlockEntity blockEntity, ExtractionMode mode) {
		if (mode != ExtractionMode.INPUT && mode != ExtractionMode.FUEL) {
			return null;
		}
		ContainerAccess access = access(blockEntity);
		if (access == null) {
			return null;
		}
		return depositModeSlots(mode, access);
	}

	/** INPUT/FUEL 的槽位表。 */
	protected int[] depositModeSlots(ExtractionMode mode, ContainerAccess access) {
		return null;
	}
}
