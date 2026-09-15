package common.client;

import common.CarryOnKeyState;
import common.ExtractionModeManager;
import common.ModeState;
import common.networking.ExtractKeyStateC2SPayload;
import common.networking.ExtractionModeSyncS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化。
 * - 修饰键（左Alt，可改键）：按下状态始终同步到服务端。
 * - 模式键：按住时在快捷栏上方显示模式列表，滚轮选择，松开生效。
 */
public class BetterRemovalClient implements ClientModInitializer {

	private static final String CATEGORY = "key.better-removal.category";
	private static final String EXTRACT_KEY = "key.better-removal.extract";
	private static final String MODE_KEY = "key.better-removal.mode";

	private static KeyBinding extractKey;
	private static KeyBinding modeKey;
	private static boolean extractLastPressed = false;
	private static boolean modeLastPressed = false;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(ExtractionModeSyncS2CPayload.ID,
				(payload, context) -> ExtractionModeManager.setClientState(
						new ModeState(payload.action(), payload.mode()), payload.probeAvailable()));

		registerModeKey();
		registerExtractKey();

		HudRenderCallback.EVENT.register((context, tickDelta) -> ModeWheel.render(context));
	}

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
	 */
	private void registerExtractKey() {
		extractKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(EXTRACT_KEY, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) {
				return;
			}
			boolean pressed = extractKey.isPressed();
			CarryOnKeyState.setPressed(pressed);
			if (pressed != extractLastPressed) {
				extractLastPressed = pressed;
				ClientPlayNetworking.send(new ExtractKeyStateC2SPayload(pressed));
			}
		});
	}

	/**
	 * 修饰键是否处于按下状态（供 Jade 预览判断左Alt）。
	 */
	public static boolean isExtractKeyPressed() {
		return extractKey != null && extractKey.isPressed();
	}
}
