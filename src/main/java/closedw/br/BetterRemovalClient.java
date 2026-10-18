package closedw.br;

import closedw.br.client.MappingSession;
import closedw.br.client.ModeWheel;
import closedw.br.networking.ExtractKeyStatePayload;
import closedw.br.networking.NetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化（Forge 1.20.1）。
 * - 修饰键（左Alt，可改键）：按下状态始终同步到服务端。
 * - 模式键：按住时在快捷栏上方显示模式列表，滚轮选择，松开生效。
 * <p>
 * 由 {@link BetterRemoval} 在客户端侧调用 {@link #init(IEventBus)}，
 * 服务端（{@code Dist.DEDICATED_SERVER}）不会加载本类。
 * <p>
 * NeoForge 侧这里是一个带 {@code @Mod(dist = CLIENT)} 的独立入口类；
 * Forge 1.20.1 的 {@code @Mod} 没有 {@code dist} 属性，所以改成显式 init。
 */
public final class BetterRemovalClient {

    public static final String CATEGORY = "key.betterremoval.category";
    public static final String MODE_KEY = "key.betterremoval.mode";
    public static final String EXTRACT_KEY = "key.betterremoval.extract";

    private static KeyMapping modeKey;
    private static KeyMapping extractKey;
    private static boolean extractLastPressed = false;
    private static boolean modeLastPressed = false;

    private BetterRemovalClient() {
    }

    public static void init(IEventBus modEventBus) {
        // 键位注册走 mod 总线
        modEventBus.addListener(BetterRemovalClient::onRegisterKeyMappings);
        // 其余都是游戏总线
        MinecraftForge.EVENT_BUS.addListener(BetterRemovalClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(BetterRemovalClient::onMouseScroll);
        MinecraftForge.EVENT_BUS.addListener(BetterRemovalClient::onRenderGui);
        MinecraftForge.EVENT_BUS.addListener(BetterRemovalClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(BetterRemovalClient::onLoggingOut);
        // 手动指认：在容器界面里滚轮选画笔、按住修饰键滑过槽位涂抹、画叠层标签
        MinecraftForge.EVENT_BUS.addListener(BetterRemovalClient::onScreenMouseScrolled);
        MinecraftForge.EVENT_BUS.addListener(BetterRemovalClient::onScreenClosing);
        MinecraftForge.EVENT_BUS.addListener(BetterRemovalClient::onContainerScreenForeground);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        modeKey = new KeyMapping(MODE_KEY, InputConstants.UNKNOWN.getValue(), CATEGORY);
        event.register(modeKey);

        extractKey = new KeyMapping(EXTRACT_KEY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
        event.register(extractKey);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Forge 的 tick 事件每 tick 发两次（START/END），只处理 END
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
                NetworkHandler.sendToServer(new ExtractKeyStatePayload(pressed));
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
            NetworkHandler.sendToServer(new ExtractKeyStatePayload(true));
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        extractLastPressed = false;
    }

    /**
     * 无界面时的滚轮：模式滚轮用。
     * <p>
     * 注意与 Fabric 侧的差异：Fabric 版挂在 {@code Mouse#onMouseScroll} 头部，每次滚动回调都能收到；
     * Forge 的 {@code MouseScrollingEvent} 在滚动累积取整<b>之后</b>才触发，
     * 所以未攒够一格的微小滚动（轨迹板、低灵敏度）不会触发——这是 Forge 事件本身的时机，无法在此规避。
     */
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (ModeWheel.onScroll(event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        ModeWheel.render(event.getGuiGraphics());
    }

    /**
     * 容器界面内的滚轮：指认会话开启时用来换画笔。
     * {@code getMouseX()/getMouseY()} 在 Forge 侧已经是 GUI 缩放坐标，不需要再换算。
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

    /** 容器界面前景：画指认标签/状态条（与 NeoForge 的 Render.Foreground 同一时机与坐标系）。 */
    public static void onContainerScreenForeground(ContainerScreenEvent.Render.Foreground event) {
        MappingSession.renderForeground(event);
    }

    /**
     * 修饰键是否处于按下状态（供 Jade 预览判断左Alt）。
     * <p>
     * 与同步用的判定保持一致，都用 {@link #isExtractKeyPhysicallyDown()}（直接问 GLFW）：
     * 原版在<b>有界面打开时不会</b>把新按下的键写进 {@link KeyMapping}
     * （KeyboardHandler 只在 {@code screen == null} 时 {@code KeyMapping.set(..., true)}），
     * 用 {@code isDown()} 会让"按住 Alt → 开过容器 GUI → 关掉 GUI 但仍按着 Alt"这段时间里
     * 预览不显示（而取物是通的，因为服务端用的是物理状态）——表现就是"能取但没有预览"。
     */
    public static boolean isExtractKeyPressed() {
        return isExtractKeyPhysicallyDown();
    }

    /**
     * 修饰键此刻是否真的被按着：直接问 GLFW，绕开 KeyMapping 在界面打开时不更新的问题。
     * 键盘与鼠标键位都支持（默认是左 Alt）。
     * <p>
     * {@code KeyMapping#getKey()} 在 Forge 1.20.1 是公开的（Fabric 侧要靠
     * {@code getBoundKeyTranslationKey()} 反查，Forge 不需要）。
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
