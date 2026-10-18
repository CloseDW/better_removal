package closedw.br.client;

import net.minecraft.screen.slot.Slot;

/**
 * 客户端容器界面的坐标与槽位查询。
 * <p>
 * 1.20.1 的 {@code HandledScreen} 把界面原点（{@code guiLeft}/{@code guiTop}）与
 * "鼠标下是哪个槽位"都做成了 {@code protected}/{@code private}，Fabric 没有对应的公开 API，
 * 所以由 {@code closedw.br.mixin.client.HandledScreenMappingMixin} 给
 * {@code HandledScreen} 补上这个接口。
 * <p>
 * 对应 NeoForge 侧用到的 {@code AbstractContainerScreen#getGuiLeft()/getGuiTop()} 与
 * {@code #getSlotUnderMouse()}。
 */
public interface MappingScreenAccess {

	/** 界面左上角在屏幕上的 X（NeoForge 的 {@code getGuiLeft()}）。 */
	int betterremoval$guiLeft();

	/** 界面左上角在屏幕上的 Y（NeoForge 的 {@code getGuiTop()}）。 */
	int betterremoval$guiTop();

	/** 界面宽度（NeoForge 的 {@code getXSize()}）。1.20.1 里是 {@code protected} 字段。 */
	int betterremoval$backgroundWidth();

	/** 界面高度（NeoForge 的 {@code getYSize()}）。1.20.1 里是 {@code protected} 字段。 */
	int betterremoval$backgroundHeight();

	/**
	 * 屏幕坐标下的鼠标位置落在哪个槽位上；没有则返回 null。
	 * 原版实现只返回 {@code isEnabled()} 的槽位，隐藏槽位自动被排除。
	 */
	Slot betterremoval$slotAt(double mouseX, double mouseY);
}
