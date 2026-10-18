package closedw.br.container.menu;

import closedw.br.slot.SlotRole;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 一条「手动指认」的槽位映射。
 * 与 {@code SlotRule} 的区别只有一个：这里的槽位号是GUI 菜单槽位索引（玩家在界面里看到的那个槽），
 * 而不是容器自己的槽位号。原因是有一类容器（方块实体没实现原版 {@code Container}，槽位藏在私有
 * {@code ItemStackHandler} 里）只有菜单能寻址到——菜单里的 {@code Slot} 直接持有内部容器的引用。
 * 指认出来的三个角色同时用于取出与放入：输入/燃料可放入，输出只取出（与内置规则的语义一致）。
 */
public final class MenuMap {

    private final String match;
    private final int menuSize;
    private final EnumMap<SlotRole, List<Integer>> roles;

    public MenuMap(String match, int menuSize, Map<SlotRole, ? extends List<Integer>> roles) {
        this.match = match == null ? "" : match.trim();
        this.menuSize = menuSize;
        EnumMap<SlotRole, List<Integer>> copy = new EnumMap<>(SlotRole.class);
        for (SlotRole role : SlotRole.values()) {
            List<Integer> slots = roles == null ? null : roles.get(role);
            copy.put(role, slots == null ? List.of() : List.copyOf(slots));
        }
        this.roles = copy;
    }

    /** 匹配串，语法与声明式规则一致（{@code 模组ID:方块ID}、{@code 模组ID}、{@code @类名前缀}，逗号分隔多候选）。 */
    public String match() {
        return this.match;
    }

    /** 指认时该容器的菜单槽位总数，用于运行时校验布局是否变过。 */
    public int menuSize() {
        return this.menuSize;
    }

    /** 某个角色的菜单槽位索引（有序、已去重）。 */
    public int[] slots(SlotRole role) {
        List<Integer> slots = this.roles.get(role);
        if (slots == null || slots.isEmpty()) {
            return new int[0];
        }
        int[] result = new int[slots.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = slots.get(i);
        }
        return result;
    }

    /** 多个角色的并集（有序、已去重）。 */
    public int[] union(SlotRole... wanted) {
        Set<Integer> merged = new LinkedHashSet<>();
        for (SlotRole role : wanted) {
            List<Integer> slots = this.roles.get(role);
            if (slots != null) {
                merged.addAll(slots);
            }
        }
        int[] result = new int[merged.size()];
        int cursor = 0;
        for (int slot : merged) {
            result[cursor++] = slot;
        }
        return result;
    }

    /** 菜单槽位索引 → 角色，供指认会话与网络回传使用。 */
    public Map<Integer, SlotRole> byMenuIndex() {
        Map<Integer, SlotRole> map = new LinkedHashMap<>();
        for (SlotRole role : SlotRole.values()) {
            for (int slot : slots(role)) {
                map.putIfAbsent(slot, role);
            }
        }
        return map;
    }

    /** 是否一个槽位都没标（这种映射没有意义，会被删除）。 */
    public boolean isEmpty() {
        for (SlotRole role : SlotRole.values()) {
            if (!this.roles.get(role).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 角色的可读列表，仅用于日志。 */
    public String describe() {
        StringBuilder builder = new StringBuilder();
        for (SlotRole role : List.of(SlotRole.INPUT, SlotRole.FUEL, SlotRole.OUTPUT, SlotRole.NONE)) {
            List<Integer> slots = this.roles.get(role);
            if (slots == null || slots.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(role.name().toLowerCase(Locale.ROOT)).append('=');
            for (int i = 0; i < slots.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(slots.get(i));
            }
        }
        return builder.toString();
    }

    /** 匹配串的规范化形式（比较与去重都用它）。 */
    public static String normalize(String match) {
        return match == null ? "" : match.trim().toLowerCase(Locale.ROOT);
    }

    /** 反转 {@link #byMenuIndex()}：把会话里累积的角色表转成各角色的槽位列表。 */
    public static Map<SlotRole, List<Integer>> toRoleLists(Map<Integer, SlotRole> byMenuIndex) {
        Map<SlotRole, List<Integer>> roles = new EnumMap<>(SlotRole.class);
        for (SlotRole role : SlotRole.values()) {
            roles.put(role, new ArrayList<>());
        }
        byMenuIndex.forEach((slot, role) -> {
            if (role != null && role != SlotRole.NONE) {
                roles.get(role).add(slot);
            }
        });
        for (SlotRole role : SlotRole.values()) {
            roles.get(role).sort(Integer::compareTo);
        }
        return roles;
    }
}
