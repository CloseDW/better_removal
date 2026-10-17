package closedw.br.container.menu;

import closedw.br.BetterRemoval;
import closedw.br.container.rule.SlotRules;
import closedw.br.slot.SlotRole;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手动指认映射的加载、查询与保存。
 */
public final class MenuMapStore {

    /** 模组自带资源路径（其它模组也用同一个路径自声明）。 */
    private static final String[] RESOURCE_DIR = { "better-removal", "menu-maps.json" };

    private static final String CONFIG_DIR = "better-removal";
    private static final String CONFIG_FILE = "menu-maps.json";

    private static final Logger LOGGER = BetterRemoval.LOGGER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * 连续改动（按住修饰键滑过涂抹）时把落盘合并成最多每 300ms 一次：
     * 不然拖过一整排箱子就是几十次写盘，白白占用服务端线程。
     */
    private static final long SAVE_INTERVAL_MS = 300L;

    private static final Set<String> LOGGED_PROBLEMS = ConcurrentHashMap.newKeySet();

    /** 配置文件里的映射（可写，优先）。 */
    private static volatile List<MenuMap> writable = List.of();
    /** 模组 jar 里的映射（只读）。 */
    private static volatile List<MenuMap> shipped = List.of();
    /** 合并后的查询表：配置优先。 */
    private static volatile List<MenuMap> merged = List.of();

    private static long lastSaveAt;
    private static boolean savePending;

    private MenuMapStore() {
    }

