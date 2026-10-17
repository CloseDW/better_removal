package closedw.br.behavior;

import closedw.br.slot.SlotSet;

/**
 * 玩家的交互模式：行为（取出/放入/补货/主动探测）+ 槽位（输出/输入/燃料/全部）。
 */
public record InteractionMode(Behavior behavior, SlotSet slots) {
    public static final InteractionMode DEFAULT = new InteractionMode(Behavior.EXTRACT, SlotSet.OUTPUT);
}
