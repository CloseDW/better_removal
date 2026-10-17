package closedw.br.container.rule;

import closedw.br.slot.SlotRole;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 槽位声明规则的解析器（纯函数，不依赖任何 Minecraft 类型，便于离线验证）。
 * 推荐格式（{@code better-removal/containers/*.json}，取出与放入分开声明）：
 * {@code
 * {
 *   "containers": [
 *     {
 *       "match": "ad_astra:compressor, @earth.terrarium.adastra...CompressorBlockEntity",
 *       "config": "compressor", "default": true, "size": 3, "sided": false,
 *       "extract": { "input": [1], "output": [2] },
 *       "deposit": { "input": [1] }
 *     }
 *   ]
 * }
 * }
 */
public final class SlotRuleParser {

    private SlotRuleParser() {
    }

    /**
     * 解析规则文件。
     * @return 成功解析出的规则（顺序与文件一致）；文件格式不对时返回空列表
     */
    public static List<SlotRule> parseFile(Reader reader) {
        List<SlotRule> rules = new ArrayList<>();
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        }
        catch (RuntimeException e) {
            // json 语法错误：整份文件按“没有规则”处理，调用方会记一条日志
            return rules;
        }
        if (root == null || !root.isJsonObject()) {
            return rules;
        }
        JsonElement containers = root.getAsJsonObject().get("containers");
        if (containers == null || !containers.isJsonArray()) {
            return rules;
        }
        JsonArray array = containers.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            try {
                rules.addAll(parseRule(element.getAsJsonObject()));
            }
            catch (RuntimeException e) {
                // 单条规则写错（例如槽位号不是数字）只跳过这一条
            }
        }
        return rules;
    }

    /**
     * 解析一条 Configured 字符串规则。第一个词是匹配串，也可以用逗号分隔多个。
     * @return 解析出的规则（可能有多条）；解析失败时返回空列表
     */
    public static List<SlotRule> parseConfigEntry(String raw) {
        List<SlotRule> rules = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return rules;
        }
        String[] tokens = raw.trim().split("\\s+");
        if (tokens.length < 2) {
            return rules;
        }
        List<SlotRule.Assignment> assignments = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            int separator = token.indexOf('=');
            if (separator <= 0) {
                return List.of();
            }
            String rawRole = token.substring(0, separator);
            String rawSlots = token.substring(separator + 1);
            SlotRole role = roleFor(rawRole);
            if (role == null) {
                return List.of();
            }
            List<Integer> slots = parseSlots(rawSlots);
            if (slots == null && !rawSlots.trim().equals("*")) {
                return List.of();
            }
            if (slots != null && slots.isEmpty()) {
                // "input=" 这种空槽位表是写错了，整条规则作废，而不是当成"什么都不声明"
                return List.of();
            }
            assignments.add(new SlotRule.Assignment(role, slots));
        }
        if (assignments.isEmpty()) {
            return rules;
        }
        // 字符串格式没有「取出/放入分开」的表达能力，两个方向共用同一份槽位表
        SlotRule.DeclaredSlots shared = new SlotRule.DeclaredSlots(List.copyOf(assignments));
        for (String part : tokens[0].split(",")) {
            String entry = part.trim().toLowerCase(Locale.ROOT);
            if (!entry.isEmpty()) {
                rules.add(new SlotRule(entry, null, null, true, 0, false, shared, shared));
            }
        }
        return rules;
    }

    /**
     * 解析一条 JSON 规则。{@code match} 可以用逗号分隔多个匹配串，一次性覆盖多个方块。
     */
    private static List<SlotRule> parseRule(JsonObject object) {
        List<SlotRule> rules = new ArrayList<>();
        String match = asString(object.get("match"));
        if (match == null || match.isBlank()) {
            return rules;
        }
        String configKey = asString(object.get("config"));
        boolean defaultEnabled = asBoolean(object.get("default"), true);
        int declaredSize = asInt(object.get("size"), 0);
        boolean sided = asBoolean(object.get("sided"), false);
        String group = asString(object.get("group"));

        JsonElement nestedExtract = object.get("extract");
        JsonElement nestedDeposit = object.get("deposit");
        SlotRule.DeclaredSlots extract;
        SlotRule.DeclaredSlots deposit;
        if (nestedExtract != null || nestedDeposit != null) {
            // 新格式：两个方向分开声明；某个方向缺失时按其空表处理（该方向不支持）
            extract = parseDirection(nestedExtract);
            deposit = parseDirection(nestedDeposit);
        }
        else {
            // 旧格式：角色直接写在规则对象上，两个方向共用（保持对已有 json 的兼容）
            SlotRule.DeclaredSlots shared = parseDirection(object);
            extract = shared;
            deposit = shared;
        }
        if (extract.isEmpty() && deposit.isEmpty()) {
            return rules;
        }
        // match 原样保留（可以含逗号分隔的多个候选），由 SlotRules.matchesEntry 逐个尝试。
        rules.add(new SlotRule(match, configKey, group, defaultEnabled, declaredSize, sided, extract, deposit));
        return rules;
    }

    /**
     * 解析一个方向的槽位声明对象：{@code { "input": [0, 1], "output": "*" }}。
     * 对象缺失或没有任何角色键时返回空表。
     */
    private static SlotRule.DeclaredSlots parseDirection(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return SlotRule.DeclaredSlots.EMPTY;
        }
        JsonObject object = element.getAsJsonObject();
        List<SlotRule.Assignment> assignments = new ArrayList<>();
        // JsonObject 保留书写顺序，正好用作“后面的覆盖前面的”语义
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            SlotRole role = roleFor(entry.getKey());
            if (role == null) {
                continue;
            }
            assignments.add(new SlotRule.Assignment(role, parseSlots(entry.getValue())));
        }
        if (assignments.isEmpty()) {
            return SlotRule.DeclaredSlots.EMPTY;
        }
        return new SlotRule.DeclaredSlots(List.copyOf(assignments));
    }

    /**
     * 解析槽位列表：字符串 {@code "*"} 返回 null（表示全部槽位），数字返回单槽，数组返回其中的槽位号。
     * 其它形式（非 {@code "*"} 的字符串、布尔、null、非数组对象）都是非法声明，抛异常让调用方跳过整条规则
     * ——绝不能把非法值当成“全部槽位”。
     */
    private static List<Integer> parseSlots(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("slot value must not be null");
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) {
                if (element.getAsString().trim().equals("*")) {
                    return null;
                }
                throw new IllegalArgumentException("invalid slot value: " + element.getAsString());
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return List.of(element.getAsInt());
            }
            throw new IllegalArgumentException("invalid slot value: " + element);
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("invalid slot value: " + element);
        }
        List<Integer> slots = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) {
                continue;
            }
            slots.add(item.getAsInt());
        }
        return slots;
    }

    /**
     * 字符串形式的槽位列表解析；{@code "*"} 返回 null（表示全部槽位），解析失败也返回 null。
     */
    private static List<Integer> parseSlots(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.equals("*")) {
            return null;
        }
        List<Integer> slots = new ArrayList<>();
        if (value.isEmpty()) {
            return slots;
        }
        for (String part : value.split(",")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            try {
                slots.add(Integer.parseInt(item));
            }
            catch (NumberFormatException e) {
                return null;
            }
        }
        return slots;
    }

    private static SlotRole roleFor(String key) {
        if (key == null) {
            return null;
        }
        return switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "input" -> SlotRole.INPUT;
            case "fuel" -> SlotRole.FUEL;
            case "output" -> SlotRole.OUTPUT;
            case "ignore", "none" -> SlotRole.NONE;
            default -> null;
        };
    }

    private static String asString(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static boolean asBoolean(JsonElement element, boolean fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static int asInt(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return element.getAsInt();
    }
}
