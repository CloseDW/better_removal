package closedw.br.experimental;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 槽位声明规则的解析器（纯函数，不依赖任何 Minecraft 类型，便于离线验证）。
 * 文件格式（{@code better-removal/containers/*.json}）：
 * {@code
 * {
 *   "containers": [
 *     { "match": "ironfurnaces", "input": [0], "fuel": [1], "output": [2], "ignore": [3, 4, 5] },
 *     { "match": "somemod:trash_can", "input": "*", "output": "*" }
 *   ]
 * }
 * }
 */
final class SlotRuleParser {

	private SlotRuleParser() {
	}

	/**
	 * 解析规则文件。
	 *
	 * @return 成功解析出的规则（顺序与文件一致）；文件格式不对时返回空列表
	 */
	static List<SlotRule> parseFile(Reader reader) {
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
	 *
	 * @return 解析出的规则（可能有多条）；解析失败时返回空列表
	 */
	static List<SlotRule> parseConfigEntry(String raw) {
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
			SlotRole role = roleFor(token.substring(0, separator));
			if (role == null) {
				return List.of();
			}
			List<Integer> slots = parseSlots(token.substring(separator + 1));
			if (slots == null && !token.substring(separator + 1).trim().equals("*")) {
				return List.of();
			}
			assignments.add(new SlotRule.Assignment(role, slots));
		}
		if (assignments.isEmpty()) {
			return rules;
		}
		for (String part : tokens[0].split(",")) {
			String entry = part.trim().toLowerCase(Locale.ROOT);
			if (!entry.isEmpty()) {
				rules.add(new SlotRule(entry, assignments));
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
			return rules;
		}
		for (String part : match.split(",")) {
			String entry = part.trim().toLowerCase(Locale.ROOT);
			if (!entry.isEmpty()) {
				rules.add(new SlotRule(entry, assignments));
			}
		}
		return rules;
	}

	/**
	 * 解析槽位列表：字符串 {@code "*"} 返回 null（表示全部槽位），数组返回其中的槽位号。
	 */
	private static List<Integer> parseSlots(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			if (primitive.isString() && primitive.getAsString().trim().equals("*")) {
				return null;
			}
			if (primitive.isNumber()) {
				return List.of(primitive.getAsInt());
			}
			return null;
		}
		if (!element.isJsonArray()) {
			return null;
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
}
