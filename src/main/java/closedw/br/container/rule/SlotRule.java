package closedw.br.container.rule;

import closedw.br.slot.SlotRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条槽位声明规则：把「某个方块的每个槽位是什么角色」写成数据。
 * 刻意不依赖任何 Minecraft 类型，方便离线验证解析结果。由 {@link SlotRuleParser} 从
 * JSON 文件或 Configured 字符串解析得到，由 {@link SlotRuleRegistry} 统一登记与自检。
 * {@code match} 的语法：逗号分隔多个匹配串，命中任意一个即匹配（忽略大小写）。
 */
public final class SlotRule {

    /**
     * 一次角色赋值。{@code slots} 为 null 表示「该容器的全部槽位」。
     * 顺序有意义：同一个槽位被多次声明时，后面的覆盖前面的
     * （便于写「全部输出，但 5 号槽忽略」＝ {@code output="*"} 后再 {@code ignore=[5]}）。
     */
    public record Assignment(SlotRole role, List<Integer> slots) {
    }

    /**
     * 一个方向的槽位声明：保留赋值顺序，按容器尺寸延迟求值。
     */
    public record DeclaredSlots(List<Assignment> assignments) {

        public static final DeclaredSlots EMPTY = new DeclaredSlots(List.of());

        public boolean isEmpty() {
            return this.assignments.isEmpty();
        }

        /**
         * 生成角色表：没有声明到的槽位一律为 {@link SlotRole#NONE}，越界槽位号忽略。
         * 遍历顺序即覆盖顺序，后写入的角色胜出。
         */
        public SlotRole[] toRoles(int size) {
            SlotRole[] roles = new SlotRole[Math.max(0, size)];
            java.util.Arrays.fill(roles, SlotRole.NONE);
            for (Assignment assignment : this.assignments) {
                if (assignment.slots() == null) {
                    java.util.Arrays.fill(roles, assignment.role());
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

        /**
         * 该方向下属于某个槽位集合的槽位号：取角色表里落在 {@code wanted} 中的槽位。
         * @return 有序去重的槽位号
         */
        public int[] indicesFor(int size, java.util.Set<SlotRole> wanted) {
            SlotRole[] roles = toRoles(size);
            java.util.LinkedHashSet<Integer> result = new java.util.LinkedHashSet<>();
            for (int slot = 0; slot < roles.length; slot++) {
                if (wanted.contains(roles[slot])) {
                    result.add(slot);
                }
            }
            int[] slots = new int[result.size()];
            int cursor = 0;
            for (int slot : result) {
                slots[cursor++] = slot;
            }
            return slots;
        }
    }

    private final String match;
    private final String configKey;
    private final String group;
    private final boolean defaultEnabled;
    private final int declaredSize;
    private final boolean sided;
    private final DeclaredSlots extract;
    private final DeclaredSlots deposit;

    public SlotRule(String match, String configKey, String group, boolean defaultEnabled, int declaredSize, boolean sided,
            DeclaredSlots extract, DeclaredSlots deposit) {
        this.match = match;
        this.configKey = configKey;
        this.group = group;
        this.defaultEnabled = defaultEnabled;
        this.declaredSize = declaredSize;
        this.sided = sided;
        this.extract = extract == null ? DeclaredSlots.EMPTY : extract;
        this.deposit = deposit == null ? DeclaredSlots.EMPTY : deposit;
    }

    /** 规则匹配串（可能含逗号分隔的多个匹配项）。 */
    public String match() {
        return this.match;
    }

    /** 该规则对应的 Configured 配置键；为 null 表示这条规则没有独立开关。 */
    public String configKey() {
        return this.configKey;
    }

    /**
     * 该规则在 Configured「容器开关」里所属的分组名；为 null 表示由规则的命名空间推断。
     * 分组名同时用作 {@code config.betterremoval.category.<分组>} 的翻译键。
     */
    public String group() {
        return this.group;
    }

    /** 配置键的默认值（仅在 {@link #configKey()} 不为 null 时有意义）。 */
    public boolean defaultEnabled() {
        return this.defaultEnabled;
    }

    /** 规则声明的槽位总数；0 表示未声明（跳过越界自检）。 */
    public int declaredSize() {
        return this.declaredSize;
    }

    /** 取出时是否要额外经过 canTakeItemThroughFace 校验。 */
    public boolean sided() {
        return this.sided;
    }

    /** 「取出」方向的槽位声明。 */
    public DeclaredSlots extract() {
        return this.extract;
    }

    /** 「放入」方向的槽位声明。 */
    public DeclaredSlots deposit() {
        return this.deposit;
    }

    public boolean isEmpty() {
        return this.match == null || this.match.isBlank();
    }

    /** 本规则声明过的最大槽位号 +1（两个方向取大者）；用于启动自检。 */
    public int highestSlot() {
        int highest = -1;
        for (DeclaredSlots declared : List.of(this.extract, this.deposit)) {
            for (Assignment assignment : declared.assignments()) {
                if (assignment.slots() == null) {
                    continue;
                }
                for (int slot : assignment.slots()) {
                    highest = Math.max(highest, slot);
                }
            }
        }
        return highest + 1;
    }

    /** 本规则声明过的全部槽位号（去重、仅用于自检告警）。 */
    public List<Integer> declaredSlots() {
        List<Integer> slots = new ArrayList<>();
        for (DeclaredSlots declared : List.of(this.extract, this.deposit)) {
            for (Assignment assignment : declared.assignments()) {
                if (assignment.slots() == null) {
                    continue;
                }
                for (int slot : assignment.slots()) {
                    if (!slots.contains(slot)) {
                        slots.add(slot);
                    }
                }
            }
        }
        return slots;
    }

    /** 本规则是否用了 {@code *}（全部槽位）声明。 */
    public boolean wildcard() {
        for (DeclaredSlots declared : List.of(this.extract, this.deposit)) {
            for (Assignment assignment : declared.assignments()) {
                if (assignment.slots() == null) {
                    return true;
                }
            }
        }
        return false;
    }
}
