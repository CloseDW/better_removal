package closedw.br.slot;

import net.minecraft.util.Formatting;

/**
 * 取出模式：决定从容器中取出哪些槽位。
 */
public enum SlotSet {

	/** 取出输出槽 */
	OUTPUT("output", Formatting.GREEN),
	/** 取出输入槽 */
	INPUT("input", Formatting.YELLOW),
	/** 取出燃料槽 */
	FUEL("fuel", Formatting.GOLD),
	/** 取出全部槽位 */
	ALL("all", Formatting.LIGHT_PURPLE);

	private final String name;
	private final Formatting accentColor;

	SlotSet(String name, Formatting accentColor) {
		this.name = name;
		this.accentColor = accentColor;
	}

	public String getName() {
		return this.name;
	}

	public Formatting getAccentColor() {
		return this.accentColor;
	}

	public String getTranslationKey() {
		return "better-removal.slot." + this.name;
	}
}