package closedw.br;

import closedw.br.client.MappingSession;
import closedw.br.client.ModeWheel;
import closedw.br.networking.ExtractKeyStatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
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
        NeoForge.EVENT_BUS.addListener(BetterRemovalClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(BetterRemovalClient::onLoggingOut);
        // 手动指认：在容器界面里滚轮选画笔、按住修饰键滑过槽位涂抹、画叠层标签
        NeoForge.EVENT_BUS.addListener(BetterRemovalClient::onScreenMouseScrolled);
        NeoForge.EVENT_BUS.addListener(BetterRemovalClient::onScreenClosing);
        NeoForge.EVENT_BUS.addListener(BetterRemovalClient::onContainerScreenForeground);
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

        // 左Alt修饰键：按下状态始终同步到服务端。
        // 必须用"物理按下"判定：原版 KeyboardHandler#keyPress 里三处 KeyMapping.set 与
        // KeyMapping.click 全都在 `screen == null` 分支内（1.20.1 Forge 反汇编实证），
        // 所以开着容器界面时 isDown() 一直停在"打开界面那一刻"的值，服务端收不到 GUI 内的按/松。
        // GUI 内部的判定本来就用 isExtractKeyPhysicallyDown()，这里与它统一。
        if (extractKey != null) {
            boolean pressed = isExtractKeyPhysicallyDown();
            if (pressed != extractLastPressed) {
                extractLastPressed = pressed;
                PacketDistributor.sendToServer(new ExtractKeyStatePayload(pressed));
            }
        }
    }

    /**
     * 服务端在玩家加入时把修饰键状态重置为"未按下"。客户端只在按键状态变化时才发包，
     * 所以重连后必须重新同步一次，否则修饰键要抬起来再按才生效。
     */
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        extractLastPressed = false;
        if (extractKey != null && extractKey.isDown()) {
            extractLastPressed = true;
            PacketDistributor.sendToServer(new ExtractKeyStatePayload(true));
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        extractLastPressed = false;
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
     * 容器界面内的滚轮：指认会话开启时用来换画笔。
     */
    public static void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (MappingSession.onMouseScrolled(event)) {
            event.setCanceled(true);
        }
    }

    /** 任意界面关闭：把指认会话换掉的系统光标还回去。 */
    public static void onScreenClosing(ScreenEvent.Closing event) {
        MappingSession.onScreenClosing();
    }

    /** 容器界面前景：画指认标签。 */
    public static void onContainerScreenForeground(ContainerScreenEvent.Render.Foreground event) {
        MappingSession.renderForeground(event);
    }

    /**
     * 修饰键是否处于按下状态（供 Jade 预览判断左Alt）。
     */
    public static boolean isExtractKeyPressed() {
        return isExtractKeyPhysicallyDown();
    }

    /**
     * 修饰键此刻是否真的被按着：直接问 GLFW，绕开 KeyMapping 在界面打开时不更新的问题。
     * 键盘与鼠标键位都支持（默认是左 Alt）。
     */
    public static boolean isExtractKeyPhysicallyDown() {
        if (extractKey == null) {
            return false;
        }
        InputConstants.Key key = extractKey.getKey();
        if (key.getValue() < 0) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }
}
