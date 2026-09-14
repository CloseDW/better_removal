package com.better_removal.experimental;

import java.util.Arrays;
import java.util.List;

/**
 * 一条槽位声明规则（纯数据，刻意不依赖任何 Minecraft 类型，方便离线验证解析结果）。
 * 由 {@link SlotRuleParser} 从 JSON 文件或 Configured 字符串解析得到。
 * {@code match} 的语法与自动探测白名单完全一致，见 {@link SlotRules#matchesEntry}：
 * {@code *} / 模组ID / {@code 模组ID:*} / {@code 模组ID:方块ID} / {@code @类名前缀}。
 */
final class SlotRule {

	/**
	 * 一次角色赋值。{@code slots} 为 null 表示“该容器的全部槽位”。
	 * 顺序有意义：同一个槽位被多次声明时，后面的覆盖前面的（便于写“全部输出，但 5 号槽忽略”）。
	 */
	record Assignment(SlotRole role, List<Integer> slots) {
	}

	private final String match;
	private final List<Assignment> assignments;

	SlotRule(String match, List<Assignment> assignments) {
		this.match = match;
		this.assignments = List.copyOf(assignments);
	}

	String match() {
		return this.match;
	}

	boolean isEmpty() {
		return this.assignments.isEmpty();
	}

	/**
	 * 生成角色表：没有声明到的槽位一律为 {@link SlotRole#NONE}，越界的槽位号直接忽略。
	 */
	SlotRole[] apply(int size) {
		SlotRole[] roles = new SlotRole[Math.max(0, size)];
		Arrays.fill(roles, SlotRole.NONE);
		for (Assignment assignment : this.assignments) {
			if (assignment.slots() == null) {
				Arrays.fill(roles, assignment.role());
				continue;
			}
			for (int slot : assignment.slots()) {
				if (slot >= 0 && slot < roles.length) {
					roles[slot] = assignment.role();
				}
			}
		}
		return roles;
	}
}
