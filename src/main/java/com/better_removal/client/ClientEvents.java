package com.better_removal.client;

import com.better_removal.BetterRemoval;
import com.better_removal.networking.AltKeyStatePacket;
import com.better_removal.networking.BetterRemovalNetwork;
import com.better_removal.networking.ExtractionModeCyclePacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化。
 * - 取出模式切换按键：默认不指定按键，按下后循环切换取出模式。
 * - 仅在同时安装Carry On时注册"取出物品"左Alt键，避免与Carry On的Shift+右键冲突。
 */
@Mod.EventBusSubscriber(modid = BetterRemoval.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {

	private static final String CATEGORY = "key.better_removal.category";
	private static final String MODE_KEY = "key.better_removal.mode";
	private static final String EXTRACT_KEY = "key.better_removal.extract";

	private static KeyMapping modeKey;
	private static KeyMapping extractKey;
	private static boolean lastAltPressed = false;

	private ClientEvents() {
	}

	@SubscribeEvent
	public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		modeKey = new KeyMapping(MODE_KEY, InputConstants.UNKNOWN.getValue(), CATEGORY);
		event.register(modeKey);

		if (ModList.get().isLoaded("carryon")) {
			extractKey = new KeyMapping(EXTRACT_KEY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
			event.register(extractKey);
		}
	}

	@Mod.EventBusSubscriber(modid = BetterRemoval.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
	public static final class TickHandler {

		private TickHandler() {
		}

		@SubscribeEvent
		public static void onClientTick(TickEvent.ClientTickEvent event) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				return;
			}

			if (modeKey != null && modeKey.consumeClick()) {
				BetterRemovalNetwork.CHANNEL.sendToServer(new ExtractionModeCyclePacket());
			}

			if (extractKey != null) {
				boolean pressed = extractKey.isDown();
				if (pressed != lastAltPressed) {
					lastAltPressed = pressed;
					BetterRemovalNetwork.CHANNEL.sendToServer(new AltKeyStatePacket(pressed));
				}
			}
		}
	}
}