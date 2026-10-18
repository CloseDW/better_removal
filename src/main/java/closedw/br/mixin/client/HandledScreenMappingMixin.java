package closedw.br.mixin.client;

import closedw.br.client.MappingScreenAccess;
import closedw.br.client.MappingSession;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手动指认叠层的渲染挂点，以及界面坐标/槽位查询的访问桥。
 * <p>
 * <b>为什么必须挂 mixin</b>：NeoForge 的 {@code ContainerScreenEvent.Render.Foreground} 在 Fabric 1.20.1
 * 没有等价事件。它是在 {@code AbstractContainerScreen#render} 里
 * {@code pushPose() → translate(guiLeft, guiTop) → 画槽位 → 触发事件 → popPose()} 之间触发的；
 * 1.20.1 的对应位置就是 {@code HandledScreen#render} 里调用 {@code drawForeground} 那一句
 * （反编译源 {@code HandledScreen.java:98-118}：第 99 行 translate、第 118 行调 drawForeground、第 150 行 pop）。
 * 所以在 {@code drawForeground} 调用之后注入，拿到的矩阵状态与 NeoForge 的前景事件完全一致——
 * 叠层一律用「GUI 内部坐标」画，不要再加一次界面原点。
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMappingMixin implements MappingScreenAccess {

	@Accessor("x")
	public abstract int betterremoval$guiLeft();

	@Accessor("y")
	public abstract int betterremoval$guiTop();

	@Accessor("backgroundWidth")
	public abstract int betterremoval$backgroundWidth();

	@Accessor("backgroundHeight")
	public abstract int betterremoval$backgroundHeight();

	@Invoker("getSlotAt")
	public abstract Slot betterremoval$slotAt(double mouseX, double mouseY);

	@Inject(
			method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawForeground(Lnet/minecraft/client/gui/DrawContext;II)V",
					shift = At.Shift.AFTER))
	private void betterremoval$afterDrawForeground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		MappingSession.renderForeground((HandledScreen<?>) (Object) this, context, mouseX, mouseY);
	}
}
