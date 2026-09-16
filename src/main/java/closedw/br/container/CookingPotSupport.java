package closedw.br.container;

import closedw.br.ExtractionMode;
import closedw.br.farmersdelight.FarmersDelightSupport;
import net.minecraft.block.entity.BlockEntity;

/**
 * 农夫乐事厨锅：非 Inventory，走 {@link CookingPotAccess} 反射访问。
 * 槽位：0-5 食材输入，6 成品显示，7 容器槽，8 成品输出。
 * 燃料预设映射到容器槽；放入只支持食材输入。
 */
public final class CookingPotSupport implements ContainerSupport {

	public static final String CONFIG_KEY = "cooking_pot";

	private static final int[] INPUT_SLOTS = { 0, 1, 2, 3, 4, 5 };
	private static final int[] FUEL_SLOTS = { 7 };
	private static final int[] OUTPUT_SLOTS = { 8 };
	private static final int[] RESTOCK_SLOTS = { 0, 1, 2, 3, 4, 5, 7 };

	@Override
	public String configKey() {
		return CONFIG_KEY;
	}

	@Override
	public boolean matches(BlockEntity blockEntity) {
		return FarmersDelightSupport.isCookingPot(blockEntity);
	}

	@Override
	public ContainerAccess access(BlockEntity blockEntity) {
		if (!matches(blockEntity)) {
			return null;
		}
		return new CookingPotAccess(blockEntity);
	}

	@Override
	public int[] extractSlots(BlockEntity blockEntity, ExtractionMode mode) {
		ContainerAccess access = access(blockEntity);
		if (access == null) {
			return null;
		}
		return switch (mode) {
			case ALL -> ContainerSupport.allSlots(access);
			case OUTPUT -> OUTPUT_SLOTS;
			case INPUT -> INPUT_SLOTS;
			case FUEL -> FUEL_SLOTS;
		};
	}

	@Override
	public int[] depositSlots(BlockEntity blockEntity, ExtractionMode mode) {
		if (mode != ExtractionMode.INPUT) {
			return null;
		}
		return INPUT_SLOTS;
	}

	@Override
	public int[] restockSlots(BlockEntity blockEntity) {
		// 碗槽（7）在默认的“输入槽 ∪ 燃料槽”之外，这里显式加入，让空碗能被补满
		return RESTOCK_SLOTS;
	}
}
