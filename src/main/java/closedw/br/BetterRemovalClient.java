package closedw.br;

import closedw.br.behavior.InteractionMode;
import closedw.br.behavior.InteractionModeManager;
import closedw.br.client.MappingSession;
import closedw.br.client.ModeWheel;
import closedw.br.mapping.MappingSessionState;
import closedw.br.networking.ExtractionModeSyncS2CPacket;
import closedw.br.networking.ExtractKeyStateC2SPacket;
import closedw.br.networking.MappingFeedbackS2CPacket;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化。
 * - 修饰键（左Alt，可改键）：按下状态始终同步到服务端。
 * - 模式键：按住时在快捷栏上方显示模式列表，滚轮选择，松开生效。
 * - 接收服务端同步的模式（供Jade预览），以及"主动探测/手动指认"是否可用。
 * - 手动指认：界面关闭时还原系统光标（叠层渲染与滚轮由 mixin 转发给 {@link MappingSession}）。
 */
public class BetterRemovalClient implements ClientModInitializer {

	public static final String CATEGORY = "key.better-removal.category";
	public static final String EXTRACT_KEY = "key.better-removal.extract";
	public static final String MODE_KEY = "key.better-removal.mode";

	private static KeyBinding extractKey;
	private static KeyBinding modeKey;
	private static boolean extractLastPressed = false;
	private static boolean modeLastPressed = false;

	@Override
	public void onInitializeClient() {
		// 接收服务端同步的取出/放入模式，以及"主动探测/手动指认"是否可用
		ClientPlayNetworking.registerGlobalReceiver(ExtractionModeSyncS2CPacket.TYPE, (packet, player, responseSender) ->
				InteractionModeManager.setClientState(new InteractionMode(packet.behavior(), packet.slots()),
						packet.probeAvailable(), packet.mappingAvailable()));

		// 接收指认会话的权威状态（整张角色表），叠层标签照着它渲染
		ClientPlayNetworking.registerGlobalReceiver(MappingFeedbackS2CPacket.TYPE, (packet, player, responseSender) -> {
			MappingSessionState.update(packet);
			if (!packet.active()) {
				// 会话结束：必须在这里就丢掉绑定的界面引用。
				// 会话结束的典型时刻是"没有容器界面在渲染"（关掉 GUI 服务端才会结束会话），
				// 渲染钩子那时不会跑，只靠它清理会让下一个会话永远绑不上（所有容器都指认不了）。
				MappingSession.onSessionEnded();
			}
		});

		registerModeKey();
		registerExtractKey();
		registerConnectionSync();
		registerScreenHooks();

		// 模式滚轮绘制（快捷栏上方）
		HudRenderCallback.EVENT.register((context, tickDelta) -> ModeWheel.render(context));
	}

