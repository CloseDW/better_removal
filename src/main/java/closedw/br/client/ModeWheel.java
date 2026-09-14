package closedw.br.client;

import closedw.br.ExtractionAction;
import closedw.br.ExtractionMode;
import closedw.br.ExtractionModeManager;
import closedw.br.ModeState;
import closedw.br.networking.ExtractionModeSetPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 模式选择滚轮：按住模式键时在快捷栏上方显示预设列表
 * （取出-输出/输入/燃料/全部、放入-输入/放入-燃料、补货），滚轮选择，松开提交。
 * 实验性的"主动探测"开启时（{@link ExtractionModeManager#isClientProbeAvailable()}），
 * 列表末尾会多出第 8 个预设；关闭时不显示。
 */
public final class ModeWheel {

    private static final List<ModeState> BASE_PRESETS = List.of(
            new ModeState(ExtractionAction.EXTRACT, ExtractionMode.OUTPUT),
            new ModeState(ExtractionAction.EXTRACT, ExtractionMode.INPUT),
            new ModeState(ExtractionAction.EXTRACT, ExtractionMode.FUEL),
            new ModeState(ExtractionAction.EXTRACT, ExtractionMode.ALL),
            new ModeState(ExtractionAction.DEPOSIT, ExtractionMode.INPUT),
            new ModeState(ExtractionAction.DEPOSIT, ExtractionMode.FUEL),
            new ModeState(ExtractionAction.RESTOCK, ExtractionMode.ALL));

    /** 实验性预设：主动探测（不使用槽位模式） */
    private static final ModeState PROBE_PRESET = new ModeState(ExtractionAction.PROBE, ExtractionMode.ALL);

    private static boolean open;
    private static int pendingIndex;
    /** 本次打开时实际显示的预设列表（打开时确定，避免中途配置变化导致索引错位） */
    private static List<ModeState> active = BASE_PRESETS;

    private ModeWheel() {
    }

    public static boolean isOpen() {
        return open;
    }

    /** 当前应显示的预设：主动探测可用时在末尾多一个 */
    private static List<ModeState> presets() {
        if (!ExtractionModeManager.isClientProbeAvailable()) {
            return BASE_PRESETS;
        }
        List<ModeState> list = new ArrayList<>(BASE_PRESETS.size() + 1);
        list.addAll(BASE_PRESETS);
        list.add(PROBE_PRESET);
        return list;
    }

    /** 打开滚轮，待选定位到当前模式 */
    public static void open() {
        open = true;
        active = presets();
        int current = active.indexOf(ExtractionModeManager.getClientState());
        pendingIndex = current >= 0 ? current : 0;
    }

    /**
     * 滚轮选择。返回true表示已消费该滚动事件（阻止其它滚轮行为）。
     */
    public static boolean onScroll(double deltaY) {
        if (!open) {
            return false;
        }
        if (deltaY != 0) {
            // 滚轮向上=上一个，向下=下一个
            pendingIndex = Math.floorMod(pendingIndex + (deltaY > 0 ? -1 : 1), active.size());
        }
        return true;
    }

    /** 提交待选模式（松开时调用），仅在与当前模式不同时才发送到服务端 */
    public static void commit() {
        if (!open) {
            return;
        }
        open = false;
        ModeState preset = active.get(pendingIndex);
        if (!preset.equals(ExtractionModeManager.getClientState())) {
            PacketDistributor.sendToServer(new ExtractionModeSetPayload(preset.action(), preset.mode()));
        }
    }

    /** 在快捷栏上方绘制模式列表 */
    public static void render(GuiGraphics graphics) {
        if (!open) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = graphics.guiWidth();
        int lineHeight = 12;
        int top = graphics.guiHeight() - 59 - active.size() * lineHeight - 4;

        for (int i = 0; i < active.size(); i++) {
            ModeState preset = active.get(i);
            boolean selected = i == pendingIndex;
            MutableComponent line = Component.literal(selected ? "▶ " : "   ")
                    .append(Component.translatable(preset.action().getTranslationKey()));

            if (preset.action().hasSlotMode()) {
                line.append(Component.literal(" "))
                        .append(Component.translatable(preset.mode().getTranslationKey()));
            }
            ChatFormatting accent = preset.action().getAccentColor();
            Integer accentValue = accent.getColor();
            int color = selected ? (accentValue == null ? 0xFFFFFFFF : 0xFF000000 | accentValue) : 0xFFA0A0A0;
            graphics.drawString(font, line, (screenWidth - font.width(line)) / 2, top + i * lineHeight, color, true);
        }
    }
}
