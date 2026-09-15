package common.experimental;

/**
 * 实验性自动探测推断出的槽位角色。
 */
public enum SlotRole {
	/** 只能取出（GUI 里不允许放入）的槽位 */
	OUTPUT,
	/** 可以放入的常规输入槽 */
	INPUT,
	/** 可以放入、且只接受燃料的槽位 */
	FUEL,
	/** 无法判断或与取出/放入无关的槽位 */
	NONE
}
