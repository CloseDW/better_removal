package closedw.br.experimental;

import closedw.br.OutputSlotExtractor;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 槽位声明规则表：用“显式声明”代替“猜测”，是自动探测之外的另一条路。
 * 三个来源，按优先级从高到低：
 * Configured 配置项 {@code experimental_slot_rules}；
 * 用户文件 {@code config/better-removal/containers/*.json}；
 * 模组自带文件 {@code better-removal/containers/*.json}。
 * 整体受实验性总开关约束，见 {@link AutoDetectSupport#hasRule}。
 */
public final class SlotRules {

    private static final String[] RESOURCE_DIR = { "better-removal", "containers" };
    private static final String RESOURCE_DIR_NAME = "better-removal/containers";
    private static final String CONFIG_DIR = "better-removal/containers";
    private static final String CONFIG_KEY = "experimental_slot_rules";

    private static final String EXAMPLE_FILE = "example.json";
    private static final String EXAMPLE_CONTENT = """
            {
              "_comment": "示例模板，不会生效：它匹配一个不存在的方块，仅用于展示格式，可照抄改写成自己的规则。匹配串忽略大小写，支持 * / 模组ID / 模组ID:* / 模组ID:方块ID / @类名前缀。",
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

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile List<SlotRule> fileRules = List.of();
    private static volatile boolean loaded;

    private static volatile String cachedConfigContent = "";
    private static volatile List<SlotRule> cachedConfigRules = List.of();

    private static final Set<String> LOGGED_PROBLEMS = ConcurrentHashMap.newKeySet();

    private SlotRules() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        List<SlotRule> rules = new ArrayList<>();
        // 先读用户文件、再读模组自带文件：先读到的优先
        loadConfigDirectory(rules);
        loadModResources(rules);
        fileRules = List.copyOf(rules);
        loaded = true;
        LOGGER.info("[auto-detect] 槽位规则：文件来源共 {} 条", fileRules.size());
    }

    public static synchronized int reload() {
        loaded = false;
        fileRules = List.of();
        cachedConfigContent = "";
        cachedConfigRules = List.of();
        load();
        return fileRules.size() + configRules().size();
    }

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
     * 匹配串语法（忽略大小写）：{@code *} / 模组ID / {@code 模组ID:*} / {@code 模组ID:方块ID} / {@code @类名前缀}。
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
        ResourceLocation id = blockId(blockEntity);
        if (id == null) {
            return false;
        }
        if (entry.equals(id.getNamespace()) || entry.equals(id.getNamespace() + ":*")) {
            return true;
        }
        return entry.equals(id.toString());
    }

    static ResourceLocation blockId(BlockEntity blockEntity) {
        try {
            return BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        }
        catch (Throwable t) {
            return null;
        }
    }

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
        var mods = new ArrayList<>(ModList.get().getMods());
        mods.sort(Comparator.comparing(mod -> mod.getModId()));
        for (var mod : mods) {
            String modId = mod.getModId();
            Path directory;
            try {
                directory = mod.getOwningFile().getFile().findResource(RESOURCE_DIR);
            }
            catch (Throwable t) {
                continue;
            }
            if (directory == null) {
                continue;
            }
            readDirectory(directory, modId + "!" + RESOURCE_DIR_NAME, out);
        }
    }

    private static void loadConfigDirectory(List<SlotRule> out) {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIR);
        try {
            Files.createDirectories(directory);
        }
        catch (IOException e) {
            LOGGER.warn("[auto-detect] 无法创建槽位规则目录 {}: {}", directory, e.toString());
        }
        writeExampleIfMissing(directory);
        readDirectory(directory, "config/" + CONFIG_DIR, out);
    }

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
