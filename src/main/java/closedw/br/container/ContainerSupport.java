package closedw.br.container;

import closedw.br.ExtractionMode;
import closedw.br.OutputSlotExtractor;
import net.minecraft.block.entity.BlockEntity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一种容器的完整描述：是否匹配、配置开关、能否交互、槽位读写、各模式槽位表。
 * 硬编码兼容、手写规则、自动探测都实现这个接口，调用方只面向注册表，不再有平行分派链。
 */
public interface ContainerSupport {

	/** 配置/翻译键；无配置开关的实现（规则、自动探测）返回 null。 */
	String configKey();

	/** 该方块是否属于这种容器。 */
	boolean matches(BlockEntity blockEntity);

	/** 容器开关是否打开。 */
	default boolean enabled() {
		return OutputSlotExtractor.isContainerEnabled(configKey());
	}

	/** 是否允许交互（如 Cooking for Blockheads 的“禁止自动化”）。 */
	default boolean allowInteraction(BlockEntity blockEntity) {
		return true;
	}

	/** 该容器的槽位读写；不可寻址时返回 null。 */
	ContainerAccess access(BlockEntity blockEntity);

	/** 取出模式下要取出的槽位；该模式不适用返回 null。 */
	int[] extractSlots(BlockEntity blockEntity, ExtractionMode mode);

	/** 放入模式下可放入的槽位；只处理 INPUT/FUEL，不适用返回 null。 */
	int[] depositSlots(BlockEntity blockEntity, ExtractionMode mode);

	/** 补货模式下可补充的槽位：输入槽与燃料槽的并集。 */
	default int[] restockSlots(BlockEntity blockEntity) {
		Set<Integer> unique = new LinkedHashSet<>();
		int[] input = depositSlots(blockEntity, ExtractionMode.INPUT);
		if (input != null) {
			for (int slot : input) {
				unique.add(slot);
			}
		}
		int[] fuel = depositSlots(blockEntity, ExtractionMode.FUEL);
		if (fuel != null) {
			for (int slot : fuel) {
				unique.add(slot);
			}
		}
		if (unique.isEmpty()) {
			return null;
		}
		int[] result = new int[unique.size()];
		int index = 0;
		for (int slot : unique) {
			result[index++] = slot;
		}
		return result;
	}

	/** 全部槽位 [0, size)。 */
	static int[] allSlots(ContainerAccess access) {
		int size = access.size();
		int[] slots = new int[size];
		for (int i = 0; i < size; i++) {
			slots[i] = i;
		}
		return slots;
	}
}
