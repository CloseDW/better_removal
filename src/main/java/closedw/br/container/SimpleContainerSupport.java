package closedw.br.container;

import closedw.br.ExtractionMode;
import net.minecraft.block.entity.BlockEntity;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 普通 Inventory 容器的描述：匹配谓词 + 取出/放入槽位表。
 * 新增一个普通容器只要在这里注册一条，不再需要同时改多条分派链。
 */
public final class SimpleContainerSupport extends InventoryContainerSupport {

	private final Predicate<BlockEntity> matcher;
	private final Map<ExtractionMode, int[]> extract = new EnumMap<>(ExtractionMode.class);
	private final Map<ExtractionMode, int[]> deposit = new EnumMap<>(ExtractionMode.class);
	private boolean extractAll;
	private boolean depositAll;

	public SimpleContainerSupport(String configKey, Predicate<BlockEntity> matcher) {
		super(configKey);
		this.matcher = matcher;
	}

	/** 取出模式下该槽位组合。 */
	public SimpleContainerSupport extract(ExtractionMode mode, int... slots) {
		this.extract.put(mode, slots);
		return this;
	}

	/** 取出模式下输入/燃料/输出都取全部槽位（漏斗、发射器、木篮等）。 */
	public SimpleContainerSupport extractAll() {
		this.extractAll = true;
		return this;
	}

	/** 放入模式下该槽位组合。 */
	public SimpleContainerSupport deposit(ExtractionMode mode, int... slots) {
		this.deposit.put(mode, slots);
		return this;
	}

	/** 放入模式下输入/燃料都放入全部槽位。 */
	public SimpleContainerSupport depositAll() {
		this.depositAll = true;
		return this;
	}

	/** 取出与放入都是全部槽位。 */
	public SimpleContainerSupport allModes() {
		return extractAll().depositAll();
	}

	@Override
	public boolean matches(BlockEntity blockEntity) {
		return this.matcher.test(blockEntity);
	}

	@Override
	protected int[] extractModeSlots(ExtractionMode mode, ContainerAccess access) {
		if (this.extractAll) {
			return ContainerSupport.allSlots(access);
		}
		return this.extract.get(mode);
	}

	@Override
	protected int[] depositModeSlots(ExtractionMode mode, ContainerAccess access) {
		if (this.depositAll) {
			return ContainerSupport.allSlots(access);
		}
		return this.deposit.get(mode);
	}
}
