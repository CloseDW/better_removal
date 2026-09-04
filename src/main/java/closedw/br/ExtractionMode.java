package closedw.br;

import net.minecraft.ChatFormatting;

/**
 * 取出模式：决定从容器中取出哪些槽位。
 */
public enum ExtractionMode {

    /** 取出输出槽 */
    OUTPUT("output", ChatFormatting.GREEN),
    /** 取出输入槽 */
    INPUT("input", ChatFormatting.YELLOW),
    /** 取出燃料槽 */
    FUEL("fuel", ChatFormatting.GOLD),
    /** 取出全部槽位 */
    ALL("all", ChatFormatting.LIGHT_PURPLE);

    private final String name;
    private final ChatFormatting accentColor;

    ExtractionMode(String name, ChatFormatting accentColor) {
        this.name = name;
        this.accentColor = accentColor;
    }

    public String getName() {
        return this.name;
    }

    public ChatFormatting getAccentColor() {
        return this.accentColor;
    }

    public String getTranslationKey() {
        return "betterremoval.mode." + this.name;
    }

    /**
     * 循环到下一个模式（OUTPUT -> INPUT -> FUEL -> ALL -> OUTPUT ...）
     */
    public ExtractionMode next() {
        ExtractionMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}