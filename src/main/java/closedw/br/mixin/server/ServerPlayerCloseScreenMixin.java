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
 * NeoForge 用 {@code PlayerContainerEvent.Close} 表达这件事，Fabric 1.20.1 没有等价事件，
 * 只能注入原版方法。这里<b>挂两个点</b>，因为 1.20.1 把"关界面"拆成了两条独立的路：
 *
 * <ol>
 *   <li><b>{@code onHandledScreenClosed()}</b>（{@code method_14247}）——
 *       <b>玩家按 E / ESC 关界面走的就是这条</b>：客户端发 {@code CloseHandledScreenC2SPacket}，
 *       服务端在 {@code ServerPlayNetworkHandler#onCloseHandledScreen} 里调它。
 *       字节码实测（{@code minecraft-merged-intermediary.jar}）：
 *       {@code method_12054} 的方法体只有
 *       {@code PacketUtils.ensureRunningOnSameThread(...)} + {@code player.method_14247()}，
 *       <b>没有</b> {@code method_7346}。</li>
 *   <li><b>{@code closeHandledScreen()}</b>（{@code method_7346}）——只在
 *       "开新容器时旧容器还开着"那条分支里被调用（{@code openHandledScreen} 里判断
 *       {@code currentScreenHandler != playerScreenHandler} 后调它）。</li>
 * </ol>
 *
 * <p>
 * <b>历史教训</b>：本类最初只挂了第 2 个点，结果在真实游戏里
 * <b>一次都没有触发</b>（一次测试会话里开了关十几个容器，{@code end()} 调用数为 0）。
 * 后果是会话跨容器残留——叠层会画到下一个"菜单槽位总数相同"的容器上
 * （例如两个 27 格的橱柜、两个 55 格的熔炉），看起来像"规则用错了容器"。
 * NeoForge 端用真事件，所以从来没有这个毛病。
 *
 * <p>
 * 两个点都会调 {@link MappingSessionManager#end}，而 {@code end()} 对"当前没有会话"是幂等的，
 * 所以重复触发无害。注入点都取 HEAD：要在 {@code currentScreenHandler} 被换掉之前结束会话。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerCloseScreenMixin {

	/**
	 * 玩家按 E / ESC 关界面（客户端发了关界面包）。<b>这条才是主路径。</b>
	 */
	@Inject(method = "onHandledScreenClosed()V", at = @At("HEAD"))
	private void betterremoval$onHandledScreenClosed(CallbackInfo ci) {
		MappingSessionManager.end((ServerPlayerEntity) (Object) this);
	}

	/**
	 * 开新容器时旧容器还开着（{@code openHandledScreen} 内部会先关掉旧的）。
	 */
	@Inject(method = "closeHandledScreen()V", at = @At("HEAD"))
	private void betterremoval$onCloseHandledScreen(CallbackInfo ci) {
		MappingSessionManager.end((ServerPlayerEntity) (Object) this);
	}
}
