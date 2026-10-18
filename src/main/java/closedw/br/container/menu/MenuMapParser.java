package closedw.br.container.menu;

import closedw.br.slot.SlotRole;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 手动指认映射（{@code better-removal/menu-maps.json}）的解析器。
 * 与 {@code SlotRuleParser} 一样是纯函数，不依赖任何 Minecraft 类型，方便离线验证。
 * 格式：
 * {@code
 * {
 *   "maps": [
 *     { "match": "somemod:crusher, @somemod.common.block.entity.CrusherBlockEntity",
 *       "menuSize": 5, "input": [1, 2], "fuel": [3], "output": [4] }
 *   ]
 * }
 * }
 */
public final class MenuMapParser {

	private MenuMapParser() {
	}

	/**
	 * 解析整份文件。
	 * @return 成功解析出的映射（顺序与文件一致）；格式不对时返回空列表
	 */
	public static List<MenuMap> parseFile(Reader reader) {
		List<MenuMap> maps = new ArrayList<>();
		JsonElement root;
		try {
			root = JsonParser.parseReader(reader);
		}
		catch (RuntimeException e) {
			// json 语法错误：整份文件按"没有映射"处理，调用方会记一条日志
			return maps;
		}
		if (root == null || !root.isJsonObject()) {
			return maps;
		}
		JsonElement entries = root.getAsJsonObject().get("maps");
		if (entries == null || !entries.isJsonArray()) {
			return maps;
		}
		JsonArray array = entries.getAsJsonArray();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			try {
				MenuMap map = parseMap(element.getAsJsonObject());
				if (map != null) {
					maps.add(map);
				}
			}
			catch (RuntimeException e) {
				// 单条写错（例如槽位号不是数字）只跳过这一条
			}
		}
		return maps;
	}

	private static MenuMap parseMap(JsonObject object) {
		String match = asString(object.get("match"));
		if (match == null || match.isBlank()) {
			return null;
		}
		int menuSize = asInt(object.get("menuSize"), 0);
		if (menuSize <= 0) {
			return null;
		}
		Map<SlotRole, List<Integer>> roles = new EnumMap<>(SlotRole.class);
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			SlotRole role = roleFor(entry.getKey());
			if (role == null) {
				continue;
			}
			List<Integer> slots = parseSlots(entry.getValue(), menuSize);
			roles.put(role, slots);
		}
		MenuMap map = new MenuMap(match, menuSize, roles);
		return map.isEmpty() ? null : map;
	}

	/**
	 * 槽位列表：数字或数字数组；越界与非法值都直接丢弃（映射可以容忍漏标，不能容忍标错）。
	 */
	private static List<Integer> parseSlots(JsonElement element, int menuSize) {
		List<Integer> slots = new ArrayList<>();
		if (element == null || element.isJsonNull()) {
			return slots;
		}
		if (element.isJsonArray()) {
			for (JsonElement item : element.getAsJsonArray()) {
				addSlot(slots, item, menuSize);
			}
			return slots;
		}
		addSlot(slots, element, menuSize);
		return slots;
	}

	private static void addSlot(List<Integer> slots, JsonElement element, int menuSize) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return;
		}
		int slot = element.getAsInt();
		if (slot < 0 || slot >= menuSize || slots.contains(slot)) {
			return;
		}
		slots.add(slot);
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

	private static int asInt(JsonElement element, int fallback) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		return element.getAsInt();
	}
}