	/**
	 * 服务端在玩家加入时把修饰键状态重置为"未按下"。客户端只在按键状态变化时才发包，
	 * 所以重连后必须重新同步一次，否则修饰键要抬起来再按才生效。
	 */
	private void registerConnectionSync() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			extractLastPressed = false;
			if (extractKey != null && extractKey.isPressed()) {
				extractLastPressed = true;
				ClientPlayNetworking.send(new ExtractKeyStateC2SPacket(true));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> extractLastPressed = false);
	}

	/**
	 * 界面钩子：界面关闭时把指认会话换掉的系统光标还回去。
	 * <p>
	 * 容器前景的叠层渲染与界面内的滚轮由 mixin 转发：
	 * 前者挂在 {@code HandledScreen#drawForeground} 之后（{@code HandledScreenMappingMixin}），
	 * 后者挂在 {@code Mouse#onMouseScroll} 上（{@code MouseScrollMixin}，与模式滚轮同一个挂点）。
	 */
	private void registerScreenHooks() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof HandledScreen<?>) {
				ScreenEvents.remove(screen).register(removed -> MappingSession.onScreenClosing());
			}
		});
	}

	/**
	 * 模式键：按住打开模式列表（快捷栏上方），滚轮选择，松开提交。
	 */
	private void registerModeKey() {
		modeKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(MODE_KEY, InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) {
				return;
			}
			boolean pressed = modeKey.isPressed();
			if (pressed != modeLastPressed) {
				modeLastPressed = pressed;
				if (pressed) {
					ModeWheel.open();
				}
				else {
					ModeWheel.commit();
				}
			}
		});
	}

	/**
	 * 修饰键（左Alt，可改键）：按下状态始终同步到服务端。
	 * <p>
	 * 用 {@link #isExtractKeyPhysicallyDown()}（直接问 GLFW）而不是 {@code KeyBinding#isPressed()}：
	 * 原版<b>在有界面打开时不会更新 KeyBinding</b>，于是"按住 Alt 右击容器 → 在界面里松开 Alt"
	 * 之后服务端会永远以为 Alt 还按着——后果是之后每次普通右击容器都会开一个指认会话，
	 * 而且即使用户想再按 Alt 也同步不回来。物理判定没有这个问题。
	 */
	private void registerExtractKey() {
		extractKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(EXTRACT_KEY, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) {
				return;
			}
			boolean pressed = isExtractKeyPhysicallyDown();
			if (pressed != extractLastPressed) {
				extractLastPressed = pressed;
				ClientPlayNetworking.send(new ExtractKeyStateC2SPacket(pressed));
			}
		});
	}

	/**
	 * 修饰键是否处于按下状态（供 Jade 预览判断左Alt）。
	 * 与同步用的判定保持一致，避免"预览出现了但取不了"这类不一致。
	 */
	public static boolean isExtractKeyPressed() {
		return isExtractKeyPhysicallyDown();
	}

	/**
	 * 鼠标此刻在「界面坐标」里的 X。
	 * <p>
	 * <b>{@code Mouse#getX()} 返回的是窗口绝对像素</b>（1.20.1 里它的方法体就是 {@code return this.x;}，
	 * 不做任何缩放），而界面原点、槽位 x/y 以及渲染回调收到的 mouseX/mouseY 全都是<b>缩放后</b>的。
	 * 原版也是在使用前自己换算：{@code GameRenderer} 渲染界面时、{@code Mouse#onMouseScroll}
	 * 把滚动转发给界面时，都做了 {@code * getScaledWidth() / getWidth()}。
	 * <p>
	 * 少了这一步，鼠标位置会差一个 GUI 缩放倍数——表现就是"鼠标明明在界面上，却总被判成在界面外，
	 * 槽位也永远悬停不到"。踩过一次，记在这里。
	 */
	public static double mouseGuiX() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getWindow() == null || mc.getWindow().getWidth() <= 0) {
			return 0.0;
		}
		Window window = mc.getWindow();
		return mc.mouse.getX() * window.getScaledWidth() / (double) window.getWidth();
	}

	/** 见 {@link #mouseGuiX()}：鼠标在「界面坐标」里的 Y。 */
	public static double mouseGuiY() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getWindow() == null || mc.getWindow().getHeight() <= 0) {
			return 0.0;
		}
		Window window = mc.getWindow();
		return mc.mouse.getY() * window.getScaledHeight() / (double) window.getHeight();
	}

	/**
	 * 修饰键此刻是否真的被按着：直接问 GLFW，绕开 KeyBinding 在界面打开时不更新的问题。
	 * 键盘与鼠标键位都支持（默认是左 Alt）。
	 * <p>
	 * 1.20.1 的 {@code KeyBinding} 没有公开"当前绑定键"的 getter，所以用
	 * {@code getBoundKeyTranslationKey()} 反查成 {@link InputUtil.Key}
	 * （不能用 {@code getDefaultKey()}：玩家改键后就失效了）。
	 */
	public static boolean isExtractKeyPhysicallyDown() {
		if (extractKey == null) {
			return false;
		}
		InputUtil.Key key;
		try {
			key = InputUtil.fromTranslationKey(extractKey.getBoundKeyTranslationKey());
		}
		catch (Throwable t) {
			return false;
		}
		if (key == null || key.getCode() < 0) {
			return false;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		long window = mc.getWindow().getHandle();
		if (key.getCategory() == InputUtil.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
		}
		return InputUtil.isKeyPressed(window, key.getCode());
	}
}
