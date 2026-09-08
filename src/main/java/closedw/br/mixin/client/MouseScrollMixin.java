package closedw.br.mixin.client;

import closedw.br.client.ModeWheel;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 模式滚轮的滚轮事件：按住模式键时拦截滚动，用于在模式列表中移动选择
 * （Fabric API 1.20.1 没有现成的滚轮事件，故挂一个最小化的客户端 mixin）。
 */
@Mixin(Mouse.class)
public abstract class MouseScrollMixin {

	@Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void betterremoval$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (ModeWheel.onScroll(vertical)) {
			ci.cancel();
		}
	}
}
