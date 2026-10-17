package closedw.br.slot;

import java.util.Locale;

/**
 * 槽位角色。
 * <p>
 * 两个来源共用：主动探测推断出来的角色，以及玩家在手动指认界面里标出来的角色。
 * 循环顺序是给指认界面用的：输入 → 输出 → 燃料 → 忽略 → 输入。
 */
public enum SlotRole {
    /** 只能取出（GUI 里不允许放入）的槽位 */
    OUTPUT,
    /** 可以放入的常规输入槽 */
    INPUT,
    /** 可以放入、且只接受燃料的槽位 */
    FUEL,
    /** 无法判断或与取出/放入无关（升级槽、展示槽、只认特定物品的受限槽位等） */
    NONE;

    /** 滚轮上滚一档：输入 → 输出 → 燃料 → 忽略 → 输入。 */
    public SlotRole next() {
        return switch (this) {
            case INPUT -> OUTPUT;
            case OUTPUT -> FUEL;
            case FUEL -> NONE;
            case NONE -> INPUT;
        };
    }

    /** 滚轮下滚一档：输入 → 忽略 → 燃料 → 输出 → 输入。 */
    public SlotRole previous() {
        return switch (this) {
            case INPUT -> NONE;
            case NONE -> FUEL;
            case FUEL -> OUTPUT;
            case OUTPUT -> INPUT;
        };
    }

    /** 翻译键，与 {@code betterremoval.slot.*} 文案对应。 */
    public String getTranslationKey() {
        return "betterremoval.slot." + this.name().toLowerCase(Locale.ROOT);
    }
}
