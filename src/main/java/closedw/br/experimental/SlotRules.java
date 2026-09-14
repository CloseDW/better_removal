package closedw.br.experimental;

import closedw.br.BetterRemoval;
import closedw.br.OutputSlotExtractor;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 槽位声明规则表：用“显式声明”代替“猜测”，是自动探测之外的另一条路。
 * 三个来源，按优先级从高到低：
 * Configured 配置项 {@code experimental_slot_rules}：随时可改，游戏内就能加；
 * 用户文件 {@code config/better-removal/containers/*.json}：不用重编译、不用等模组更新；
 * 模组自带文件 {@code better-removal/containers/*.json}：内置兼容和其它模组的自声明都走这里 ——“支持某个模组”可以只是往 jar 里放一个 json，不需要改任何代码。
 *整体受实验性总开关约束，见 {@link AutoDetectSupport#hasRule}。
 */
public final class SlotRules {

	/** 模组资源目录（jar 内路径，其它模组也用同一个路径自声明）。 */
	private static final String RESOURCE_DIR = "better-removal/containers";
	/** 用户配置目录（相对 config 目录）。 */
	private static final String CONFIG_DIR = "better-removal/containers";
	/** Configured 中的规则列表键。 */
	private static final String CONFIG_KEY = "experimental_slot_rules";

	/** 用户目录缺省生成的示例文件（仅当文件不存在时写入，之后永不覆盖）。 */
	private static final String EXAMPLE_FILE = "example.json";
	private static final String EXAMPLE_CONTENT = """
			{
			  "_comment": "示例模板，不会生效：它匹配一个不存在的方块，仅用于展示格式，可照抄改写成自己的规则。",
			  "containers": [
			    {
			      "match": "examplemod:example_block",
			      "input": [0, 1],
			      "fuel": [2],
			      "output": [3, 4],
			      "ignore": [5]
			    }
			  ]
			}
			""";

	private static final Logger LOGGER = BetterRemoval.LOGGER;

	/** 文件来源的规则（启动时或 /br reload 时读取一次）。 */
	private static volatile List<SlotRule> fileRules = List.of();
	private static volatile boolean loaded;

	/** Configured 规则的解析缓存：内容没变就不重复解析。 */
	private static volatile String cachedConfigContent = "";
	private static volatile List<SlotRule> cachedConfigRules = List.of();

	/** 只在内容变化时打印一次日志，避免刷屏。 */
	private static final Set<String> LOGGED_PROBLEMS = ConcurrentHashMap.newKeySet();

	private SlotRules() {
	}

	/**
	 * 读取文件来源的规则。启动时调用一次即可，重复调用无副作用。
	 */
	public static synchronized void load() {
		if (loaded) {
			return;
		}
		List<SlotRule> rules = new ArrayList<>();
		// 先读用户文件、再读模组自带文件：先读到的优先，这样用户可以用自己的文件覆盖内置规则
		loadConfigDirectory(rules);
		loadModResources(rules);
		fileRules = List.copyOf(rules);
		loaded = true;
		LOGGER.info("[auto-detect] 槽位规则：文件来源共 {} 条", fileRules.size());
	}

	/**
	 * 重新读取（连接 /br reload）：让用户改完 json/配置后不用重启游戏。
	 * @return 重新读取后的规则总数（含 Configured 里的规则）
	 */
	public static synchronized int reload() {
		loaded = false;
		fileRules = List.of();
		cachedConfigContent = "";
		cachedConfigRules = List.of();
		load();
		return fileRules.size() + configRules().size();
	}

	/**
	 * 找出命中该方块的第一条规则。
	 */
	static SlotRule find(BlockEntity blockEntity) {
		if (blockEntity == null) {
			return null;
		}
		for (SlotRule rule : configRules()) {
			if (matchesEntry(blockEntity, rule.match())) {
				return rule;
			}
		}
		load();
		for (SlotRule rule : fileRules) {
			if (matchesEntry(blockEntity, rule.match())) {
				return rule;
			}
		}
		return null;
	}

	/**
	 * 匹配串语法（忽略大小写），与自动探测白名单完全一致：
	 *{@code 模组ID:方块ID} → 精确匹配某个方块；
	 */
	static boolean matchesEntry(BlockEntity blockEntity, String rawEntry) {
		if (blockEntity == null || rawEntry == null) {
			return false;
		}
		String entry = rawEntry.trim().toLowerCase(Locale.ROOT);
		if (entry.isEmpty()) {
			return false;
		}
		if (entry.equals("*")) {
			return true;
		}
		if (entry.startsWith("@")) {
			return blockEntity.getClass().getName().toLowerCase(Locale.ROOT).startsWith(entry.substring(1));
		}
		Identifier id = blockId(blockEntity);
		if (id == null) {
			return false;
		}
		if (entry.equals(id.getNamespace()) || entry.equals(id.getNamespace() + ":*")) {
			return true;
		}
		return entry.equals(id.toString());
	}

	static Identifier blockId(BlockEntity blockEntity) {
		try {
			return Registries.BLOCK.getId(blockEntity.getCachedState().getBlock());
		}
		catch (Throwable t) {
			return null;
		}
	}

	/**
	 * Configured 里的规则：每次查询都会看一眼配置是否变化，变了才重新解析。
	 */
	private static List<SlotRule> configRules() {
		List<String> entries = OutputSlotExtractor.getConfigList(CONFIG_KEY);
		String content = String.join("\n", entries);
		if (content.equals(cachedConfigContent)) {
			return cachedConfigRules;
		}
		List<SlotRule> rules = new ArrayList<>();
		for (String entry : entries) {
			List<SlotRule> parsed = SlotRuleParser.parseConfigEntry(entry);
			if (parsed.isEmpty()) {
				logProblem("配置项 " + CONFIG_KEY + " 中这一条写错了，已忽略：" + entry);
				continue;
			}
			rules.addAll(parsed);
		}
		cachedConfigContent = content;
		cachedConfigRules = List.copyOf(rules);
		return cachedConfigRules;
	}

	private static void loadModResources(List<SlotRule> out) {
		// 按模组ID排序：加载顺序固定，同名规则谁优先就不会随机变化
		List<ModContainer> mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
		mods.sort(Comparator.comparing(mod -> mod.getMetadata().getId()));
		for (ModContainer mod : mods) {
			String modId = mod.getMetadata().getId();
			Optional<Path> directory;
			try {
				directory = mod.findPath(RESOURCE_DIR);
			}
			catch (Throwable t) {
				continue;
			}
			if (directory.isEmpty()) {
				continue;
			}
			readDirectory(directory.get(), modId + "!" + RESOURCE_DIR, out);
		}
	}

	private static void loadConfigDirectory(List<SlotRule> out) {
		Path directory = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIR);
		try {
			// 先建好目录，玩家才知道该往哪里放文件
			Files.createDirectories(directory);
		}
		catch (IOException e) {
			LOGGER.warn("[auto-detect] 无法创建槽位规则目录 {}: {}", directory, e.toString());
		}
		writeExampleIfMissing(directory);
		readDirectory(directory, "config/" + CONFIG_DIR, out);
	}

	/**
	 * 目录里没有示例文件时写入一份模板（match 是不存在的方块，永远不生效）。
	 * 只生成一次：文件已存在就绝不覆盖，避免冲掉玩家的改动。
	 */
	private static void writeExampleIfMissing(Path directory) {
		Path example = directory.resolve(EXAMPLE_FILE);
		try {
			if (Files.exists(example)) {
				return;
			}
			Files.writeString(example, EXAMPLE_CONTENT, StandardCharsets.UTF_8);
			LOGGER.info("[auto-detect] 已生成示例槽位规则 {}", example);
		}
		catch (IOException e) {
			LOGGER.warn("[auto-detect] 无法生成示例槽位规则 {}: {}", example, e.toString());
		}
	}

	private static void readDirectory(Path directory, String source, List<SlotRule> out) {
		try {
			if (!Files.isDirectory(directory)) {
				return;
			}
			List<Path> files;
			try (Stream<Path> stream = Files.list(directory)) {
				files = stream
						.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
						.sorted()
						.toList();
			}
			for (Path file : files) {
				try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					List<SlotRule> parsed = SlotRuleParser.parseFile(reader);
					out.addAll(parsed);
					LOGGER.info("[auto-detect] 已加载槽位规则 {}/{}（{} 条）", source, file.getFileName(), parsed.size());
				}
				catch (Throwable t) {
					LOGGER.warn("[auto-detect] 槽位规则文件解析失败 {}/{}: {}", source, file.getFileName(), t.toString());
				}
			}
		}
		catch (Throwable t) {
			LOGGER.warn("[auto-detect] 读取槽位规则目录失败 {}: {}", source, t.toString());
		}
	}

	private static void logProblem(String message) {
		if (LOGGED_PROBLEMS.add(message)) {
			LOGGER.warn("[auto-detect] {}", message);
		}
	}
}
