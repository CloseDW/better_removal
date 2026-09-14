package com.better_removal.client;

import com.better_removal.BetterRemoval;
import com.better_removal.networking.AltKeyStatePacket;
import com.better_removal.networking.BetterRemovalNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化。
 * - 修饰键（左Alt，可改键）：按下状态始终同步到服务端。
 * - 模式键：按住时在快捷栏上方显示模式列表，滚轮选择，松开生效。
 */
@Mod.EventBusSubscriber(modid = BetterRemoval.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {

	private static final String CATEGORY = "key.better_removal.category";
	private static final String MODE_KEY = "key.better_removal.mode";
	private static final String EXTRACT_KEY = "key.better_removal.extract";

	private static KeyMapping modeKey;
	private static KeyMapping extractKey;
	private static boolean lastAltPressed = false;
	private static boolean lastModePressed = false;

	private ClientEvents() {
	}

	@SubscribeEvent
	public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		modeKey = new KeyMapping(MODE_KEY, InputConstants.UNKNOWN.getValue(), CATEGORY);
		event.register(modeKey);

		extractKey = new KeyMapping(EXTRACT_KEY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
		event.register(extractKey);
	}

	/**
	 * 修饰键是否处于按下状态（供 Jade 预览判断左Alt）。
	 */
	public static boolean isExtractKeyPressed() {
		return extractKey != null && extractKey.isDown();
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

			// 模式键：按住打开滚轮，松开提交
			boolean modePressed = modeKey != null && modeKey.isDown();
			if (modePressed != lastModePressed) {
				lastModePressed = modePressed;
				if (modePressed) {
					ModeWheel.open();
				}
				else {
					ModeWheel.commit();
				}
			}

			// 左Alt修饰键：按下状态始终同步到服务端
			if (extractKey != null) {
				boolean pressed = extractKey.isDown();
				if (pressed != lastAltPressed) {
					lastAltPressed = pressed;
					BetterRemovalNetwork.CHANNEL.sendToServer(new AltKeyStatePacket(pressed));
				}
			}
		}

		@SubscribeEvent
		public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
			if (ModeWheel.onScroll(event.getScrollDelta())) {
				event.setCanceled(true);
			}
		}

		@SubscribeEvent
		public static void onRenderGui(RenderGuiEvent.Post event) {
			ModeWheel.render(event.getGuiGraphics());
		}
	}
}