package closedw.br;

import closedw.br.client.ModeWheel;
import closedw.br.networking.AltKeyStatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化。
 * - 修饰键（左Alt，可改键）：按下状态始终同步到服务端。
 * - 模式键：按住时在快捷栏上方显示模式列表，滚轮选择，松开生效。
 */
@Mod(value = BetterRemoval.MODID, dist = Dist.CLIENT)
public class BetterRemovalClient {

    public static final String CATEGORY = "key.betterremoval.category";
    public static final String MODE_KEY = "key.betterremoval.mode";
    public static final String EXTRACT_KEY = "key.betterremoval.extract";

    private static KeyMapping modeKey;
    private static KeyMapping extractKey;
    private static boolean extractLastPressed = false;
    private static boolean modeLastPressed = false;

    public BetterRemovalClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(BetterRemovalClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(BetterRemovalClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BetterRemovalClient::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(BetterRemovalClient::onRenderGui);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        modeKey = new KeyMapping(MODE_KEY, InputConstants.UNKNOWN.getValue(), CATEGORY);
        event.register(modeKey);

        extractKey = new KeyMapping(EXTRACT_KEY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
        event.register(extractKey);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 模式键：按住打开滚轮，松开提交
        boolean modePressed = modeKey != null && modeKey.isDown();
        if (modePressed != modeLastPressed) {
            modeLastPressed = modePressed;
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
            if (pressed != extractLastPressed) {
                extractLastPressed = pressed;
                PacketDistributor.sendToServer(new AltKeyStatePayload(pressed));
            }
        }
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (ModeWheel.onScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        ModeWheel.render(event.getGuiGraphics());
    }

    /**
     * 修饰键是否处于按下状态（供 Jade 预览判断左Alt）。
     */
    public static boolean isExtractKeyPressed() {
        return extractKey != null && extractKey.isDown();
    }
}
