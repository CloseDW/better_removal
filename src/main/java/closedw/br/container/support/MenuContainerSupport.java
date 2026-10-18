package closedw.br.container.support;

import closedw.br.OutputSlotExtractor;
import closedw.br.container.access.MenuSlotAccess;
import closedw.br.container.menu.MenuMap;
import closedw.br.container.menu.MenuMapStore;
import closedw.br.container.menu.MenuResolver;
import closedw.br.slot.SlotRole;
import closedw.br.slot.SlotSet;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;

/**
 * 手动指认出来的容器：槽位布局由玩家在 GUI 里点出来（{@code config/better-removal/menu-maps.json}），
 * 运行时按菜单槽位索引寻址。
 * 用途是覆盖那些"方块实体没实现原版 {@code Inventory}、槽位藏在私有 {@code ItemStackHandler} 里"
 * 的容器——它们写规则也够不着，但菜单里的 {@code Slot} 能寻址到。
 */
public final class MenuContainerSupport implements ContainerSupport {

	@Override
	public String configKey() {
		// 单条映射没有独立开关，总开关是 menu_mapping；单条映射的增删走"清空所有标签即删除"
		return null;
	}

	@Override
	public boolean enabled() {
		return OutputSlotExtractor.isContainerEnabled("menu_mapping");
	}

	@Override
	public boolean matches(BlockEntity blockEntity) {
		return MenuMapStore.find(blockEntity) != null;
	}

	@Override
	public ContainerAccess access(BlockEntity blockEntity, PlayerEntity player) {
		ScreenHandler menu = MenuResolver.open(blockEntity, player);
		return menu == null ? null : new MenuSlotAccess(menu, player);
	}

	/**
	 * 没有操作玩家的入口（预览等）：拿离方块最近的服务端玩家当菜单代理。
	 */
	@Override
	public ContainerAccess access(BlockEntity blockEntity) {
		PlayerEntity player = MenuResolver.probePlayer(blockEntity.getWorld(), blockEntity.getPos());
		return access(blockEntity, player);
	}

	@Override
	public int[] extractSlots(BlockEntity blockEntity, SlotSet mode) {
		MenuMap map = MenuMapStore.find(blockEntity);
		if (map == null || mode == null) {
			return null;
		}
		int[] slots = switch (mode) {
			case OUTPUT -> map.slots(SlotRole.OUTPUT);
			case INPUT -> map.slots(SlotRole.INPUT);
			case FUEL -> map.slots(SlotRole.FUEL);
			// ALL = 输入 ∪ 燃料 ∪ 输出（忽略项不算）
			case ALL -> map.union(SlotRole.INPUT, SlotRole.FUEL, SlotRole.OUTPUT);
		};
		return slots.length == 0 ? null : slots;
	}

	@Override
	public int[] depositSlots(BlockEntity blockEntity, SlotSet mode) {
		MenuMap map = MenuMapStore.find(blockEntity);
		if (map == null) {
			return null;
		}
		int[] slots = switch (mode) {
			case INPUT -> map.slots(SlotRole.INPUT);
			case FUEL -> map.slots(SlotRole.FUEL);
			default -> new int[0];
		};
		return slots.length == 0 ? null : slots;
	}
}
