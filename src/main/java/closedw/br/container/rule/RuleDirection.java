package closedw.br.container.rule;

/**
 * 规则的槽位声明方向。
 * 槽位表在 json 里分成 {@code extract} 与 {@code deposit} 两块（因为像 Vinery 苹果压榨器那样
 * 「取出燃料槽 ≠ 放入燃料槽」的布局是存在的），这个枚举就是"该看哪一块"的开关。
 */
public enum RuleDirection {

    /** 取出方向：{@code extract} 槽位表。 */
    EXTRACT,

    /** 放入方向：{@code deposit} 槽位表。 */
    DEPOSIT
}
