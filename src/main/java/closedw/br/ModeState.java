package closedw.br;

/**
 * 玩家的交互模式：行为（取出/放入）+ 槽位（输出/输入/燃料/全部）。
 */
public record ModeState(ExtractionAction action, ExtractionMode mode) {
	public static final ModeState DEFAULT = new ModeState(ExtractionAction.EXTRACT, ExtractionMode.OUTPUT);
}
