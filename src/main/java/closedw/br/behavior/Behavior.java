package closedw.br.behavior;

import net.minecraft.ChatFormatting;

/**
 * 交互行为：取出（空手）、放入（手持物品）、补货（空手）或主动探测（实验性）。
 */
public enum Behavior {

    /** 取出：空手 + 修饰键右击，从容器取物 */
    EXTRACT("extract", ChatFormatting.AQUA),
    /** 放入：手持物品 + 修饰键右击，把物品放进容器对应槽位 */
    DEPOSIT("deposit", ChatFormatting.GOLD),
    /** 补货：空手 + 修饰键右击，从背包向容器输入/燃料槽补充已有同类物品（空槽不补） */
    RESTOCK("restock", ChatFormatting.GREEN),
    /** 主动探测（实验性）：修饰键右击容器，把槽位探测的结果以规则串的形式打印到聊天框 */
    PROBE("probe", ChatFormatting.LIGHT_PURPLE),
    /** 手动指认：修饰键右击容器时<b>不</b>拦截，正常打开 GUI，玩家在界面里标出输入/燃料/输出槽 */
    MAPPING("mapping", ChatFormatting.BLUE);

    private final String name;
    private final ChatFormatting accentColor;

    Behavior(String name, ChatFormatting accentColor) {
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
        return "betterremoval.action." + this.name;
    }

    /**
     * 该行为是否有"槽位模式"可选：取出/放入有，补货和主动探测没有
     * （所以模式滚轮与提示文本都不显示槽位名）。
     */
    public boolean hasSlotMode() {
        return this == EXTRACT || this == DEPOSIT;
    }
}
