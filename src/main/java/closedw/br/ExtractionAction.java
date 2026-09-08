package closedw.br;

import net.minecraft.util.Formatting;

/**
 * 交互行为：取出（空手）、放入（手持物品）或补货（空手）。
 */
public enum ExtractionAction {

	/** 取出：空手 + 修饰键右击，从容器取物 */
	EXTRACT("extract", Formatting.AQUA),
	/** 放入：手持物品 + 修饰键右击，把物品放进容器对应槽位 */
	DEPOSIT("deposit", Formatting.GOLD),
	/** 补货：空手 + 修饰键右击，从背包向容器输入/燃料槽补充已有同类物品（空槽不补） */
	RESTOCK("restock", Formatting.GREEN);

	private final String name;
	private final Formatting accentColor;

	ExtractionAction(String name, Formatting accentColor) {
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
		return "better-removal.action." + this.name;
	}
}
