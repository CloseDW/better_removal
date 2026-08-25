package com.example;

import com.example.networking.ExtractKeyStateC2SPacket;
import com.example.networking.ExtractionModeCycleC2SPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化。
 * - 取出模式切换按键：默认不指定按键，按下后循环切换取出模式。
 * - 仅在同时安装Carry On时注册"取出物品"左Alt键，避免与Carry On的Shift+右键冲突。
 */
public class BetterRemovalClient implements ClientModInitializer {

	private static final String CATEGORY = "key.better-removal.category";
	private static final String EXTRACT_KEY = "key.better-removal.extract";
	private static final String MODE_KEY = "key.better-removal.mode";

	private static boolean lastPressed = false;

	@Override
	public void onInitializeClient() {
		registerModeCycleKey();

		if (FabricLoader.getInstance().isModLoaded("carryon")) {
			registerExtractKey();
		}
	}

	/**
	 * 取出模式循环切换按键（默认不绑定）。按下时发送循环切换请求。
	 */
	private void registerModeCycleKey() {
		KeyBinding modeKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(MODE_KEY, InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) {
				return;
			}
			if (modeKey.wasPressed()) {
				ClientPlayNetworking.send(new ExtractionModeCycleC2SPacket());
			}
		});
	}

	/**
	 * Carry On 兼容键：左Alt 按下状态同步到服务端。
	 */
	private void registerExtractKey() {
		KeyBinding extractKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(EXTRACT_KEY, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) {
				return;
			}
			boolean pressed = extractKey.isPressed();
			if (pressed != lastPressed) {
				lastPressed = pressed;
				ClientPlayNetworking.send(new ExtractKeyStateC2SPacket(pressed));
			}
		});
	}
}