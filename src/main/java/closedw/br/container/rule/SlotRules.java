package closedw.br.container.rule;

import closedw.br.BetterRemoval;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.BufferedReader;
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
 * 槽位规则的加载：把规则从两个来源读出来，查询与自检在 {@link SlotRuleRegistry}。
 * Configured 配置项 {@code slot_rules}：随时可改，游戏内就能加（读取在 {@link SlotRuleRegistry}）；
 * 模组自带文件 {@code better-removal/containers/*.json}：内置兼容与其它模组的自声明都走这里。
 * 声明式规则不受实验性总开关约束：规则是显式声明，不是猜测。
 */
public final class SlotRules {

    /** 模组资源目录（jar 内路径，其它模组也用同一个路径自声明）。 */
    private static final String[] RESOURCE_DIR = { "better-removal", "containers" };

    /** 资源目录的可读名字（仅日志用）。 */
    private static final String RESOURCE_DIR_NAME = "better-removal/containers";

    /** 旧版本用过、现已废弃的用户规则目录（相对 config 目录）。 */
    private static final String LEGACY_CONFIG_DIR = "better-removal/containers";

    private static final Logger LOGGER = BetterRemoval.LOGGER;

    /** 模组自带文件里的规则（启动时或 /br reload 时读取一次）。 */
    private static volatile List<SlotRule> modRules = List.of();
    private static volatile boolean loaded;

    /** 只在内容变化时打印一次日志，避免刷屏。 */
    private static final Set<String> LOGGED_PROBLEMS = ConcurrentHashMap.newKeySet();

    private SlotRules() {
    }

    /**
     * 读取模组自带文件里的规则。启动时调用一次即可，重复调用无副作用。
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        List<SlotRule> rules = new ArrayList<>();
        loadModResources(rules);
        modRules = List.copyOf(rules);
        loaded = true;
        LOGGER.info("[container] slot rules: {} loaded from mod-provided files", modRules.size());
        warnAboutLegacyConfigDirectory();
    }

    /**
     * 丢弃缓存，下次读取时重新读盘（由 {@link SlotRuleRegistry#reload()} 调用）。
     */
    static synchronized void invalidate() {
        loaded = false;
        modRules = List.of();
    }

    /**
     * 模组自带文件里的规则（不含 Configured 里的规则）。
     */
    static List<SlotRule> modRules() {
        load();
        return modRules;
    }

    /**
     * 旧版本支持过「玩家自己的 json 文件」这条路，现已废弃：规则请写在 Configured 的
     * {@code slot_rules} 里，或由模组自带 json 提供。
     */
    private static void warnAboutLegacyConfigDirectory() {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(LEGACY_CONFIG_DIR);
        try {
            if (!Files.isDirectory(directory)) {
                return;
            }
            boolean any;
            try (Stream<Path> stream = Files.list(directory)) {
                any = stream.anyMatch(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"));
            }
            if (any) {
                logProblem("json rules under config/" + LEGACY_CONFIG_DIR + " are no longer read; move them into the"
                        + " Configured \"Declared container slot rules\" list instead (that directory can be deleted)");
            }
        }
        catch (Throwable t) {
            // 检查失败不影响规则加载
        }
    }

    /**
     * 匹配串语法（忽略大小写）：
     * {@code *} / 模组ID / {@code 模组ID:*} / {@code 模组ID:方块ID} / {@code @类名前缀}。
     * 逗号分隔多个匹配项，命中任意一个即算匹配。
     */
    public static boolean matchesEntry(BlockEntity blockEntity, String rawEntry) {
        if (blockEntity == null || rawEntry == null) {
            return false;
        }
        for (String part : rawEntry.split(",")) {
            if (matchesSingleEntry(blockEntity, part)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSingleEntry(BlockEntity blockEntity, String rawEntry) {
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

    /** 该方块实体的方块 ID；取不到时返回 null（例如方块实体不在世界里）。 */
    public static ResourceLocation blockId(BlockEntity blockEntity) {
        try {
            return BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static void loadModResources(List<SlotRule> out) {
        // 按模组ID排序：加载顺序固定，同名规则谁优先就不会随机变化
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
                    LOGGER.info("[container] loaded slot rules from {}/{} ({} rule(s))", source, file.getFileName(), parsed.size());
                }
                catch (Throwable t) {
                    LOGGER.warn("[container] failed to parse slot rule file {}/{}: {}", source, file.getFileName(), t.toString());
                }
            }
        }
        catch (Throwable t) {
            LOGGER.warn("[container] failed to read slot rule resources {}: {}", source, t.toString());
        }
    }

    static void logProblem(String message) {
        if (LOGGED_PROBLEMS.add(message)) {
            LOGGER.warn("[container] {}", message);
        }
    }
}
