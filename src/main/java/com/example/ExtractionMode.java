package com.example;

import net.minecraft.util.Formatting;

/**
 * 取出模式：决定从容器中取出哪些槽位。
 */
public enum ExtractionMode {

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

	ExtractionMode(String name, Formatting accentColor) {
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
		return "better-removal.mode." + this.name;
	}

	/**
	 * 循环到下一个模式（OUTPUT -> INPUT -> FUEL -> ALL -> OUTPUT ...）
	 */
	public ExtractionMode next() {
		ExtractionMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}
}