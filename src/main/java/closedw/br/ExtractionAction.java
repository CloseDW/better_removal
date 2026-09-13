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
	RESTOCK("restock", Formatting.GREEN),
	/** 主动探测（实验性）：修饰键右击容器，把三次槽位探测的结果以规则 JSON 的形式打印到聊天框 */
	PROBE("probe", Formatting.LIGHT_PURPLE);

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

	/**
	 * 该行为是否有"槽位模式"可选：取出/放入有，补货和主动探测没有
	 * （所以模式滚轮与提示文本都不显示槽位名）。
	 */
	public boolean hasSlotMode() {
		return this == EXTRACT || this == DEPOSIT;
	}
}
