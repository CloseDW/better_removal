package closedw.br.container.rule;

import closedw.br.OutputSlotExtractor;
import closedw.br.slot.SlotRole;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 槽位规则的查询表：把 {@link SlotRules} 读出的文件规则与 Configured 里的规则合并成
 * 「方块 → 规则」的一张表，并负责启动自检与重载通知。
 * 声明式规则不受实验性总开关约束：规则是显式声明，不是猜测。
 * 规则自身的开关走它自带的 {@link SlotRule#configKey()}。
 */
public final class SlotRuleRegistry {

    /** Configured 中的规则列表键。 */
    private static final String CONFIG_KEY = "slot_rules";
    /** 旧版本使用的规则列表键，继续读取以兼容老配置。 */
    private static final String LEGACY_CONFIG_KEY = "experimental_slot_rules";

    /** Configured 规则的解析缓存：内容没变就不重复解析。 */
    private static volatile String cachedConfigContent = "";
    private static volatile List<SlotRule> cachedConfigRules = List.of();

    /** 合并后的规则表（Configured 优先，其次是文件来源）。 */
    private static volatile List<SlotRule> merged = List.of();

    /** 重载完成后的通知者（配置层据此重建 Configured 分组与默认值）。 */
    private static final List<Consumer<Integer>> RELOAD_LISTENERS = new ArrayList<>();

    private SlotRuleRegistry() {
    }

    /**
     * 读取全部来源的规则并做自检。启动时调用一次；重复调用无副作用（文件来源带缓存）。
     */
    public static synchronized void load() {
        rebuild();
    }

    /**
     * 重新读取全部来源（由 {@code /br reload} 调用）：改完 json 或配置后不用重启游戏。
     * @return 重新读取后的规则总数
     */
    public static synchronized int reload() {
        SlotRules.invalidate();
        cachedConfigContent = "";
        cachedConfigRules = List.of();
        int count = rebuild();
        for (Consumer<Integer> listener : List.copyOf(RELOAD_LISTENERS)) {
            try {
                listener.accept(count);
            }
            catch (Throwable t) {
                // 单个监听者失败不影响规则本身
            }
        }
        return count;
    }

    /** 注册重载通知者。 */
    public static synchronized void addReloadListener(Consumer<Integer> listener) {
        if (listener != null) {
            RELOAD_LISTENERS.add(listener);
        }
    }

    private static int rebuild() {
        List<SlotRule> rules = new ArrayList<>(configRules());
        rules.addAll(SlotRules.modRules());
        merged = List.copyOf(rules);
        validate(merged);
        return merged.size();
    }

    /**
     * 找出命中该方块的第一条规则；没有则返回 null。
     */
    public static SlotRule find(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return null;
        }
        for (SlotRule rule : merged) {
            if (SlotRules.matchesEntry(blockEntity, rule.match())) {
                return rule;
            }
        }
        return null;
    }

    /** 规则声明的全部配置键（键 → 默认值），供配置层生成 Configured 分组。 */
    public static Map<String, Boolean> configKeys() {
        Map<String, Boolean> keys = new LinkedHashMap<>();
        for (SlotRule rule : merged) {
            if (rule.configKey() != null && !rule.configKey().isBlank()) {
                keys.putIfAbsent(rule.configKey(), rule.defaultEnabled());
            }
        }
        return keys;
    }

    /**
     * 规则声明的配置键 → 所属分组，供配置层把开关分到对应的模组分组。
     * 优先用规则显式声明的 {@code group}；没写时才从 match 的命名空间推断。
     */
    public static Map<String, String> configGroups() {
        Map<String, String> groups = new LinkedHashMap<>();
        for (SlotRule rule : merged) {
            if (rule.configKey() == null || rule.configKey().isBlank()) {
                continue;
            }
            String group = rule.group();
            if (group == null || group.isBlank()) {
                group = namespaceOf(rule.match());
            }
            groups.putIfAbsent(rule.configKey(), group.trim().toLowerCase(Locale.ROOT));
        }
        return groups;
    }

    /**
     * 从匹配串推断模组命名空间：取第一个形如 {@code 命名空间:路径} 的匹配项的冒号前部分。
     * {@code @类名前缀} 与通配符推不出命名空间，归到 {@code rules}。
     */
    private static String namespaceOf(String match) {
        if (match == null) {
            return "rules";
        }
        for (String part : match.split(",")) {
            String entry = part.trim().toLowerCase(Locale.ROOT);
            if (entry.startsWith("@")) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon > 0) {
                return entry.substring(0, colon);
            }
        }
        return "rules";
    }

    /**
     * 按规则解析某个容器在给定槽位集合下的槽位号。
     * @param direction 取出还是放入
     * @param wanted    该槽位集合对应的角色集合
     * @return 具体槽位号；规则没声明到任何槽位时返回空数组
     */
    public static int[] slotsFor(BlockEntity blockEntity, SlotRule rule, RuleDirection direction, Set<SlotRole> wanted) {
        if (rule == null || !(blockEntity instanceof Container container)) {
            return new int[0];
        }
        int size;
        try {
            size = container.getContainerSize();
        }
        catch (Throwable t) {
            return new int[0];
        }
        SlotRule.DeclaredSlots declared = direction == RuleDirection.EXTRACT ? rule.extract() : rule.deposit();
        return declared.indicesFor(size, wanted);
    }

    /** 规则声明尺寸与真实容器尺寸是否一致（用于自检告警）。 */
    public static String sizeMismatch(BlockEntity blockEntity, SlotRule rule) {
        if (rule.declaredSize() <= 0 || !(blockEntity instanceof Container container)) {
            return null;
        }
        try {
            int actual = container.getContainerSize();
            if (actual != rule.declaredSize()) {
                return "rule declares size=" + rule.declaredSize() + " but the container has " + actual + " slot(s)";
            }
        }
        catch (Throwable t) {
            return null;
        }
        return null;
    }

    /**
     * Configured 里的规则：每次查询都会看一眼配置是否变化，变了才重新解析。
     */
    private static List<SlotRule> configRules() {
        List<String> entries = OutputSlotExtractor.getConfigList(CONFIG_KEY);
        if (entries.isEmpty()) {
            // 兼容旧键名
            entries = OutputSlotExtractor.getConfigList(LEGACY_CONFIG_KEY);
        }
        String content = String.join("\n", entries);
        if (content.equals(cachedConfigContent)) {
            return cachedConfigRules;
        }
        List<SlotRule> rules = new ArrayList<>();
        for (String entry : entries) {
            List<SlotRule> parsed = SlotRuleParser.parseConfigEntry(entry);
            if (parsed.isEmpty()) {
                SlotRules.logProblem("malformed entry in config " + CONFIG_KEY + ", ignored: " + entry);
                continue;
            }
            rules.addAll(parsed);
        }
        cachedConfigContent = content;
        cachedConfigRules = List.copyOf(rules);
        return cachedConfigRules;
    }

    // 启动自检
    /**
     * 规则自检：目的是让「json 写错了」在启动日志里就能看到，而不是等到玩家右击容器时静默无事发生。
     * 这里只做不依赖游戏注册表的检查（配置键重复、槽位越界、声明了 group 却没有开关）。
     */
    private static void validate(List<SlotRule> rules) {
        Set<String> configKeys = new LinkedHashSet<>();
        for (SlotRule rule : rules) {
            warnAboutConfigKeyDuplication(rule, configKeys);
            warnAboutOutOfRangeSlots(rule);
            warnAboutMissingConfigKey(rule);
        }
    }

    /**
     * 规则声明了分组，却没有配置键：那条规则在 Configured 里不会有开关，玩家既看不见也关不掉它。
     */
    private static void warnAboutMissingConfigKey(SlotRule rule) {
        if (rule.configKey() != null && !rule.configKey().isBlank()) {
            return;
        }
        if (rule.group() != null && !rule.group().isBlank()) {
            SlotRules.logProblem("rule match=" + rule.match() + " declares group \"" + rule.group()
                    + "\" but has no config key, so it gets no switch in Configured and cannot be turned off");
        }
    }

    /**
     * 匹配串里的方块 ID 在注册表里不存在：几乎一定是写错了（拼写、命名空间、方块 ID 变更），
     * 或者那个模组没装。这是数据驱动的主要风险——写错不会报错，只会静默不生效——所以要喊出来。
     * 只能在注册表冻结之后调用（见 {@link #revalidateBlockIds()}）。
     */
    private static void warnAboutUnknownBlocks(SlotRule rule) {
        String match = rule.match();
        if (match == null) {
            return;
        }
        for (String part : match.split(",")) {
            String entry = part.trim().toLowerCase(Locale.ROOT);
            // 只检查"命名空间:路径"这种明确的方块 ID；通配符与 @类名前缀无法校验
            if (entry.isEmpty() || entry.startsWith("@") || entry.startsWith("*") || entry.endsWith(":*")) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id == null) {
                SlotRules.logProblem("malformed block id in rule: " + entry + " (match=" + match + ")");
                continue;
            }
            boolean present;
            try {
                present = BuiltInRegistries.BLOCK.containsKey(id);
            }
            catch (Throwable t) {
                continue;
            }
            if (!present) {
                SlotRules.logProblem("block " + id + " in rule does not exist (match=" + match
                        + "). This is harmless if the mod is simply not installed; otherwise the block id is wrong"
                        + " (Active Probe prints the correct one)");
            }
        }
    }

    /** 同一个配置键被多条规则使用：后注册的规则会覆盖前一条的开关语义。 */
    private static void warnAboutConfigKeyDuplication(SlotRule rule, Set<String> seen) {
        String key = rule.configKey();
        if (key == null || key.isBlank()) {
            return;
        }
        if (!seen.add(key)) {
            SlotRules.logProblem("config key " + key + " is used by several rules (match=" + rule.match()
                    + "), so their switches overwrite each other");
        }
    }

    /** 槽位号超出规则自己声明的 size，或者规则没有任何槽位：都是写错了。 */
    private static void warnAboutOutOfRangeSlots(SlotRule rule) {
        if (rule.wildcard()) {
            // 用了 "*"，槽位号是否越界要等运行时才知道
            return;
        }
        int size = rule.declaredSize();
        if (size > 0) {
            for (int slot : rule.declaredSlots()) {
                if (slot >= size) {
                    SlotRules.logProblem("rule match=" + rule.match() + " declares size=" + size
                            + " but uses slot " + slot + " (out-of-range slots are ignored)");
                }
            }
            return;
        }
        if (rule.highestSlot() == 0) {
            SlotRules.logProblem("rule match=" + rule.match() + " declares no slots at all, so it has no effect");
        }
    }

    /** 该容器是否有命中声明式规则（探测层与容器适配器都用它来判断"这个方块归不归规则管"）。 */
    public static boolean hasRule(BlockEntity blockEntity) {
        return find(blockEntity) != null;
    }

    /**
     * 重新跑一遍「方块 ID 是否存在」的校验。
     * 注册表在模组加载阶段可能还没冻结完，所以这条检查由服务器启动事件触发（见 {@code BetterRemoval}）。
     */
    public static synchronized void revalidateBlockIds() {
        for (SlotRule rule : merged) {
            warnAboutUnknownBlocks(rule);
        }
    }
}
