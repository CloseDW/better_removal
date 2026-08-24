package com.example;

import com.example.networking.ExtractKeyStateC2SPacket;
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
 * 仅在同时安装Carry On时注册"取出物品"左Alt
 * 并将其按下状态同步到服务端，避免与Carry On的Shift+右键冲突。
 */
public class BetterRemovalClient implements ClientModInitializer {

	private static final String CATEGORY = "key.better-removal.category";
	private static final String EXTRACT_KEY = "key.better-removal.extract";

	private static boolean lastPressed = false;

	@Override
	public void onInitializeClient() {
		if (!FabricLoader.getInstance().isModLoaded("carryon")) {
			return;
		}

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