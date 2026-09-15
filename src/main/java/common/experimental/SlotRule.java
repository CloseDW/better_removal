package common.experimental;

import java.util.Arrays;
import java.util.List;

/**
 * 一条槽位声明规则（纯数据，不依赖任何 Minecraft 类型）。
 */
final class SlotRule {

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
