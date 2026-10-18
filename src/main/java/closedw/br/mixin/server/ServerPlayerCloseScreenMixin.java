package closedw.br.mixin.server;

import closedw.br.mapping.MappingSessionManager;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 容器界面关闭时结束手动指认会话（并让客户端清掉叠层标签）。
 * <p>
 * NeoForge 用 {@code PlayerContainerEvent.Close} 表达这件事，Fabric 1.20.1 没有等价事件；
 * 该事件在 NeoForge 里就是挂在"玩家关闭当前容器"这一步上的，对应 1.20.1 的
 * {@code ServerPlayerEntity#closeHandledScreen()}（反编译源 {@code ServerPlayerEntity.java:1050}，
 * 内部调 {@code currentScreenHandler.onClosed(this)} 并把当前菜单换回玩家背包菜单）。
 * 注入点取 HEAD：要在 {@code currentScreenHandler} 被换掉之前结束会话。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerCloseScreenMixin {

	@Inject(method = "closeHandledScreen()V", at = @At("HEAD"))
	private void betterremoval$onCloseHandledScreen(CallbackInfo ci) {
		MappingSessionManager.end((ServerPlayerEntity) (Object) this);
	}
}
