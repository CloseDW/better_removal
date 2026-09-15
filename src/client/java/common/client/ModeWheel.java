package common.client;

import common.ExtractionAction;
import common.ExtractionMode;
import common.ExtractionModeManager;
import common.ModeState;
import common.networking.ExtractionModeSetC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 模式选择滚轮：按住模式键时在快捷栏上方显示预设列表，滚轮选择，松开提交。
 * 实验性的"主动探测"开启时，列表末尾会多出第 8 个预设。
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

	private static final ModeState PROBE_PRESET = new ModeState(ExtractionAction.PROBE, ExtractionMode.ALL);

	private static boolean open;
	private static int pendingIndex;
	private static List<ModeState> active = BASE_PRESETS;

	private ModeWheel() {
	}

	public static boolean isOpen() {
		return open;
	}

	private static List<ModeState> presets() {
		if (!ExtractionModeManager.isClientProbeAvailable()) {
			return BASE_PRESETS;
		}
		List<ModeState> list = new ArrayList<>(BASE_PRESETS.size() + 1);
		list.addAll(BASE_PRESETS);
		list.add(PROBE_PRESET);
		return list;
	}

	public static void open() {
		open = true;
		active = presets();
		int current = active.indexOf(ExtractionModeManager.getClientState());
		pendingIndex = current >= 0 ? current : 0;
	}

	public static boolean onScroll(double vertical) {
		if (!open) {
			return false;
		}
		if (vertical != 0) {
			pendingIndex = Math.floorMod(pendingIndex + (vertical > 0 ? -1 : 1), active.size());
		}
		return true;
	}

	public static void commit() {
		if (!open) {
			return;
		}
		open = false;
		ModeState preset = active.get(pendingIndex);
		if (!preset.equals(ExtractionModeManager.getClientState())) {
			ClientPlayNetworking.send(new ExtractionModeSetC2SPayload(preset.action(), preset.mode()));
		}
	}

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
			ModeState preset = active.get(i);
			boolean selected = i == pendingIndex;
			MutableText line = Text.literal(selected ? "▶ " : "   ")
					.append(Text.translatable(preset.action().getTranslationKey()));

			if (preset.action().hasSlotMode()) {
				line.append(Text.literal(" "))
						.append(Text.translatable(preset.mode().getTranslationKey()));
			}
			Formatting accent = preset.action().getAccentColor();
			Integer accentValue = accent.getColorValue();
			int color = selected ? (accentValue == null ? 0xFFFFFFFF : 0xFF000000 | accentValue) : 0xFFA0A0A0;
			context.drawText(font, line, (screenWidth - font.getWidth(line)) / 2, top + i * lineHeight, color, true);
		}
	}
}