    public static Path getPath() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIR).resolve(CONFIG_FILE);
    }

    /** 启动时调用一次；重复调用只重新读盘。 */
    public static synchronized void load() {
        // 有没落盘的改动先写下去，免得读回旧内容
        flush();
        writable = readWritable();
        shipped = readShipped();
        merged = merge(writable, shipped);
        LOGGER.info("[menu-map] {} mapping(s) loaded ({} from config, {} from mod files)",
                merged.size(), writable.size(), shipped.size());
    }

    /** 把还没落盘的改动写下去（指认会话结束时、以及重新读盘前调用）。 */
    public static synchronized void flush() {
        if (!savePending) {
            return;
        }
        save();
        savePending = false;
        lastSaveAt = System.currentTimeMillis();
    }

    /** 丢弃缓存后重新读盘（{@code /br reload} 调用）。 */
    public static synchronized void reload() {
        load();
    }

    /** 合并后的映射数量。 */
    public static int size() {
        return merged.size();
    }

    /** 命中该方块的第一条映射；没有则返回 null。 */
    public static MenuMap find(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return null;
        }
        for (MenuMap map : merged) {
            if (SlotRules.matchesEntry(blockEntity, map.match())) {
                return map;
            }
        }
        return null;
    }

    /** 按匹配串精确查找（指认开始时载入已有映射用）。 */
    public static MenuMap findExact(String match) {
        String key = MenuMap.normalize(match);
        if (key.isEmpty()) {
            return null;
        }
        for (MenuMap map : merged) {
            if (MenuMap.normalize(map.match()).equals(key)) {
                return map;
            }
        }
        return null;
    }

    /**
     * 写入或替换一条映射并落盘。传一个所有角色都为空的映射表示删除。
     */
    public static synchronized void putOrReplace(MenuMap map) {
        if (map == null || map.match().isBlank()) {
            return;
        }
        String key = MenuMap.normalize(map.match());
        List<MenuMap> next = new ArrayList<>(writable);
        next.removeIf(existing -> MenuMap.normalize(existing.match()).equals(key));
        if (!map.isEmpty()) {
            next.add(map);
        }
        writable = List.copyOf(next);
        merged = merge(writable, shipped);
        // 落盘限流：短时间内的连续改动先攒着，由 flush() 或下一次超时补上
        long now = System.currentTimeMillis();
        if (now - lastSaveAt >= SAVE_INTERVAL_MS) {
            save();
            lastSaveAt = now;
            savePending = false;
        }
        else {
            savePending = true;
        }
    }

    /**
     * 删除一条映射（按匹配串精确匹配）。只删配置里那份，并且**立刻落盘**——
     * 删除是玩家明确的一次动作，不该像涂抹那样被限流拖着。
     *
     * @return true = 删掉了；false = 配置里没有这条（可能只存在于模组自带文件，或本来就没有）
     */
    public static synchronized boolean remove(String match) {
        String key = MenuMap.normalize(match);
        if (key.isEmpty()) {
            return false;
        }
        List<MenuMap> next = new ArrayList<>(writable);
        if (!next.removeIf(existing -> MenuMap.normalize(existing.match()).equals(key))) {
            return false;
        }
        writable = List.copyOf(next);
        merged = merge(writable, shipped);
        save();
        savePending = false;
        lastSaveAt = System.currentTimeMillis();
        LOGGER.info("[menu-map] removed mapping for {} ({} left)", key, merged.size());
        return true;
    }

    /** 这条匹配串是否来自模组自带文件（只读，删不掉）。 */
    public static boolean isShipped(String match) {
        String key = MenuMap.normalize(match);
        if (key.isEmpty()) {
            return false;
        }
        for (MenuMap map : shipped) {
            if (MenuMap.normalize(map.match()).equals(key)) {
                return true;
            }
        }
        return false;
    }

    // 读

    private static List<MenuMap> readWritable() {
        Path path = getPath();
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<MenuMap> maps = MenuMapParser.parseFile(reader);
            LOGGER.info("[menu-map] loaded {} mapping(s) from {}", maps.size(), path);
            return List.copyOf(maps);
        }
        catch (Throwable t) {
            LOGGER.warn("[menu-map] failed to read {}: {}", path, t.toString());
            return List.of();
        }
    }

    /**
     * 扫描所有模组自带的 {@code better-removal/menu-maps.json}。
     * 按模组 ID 排序，保证加载顺序固定（同一匹配串谁优先不会随机变化）。
     */
    private static List<MenuMap> readShipped() {
        List<MenuMap> maps = new ArrayList<>();
        List<net.neoforged.neoforgespi.language.IModInfo> mods;
        try {
            mods = new ArrayList<>(ModList.get().getMods());
        }
        catch (Throwable t) {
            return List.of();
        }
        mods.sort(Comparator.comparing(mod -> mod.getModId()));
        for (var mod : mods) {
            Path file;
            try {
                file = mod.getOwningFile().getFile().findResource(RESOURCE_DIR);
            }
            catch (Throwable t) {
                continue;
            }
            if (file == null || !Files.isRegularFile(file)) {
                continue;
            }
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                List<MenuMap> parsed = MenuMapParser.parseFile(reader);
                if (!parsed.isEmpty()) {
                    maps.addAll(parsed);
                    LOGGER.info("[menu-map] loaded {} mapping(s) from {}!better-removal/{}",
                            parsed.size(), mod.getModId(), CONFIG_FILE);
                }
            }
            catch (Throwable t) {
                LOGGER.warn("[menu-map] failed to read menu maps from {}: {}", mod.getModId(), t.toString());
            }
        }
        return List.copyOf(maps);
    }

    private static List<MenuMap> merge(List<MenuMap> writableMaps, List<MenuMap> shippedMaps) {
        List<MenuMap> all = new ArrayList<>(writableMaps);
        Set<String> seen = ConcurrentHashMap.newKeySet();
        writableMaps.forEach(map -> seen.add(MenuMap.normalize(map.match())));
        for (MenuMap map : shippedMaps) {
            if (seen.add(MenuMap.normalize(map.match()))) {
                all.add(map);
            }
        }
        validate(all);
        return List.copyOf(all);
    }

    // 写

    private static void save() {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (MenuMap map : writable) {
            JsonObject object = new JsonObject();
            object.addProperty("match", map.match());
            object.addProperty("menuSize", map.menuSize());
            for (SlotRole role : List.of(SlotRole.INPUT, SlotRole.FUEL, SlotRole.OUTPUT, SlotRole.NONE)) {
                int[] slots = map.slots(role);
                if (slots.length == 0) {
                    continue;
                }
                JsonArray list = new JsonArray();
                for (int slot : slots) {
                    list.add(slot);
                }
                object.add(role.name().toLowerCase(Locale.ROOT), list);
            }
            array.add(object);
        }
        root.add("maps", array);

        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        }
        catch (Throwable t) {
            LOGGER.error("[menu-map] failed to save {}: {}", path, t.toString());
        }
    }

    // 自检

    /**
     * 加载自检：只做不依赖游戏状态的检查（匹配串为空、菜单槽位总数缺失、没有任何角色）。
     * 「槽位能不能真的解析出来」要等有容器实例时才算得出来，由指认会话里的离线自检负责。
     */
    private static void validate(List<MenuMap> maps) {
        Set<String> seen = ConcurrentHashMap.newKeySet();
        for (MenuMap map : maps) {
            String key = MenuMap.normalize(map.match());
            if (!seen.add(key)) {
                logProblem("mapping " + map.match() + " is declared more than once, the first one wins");
            }
            if (map.isEmpty()) {
                logProblem("mapping " + map.match() + " declares no slots, so it has no effect");
            }
            for (SlotRole role : SlotRole.values()) {
                for (int slot : map.slots(role)) {
                    if (slot < 0 || slot >= map.menuSize()) {
                        logProblem("mapping " + map.match() + " declares menuSize=" + map.menuSize()
                                + " but uses menu slot " + slot + " (out-of-range slots are ignored)");
                    }
                }
            }
        }
    }

    private static void logProblem(String message) {
        if (LOGGED_PROBLEMS.add(message)) {
            LOGGER.warn("[menu-map] {}", message);
        }
    }

    /** 仅测试/调试用：导出当前内存里的映射表。 */
    public static List<MenuMap> all() {
        return merged;
    }
}
