package closedw.br.mixin.client;

import closedw.br.BetterRemovalClient;
import closedw.br.client.MappingSession;
import closedw.br.client.ModeWheel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 滚轮事件的统一挂点。
 * <p>
 * 两件事共用这里，顺序不能颠倒：
 * <ol>
 *   <li>模式滚轮：按住模式键时拦截滚动，用于在模式列表中移动选择。
 *       （Fabric API 1.20.1 没有"无界面时的滚轮事件"，必须挂 mixin）</li>
 *   <li>手动指认：容器界面里按住修饰键滚轮换画笔。选用这个挂点而不是 Fabric 的
 *       {@code ScreenMouseEvents.allowMouseScroll}，是为了让"取消滚动"的语义由本模组自己完全掌握
 *       （返回值是否被 Fabric 的 Event 采纳取决于其内部约定），同时和上面那条保持同一个入口。</li>
 * </ol>
 */
@Mixin(Mouse.class)
public abstract class MouseScrollMixin {

	@Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void betterremoval$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (ModeWheel.onScroll(vertical)) {
			ci.cancel();
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || !(mc.currentScreen instanceof HandledScreen<?> screen)) {
			return;
		}
		// 鼠标位置必须换算成「界面坐标」再交出去：Mouse#getX() 是窗口绝对像素，
		// 而界面与槽位坐标都是缩放后的（换算见 BetterRemovalClient#mouseGuiX 的注释）。
		if (MappingSession.onMouseScrolled(screen, BetterRemovalClient.mouseGuiX(), BetterRemovalClient.mouseGuiY(), vertical)) {
			ci.cancel();
		}
	}
}
