package closedw.br.container;

import closedw.br.ExtractionMode;
import closedw.br.cookingforblockheads.CookingForBlockheadsSupport;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;

/**
 * Cooking for Blockheads 烤炉：方块实体本身不是 Inventory，
 * 通过反射拿内部容器（20格）后复用 {@link InventoryAccess}。
 * 槽位：0-2 输入，3 燃料，4-6 输出；ALL=输入+燃料+输出（不含加工格7-15与工具格16-19）。
 * 模组开启“禁止烤炉自动化”时不交互。
 */
public final class OvenSupport implements ContainerSupport {

	public static final String CONFIG_KEY = "oven";

	private static final int[] INPUT_SLOTS = { 0, 1, 2 };
	private static final int[] FUEL_SLOTS = { 3 };
	private static final int[] OUTPUT_SLOTS = { 4, 5, 6 };
	private static final int[] ALL_SLOTS = { 0, 1, 2, 3, 4, 5, 6 };

	@Override
	public String configKey() {
		return CONFIG_KEY;
	}

	@Override
	public boolean matches(BlockEntity blockEntity) {
		return CookingForBlockheadsSupport.isOven(blockEntity);
	}

	@Override
	public boolean allowInteraction(BlockEntity blockEntity) {
		return !CookingForBlockheadsSupport.isAutomationDisallowed();
	}

	@Override
	public ContainerAccess access(BlockEntity blockEntity) {
		Inventory inventory = CookingForBlockheadsSupport.getInternalInventory(blockEntity);
		if (inventory == null) {
			return null;
		}
		return new InventoryAccess(blockEntity, inventory);
	}

	@Override
	public int[] extractSlots(BlockEntity blockEntity, ExtractionMode mode) {
		if (access(blockEntity) == null) {
			return null;
		}
		return switch (mode) {
			case ALL -> ALL_SLOTS;
			case OUTPUT -> OUTPUT_SLOTS;
			case INPUT -> INPUT_SLOTS;
			case FUEL -> FUEL_SLOTS;
		};
	}

	@Override
	public int[] depositSlots(BlockEntity blockEntity, ExtractionMode mode) {
		if (access(blockEntity) == null) {
			return null;
		}
		return switch (mode) {
			case INPUT -> INPUT_SLOTS;
			case FUEL -> FUEL_SLOTS;
			default -> null;
		};
	}
}
