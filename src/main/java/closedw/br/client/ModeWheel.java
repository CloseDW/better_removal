package closedw.br.client;

import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionMode;
import closedw.br.behavior.InteractionModeManager;
import closedw.br.networking.ExtractionModeSetC2SPacket;
import closedw.br.slot.SlotSet;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.ArrayList;

/**
 * 模式选择滚轮：按住模式键时在快捷栏上方显示预设列表
 * （取出-输出/输入/燃料/全部、放入-输入/放入-燃料、补货），滚轮选择，松开提交。
 * 两个实验性预设按可用性追加在末尾：
 * {@link InteractionModeManager#isClientProbeAvailable()} → 「主动探测」、
 * {@link InteractionModeManager#isClientMappingAvailable()} → 「手动指认」；关闭时不显示。
 */
public final class ModeWheel {

	private static final List<InteractionMode> BASE_PRESETS = List.of(
			new InteractionMode(Behavior.EXTRACT, SlotSet.OUTPUT),
			new InteractionMode(Behavior.EXTRACT, SlotSet.INPUT),
			new InteractionMode(Behavior.EXTRACT, SlotSet.FUEL),
			new InteractionMode(Behavior.EXTRACT, SlotSet.ALL),
			new InteractionMode(Behavior.DEPOSIT, SlotSet.INPUT),
			new InteractionMode(Behavior.DEPOSIT, SlotSet.FUEL),
			new InteractionMode(Behavior.RESTOCK, SlotSet.ALL));

	/** 实验性预设：主动探测（不使用槽位模式） */
	private static final InteractionMode PROBE_PRESET = new InteractionMode(Behavior.PROBE, SlotSet.ALL);
	/** 实验性预设：手动指认（不使用槽位模式） */
	private static final InteractionMode MAPPING_PRESET = new InteractionMode(Behavior.MAPPING, SlotSet.ALL);

	private static boolean open;
	private static int pendingIndex;
	/** 本次打开时实际显示的预设列表（打开时确定，避免中途配置变化导致索引错位） */
	private static List<InteractionMode> active = BASE_PRESETS;

	private ModeWheel() {
	}

	public static boolean isOpen() {
		return open;
	}

	/** 当前应显示的预设：主动探测/手动指认可用时在末尾各多一个 */
	private static List<InteractionMode> presets() {
		boolean probe = InteractionModeManager.isClientProbeAvailable();
		boolean mapping = InteractionModeManager.isClientMappingAvailable();
		if (!probe && !mapping) {
			return BASE_PRESETS;
		}
		List<InteractionMode> list = new ArrayList<>(BASE_PRESETS.size() + 2);
		list.addAll(BASE_PRESETS);
		if (probe) {
			list.add(PROBE_PRESET);
		}
		if (mapping) {
			list.add(MAPPING_PRESET);
		}
		return list;
	}

	/** 打开滚轮，待选定位到当前模式 */
	public static void open() {
		open = true;
		active = presets();
		int current = active.indexOf(InteractionModeManager.getClientState());
		pendingIndex = current >= 0 ? current : 0;
	}

	/**
	 * 滚轮选择。返回true表示已消费该滚动事件（阻止切换快捷栏）。
	 */
	public static boolean onScroll(double vertical) {
		if (!open) {
			return false;
		}
		if (vertical != 0) {
			// 滚轮向上=上一个，向下=下一个
			pendingIndex = Math.floorMod(pendingIndex + (vertical > 0 ? -1 : 1), active.size());
		}
		return true;
	}

	/** 提交待选模式（松开时调用），仅在与当前模式不同时才发送到服务端 */
	public static void commit() {
		if (!open) {
			return;
		}
		open = false;
		InteractionMode preset = active.get(pendingIndex);
		if (!preset.equals(InteractionModeManager.getClientState())) {
			ClientPlayNetworking.send(new ExtractionModeSetC2SPacket(preset.behavior(), preset.slots()));
		}
	}

	/** 在快捷栏上方绘制模式列表 */
	public static void render(DrawContext context) {
		if (!open) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer font = mc.textRenderer;
		int screenWidth = context.getScaledWindowWidth();
		int lineHeight = 12;
		int top = context.getScaledWindowHeight() - 59 - active.size() * lineHeight - 4;

		for (int i = 0; i < active.size(); i++) {
			InteractionMode preset = active.get(i);
			boolean selected = i == pendingIndex;
			MutableText line = Text.literal(selected ? "▶ " : "   ")
					.append(Text.translatable(preset.behavior().getTranslationKey()));

			if (preset.behavior().hasSlotMode()) {
				line.append(Text.literal(" "))
						.append(Text.translatable(preset.slots().getTranslationKey()));
			}
			Formatting accent = preset.behavior().getAccentColor();
			Integer accentValue = accent.getColorValue();
			int color = selected ? (accentValue == null ? 0xFFFFFFFF : 0xFF000000 | accentValue) : 0xFFA0A0A0;
			context.drawText(font, line, (screenWidth - font.getWidth(line)) / 2, top + i * lineHeight, color, true);
		}
	}
}
