package closedw.br;

import closedw.br.networking.AltKeyStatePayload;
import closedw.br.networking.ExtractionModeCyclePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化。
 * - 取出模式切换按键：默认不绑定，按下后循环切换取出模式。
 * - 仅在同时安装Carry On时注册"取出物品"左Alt键，避免与Carry On的Shift+右键冲突。
 */
@Mod(value = BetterRemoval.MODID, dist = Dist.CLIENT)
public class BetterRemovalClient {

    public static final String CATEGORY = "key.betterremoval.category";
    public static final String MODE_KEY = "key.betterremoval.mode";
    public static final String EXTRACT_KEY = "key.betterremoval.extract";

    private static KeyMapping modeKey;
    private static KeyMapping extractKey;
    private static boolean lastPressed = false;

    public BetterRemovalClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(BetterRemovalClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(BetterRemovalClient::onClientTick);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        modeKey = new KeyMapping(MODE_KEY, InputConstants.UNKNOWN.getValue(), CATEGORY);
        event.register(modeKey);

        if (ModList.get().isLoaded("carryon")) {
            extractKey = new KeyMapping(EXTRACT_KEY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
            event.register(extractKey);
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        if (modeKey != null && modeKey.consumeClick()) {
            PacketDistributor.sendToServer(new ExtractionModeCyclePayload());
        }

        if (extractKey != null) {
            boolean pressed = extractKey.isDown();
            if (pressed != lastPressed) {
                lastPressed = pressed;
                PacketDistributor.sendToServer(new AltKeyStatePayload(pressed));
            }
        }
    }

    /**
     * Carry On 兼容键是否处于按下状态（供 Jade 预览判断左Alt）。
     */
    public static boolean isExtractKeyPressed() {
        return extractKey != null && extractKey.isDown();
    }
}