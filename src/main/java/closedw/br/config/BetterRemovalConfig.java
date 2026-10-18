package closedw.br.config;

import com.mrcrayfish.configured.api.ConfigType;
import com.mrcrayfish.configured.api.IConfigEntry;
import com.mrcrayfish.configured.api.IConfigValue;
import com.mrcrayfish.configured.api.IModConfig;
import com.mrcrayfish.configured.util.ConfigHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Better Removal的配置：为每种容器提供ON/OFF。
 * 容器开关的清单不再写死在这里——容器的槽位布局由 json 规则描述，
 * 所以分组与键在规则加载后由 {@link #registerRuleContainers} 动态登记
 * （只有两个必须硬编码的容器例外，见 {@link #BUILTIN_CONTAINER_GROUPS}）。
 */
public class BetterRemovalConfig implements IModConfig
{
    public static final String MOD_ID = "betterremoval";
    public static final String FILE_NAME = "better-removal.properties";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 顶层分组：分组名->配置键列表（不含“容器开关”，它由 {@link #CONTAINER_CATEGORIES} 组合而成）。
     */
    public static final Map<String, List<String>> CATEGORIES = new LinkedHashMap<>();

    /**
     * “容器开关”大类下的各模组子分组：子分组名->配置键列表。
     * 由 {@link #registerRuleContainers} 按已加载的规则填充。
     */
    public static final Map<String, List<String>> CONTAINER_CATEGORIES = new LinkedHashMap<>();

    /**
     * 配置键与默认值。
     */
    public static final Map<String, Boolean> DEFAULT_VALUES = new LinkedHashMap<>();

    /**
     * 整数配置键与默认值。
     */
    public static final Map<String, Integer> INT_DEFAULTS = new LinkedHashMap<>();

    /**
     * 字符串列表配置键与默认值（声明式槽位规则）。
     */
    public static final Map<String, List<String>> LIST_DEFAULTS = new LinkedHashMap<>();

    /**
     * 带说明文案（{@code config.betterremoval.<key>.tooltip}）的布尔开关。
     * 只有列在这里的键才会把 tooltip 交给 Configured，避免缺文案时界面显示原始翻译键。
     */
    public static final Set<String> TOOLTIP_KEYS = new LinkedHashSet<>(List.of(
            "experimental_transfer_probe", "menu_mapping"));

    /**
     * 必须硬编码的容器（厨锅、烤炉）没有 json 规则，所以它们的开关在这里显式给出所属分组。
     * 键 → 分组名（分组名同时用作 {@code config.betterremoval.category.<分组>} 的翻译键）。
     */
    private static final Map<String, String> BUILTIN_CONTAINER_GROUPS = new LinkedHashMap<>();

    /**
     * 声明式规则里的「模组命名空间 → 容器开关分组」映射。
     * 没列在这里的命名空间会归到 {@code rules} 分组，用模组自己的显示名。
     */
    private static final Map<String, String> NAMESPACE_TO_GROUP = new LinkedHashMap<>();

    /**
     * 不依赖任何模组就始终显示的分组。
     * {@code vanilla} 是原版容器；{@code rules} 是兜底分组，装的是命名空间推不出来的规则。
     * 其余分组名都是模组命名空间，按"模组是否安装"过滤。
     */
    private static final Set<String> ALWAYS_VISIBLE_GROUPS = Set.of("vanilla", "rules");

    static
    {
        NAMESPACE_TO_GROUP.put("minecraft", "vanilla");
        NAMESPACE_TO_GROUP.put("farmersdelight", "farmersdelight");
        NAMESPACE_TO_GROUP.put("ad_astra", "ad_astra");
        NAMESPACE_TO_GROUP.put("crabbersdelight", "crabbersdelight");
        NAMESPACE_TO_GROUP.put("aether", "aether");
        NAMESPACE_TO_GROUP.put("vinery", "vinery");
        NAMESPACE_TO_GROUP.put("fossil", "fossil");
        NAMESPACE_TO_GROUP.put("cookingforblockheads", "cookingforblockheads");
        NAMESPACE_TO_GROUP.put("farm_and_charm", "farm_and_charm");
        NAMESPACE_TO_GROUP.put("ironfurnaces", "ironfurnaces");

        BUILTIN_CONTAINER_GROUPS.put("cooking_pot", "farmersdelight");
        BUILTIN_CONTAINER_GROUPS.put("oven", "cookingforblockheads");

        // 三个顶层大类：容器开关 / 实验性 / 通用
        // 「容器开关」下的分组与每个分组的键由规则 + 内置容器推导，见 registerRuleContainers
        CATEGORIES.put("experimental", List.of(
                "experimental_transfer_probe", "menu_mapping", "slot_rules"));

        CATEGORIES.put("general", List.of(
                "jade_preview", "ftb_ultimine", "deposit", "restock",
                "ftb_ultimine_max_containers"));

        DEFAULT_VALUES.put("jade_preview", true);
        DEFAULT_VALUES.put("ftb_ultimine", true);
        DEFAULT_VALUES.put("deposit", true);
        DEFAULT_VALUES.put("restock", true);

        // 实验性：主动探测模式（按住修饰键右击容器，把槽位探测结果以规则串打到聊天框），默认关闭
        DEFAULT_VALUES.put("experimental_transfer_probe", false);

        // 实验性：手动指认（模式滚轮多出「手动指认」入口，右击打开 GUI 给槽位打标签），默认关闭
        DEFAULT_VALUES.put("menu_mapping", false);

        // FTB Ultimine连锁取出单次最多容器数量（64）
        INT_DEFAULTS.put("ftb_ultimine_max_containers", 64);

        // 声明式槽位规则，每条一条 "匹配串 角色=槽位..."；不受任何实验性开关约束
        LIST_DEFAULTS.put("slot_rules", List.of());
    }

    private final Map<String, BooleanValue> values = new LinkedHashMap<>();
    private final Map<String, IntegerValue> intValues = new LinkedHashMap<>();
    private final Map<String, StringListValue> listValues = new LinkedHashMap<>();
    /** 读盘得到的原始属性：动态登记的容器开关要按它恢复玩家上次保存的值。 */
    private Properties loadedProps = new Properties();
    private IConfigEntry root;

    private BetterRemovalConfig()
    {
        this.load();
    }

    private static final BetterRemovalConfig INSTANCE = new BetterRemovalConfig();

    public static BetterRemovalConfig get()
    {
        return INSTANCE;
    }

    public static Path getPath()
    {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private void load()
    {
        Properties props = new Properties();
        Path path = getPath();
        if(Files.exists(path))
        {
            try(InputStream in = Files.newInputStream(path))
            {
                props.load(in);
            }
            catch(IOException e)
            {
                LOGGER.warn("Failed to load config from {}", path, e);
            }
        }
        this.loadedProps = props;
        DEFAULT_VALUES.forEach((key, defaultValue) ->
        {
            boolean value = Boolean.parseBoolean(props.getProperty(key, String.valueOf(defaultValue)));
            this.values.put(key, new BooleanValue(key, defaultValue, value, TOOLTIP_KEYS.contains(key)));
        });
        INT_DEFAULTS.forEach((key, defaultValue) ->
        {
            int value = defaultValue;
            try
            {
                value = Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
            }
            catch(NumberFormatException e)
            {
                LOGGER.warn("Invalid integer config value for {}, using default {}", key, defaultValue);
            }
            this.intValues.put(key, new IntegerValue(key, defaultValue, value));
        });
        LIST_DEFAULTS.forEach((key, defaultValue) ->
        {
            List<String> value = new ArrayList<>();
            // 用换行分隔，避免与规则本身的逗号（槽位表 / 逗号 match）冲突
            for(String part : props.getProperty(key, "").split("\n"))
            {
                String entry = part.trim();
                if(!entry.isEmpty())
                {
                    value.add(entry);
                }
            }
            this.listValues.put(key, new StringListValue(key, defaultValue, value));
        });
    }

    /**
     * 查询某个容器是否启用（默认启用）。
     */
    public boolean isEnabled(String key)
    {
        BooleanValue value = this.values.get(key);
        return value == null || value.get();
    }

    /**
     * 查询整数配置（未配置时返回默认值）。
     */
    public int getInt(String key)
    {
        IntegerValue value = this.intValues.get(key);
        return value != null ? value.get() : INT_DEFAULTS.getOrDefault(key, 0);
    }

    /**
     * 查询字符串列表配置（未配置时返回默认值）。
     */
    public List<String> getList(String key)
    {
        StringListValue value = this.listValues.get(key);
        return value != null ? value.get() : LIST_DEFAULTS.getOrDefault(key, List.of());
    }

    @Override
    public void update(IConfigEntry entry)
    {
        Set<IConfigValue<?>> changed = ConfigHelper.getChangedValues(entry);
        if(changed.isEmpty())
        {
            return;
        }

        Properties props = new Properties();
        this.values.forEach((key, value) -> props.setProperty(key, String.valueOf(value.get())));
        this.intValues.forEach((key, value) -> props.setProperty(key, String.valueOf(value.get())));
        this.listValues.forEach((key, value) -> props.setProperty(key, String.join("\n", value.get())));
        Path path = getPath();
        try
        {
            Files.createDirectories(path.getParent());
            try(OutputStream out = Files.newOutputStream(path))
            {
                props.store(out, "Better Removal container toggles (edited via Configured)");
            }
            this.loadedProps = props;
        }
        catch(IOException e)
        {
            LOGGER.error("Failed to save config to {}", path, e);
        }
        return;
    }

    /**
     * 登记「容器开关」分组与每个容器的开关键。
     * 容器的槽位布局由 json 规则描述，代码里不再有这些容器的一份清单，
     * 所以 Configured 里能出现哪些开关完全取决于实际加载到了哪些规则（外加两个硬编码容器）。
     * <p>
     * 同时按<b>已安装的模组</b>过滤：只显示对应模组真的装了的开关，
     * 没装的模组不会在界面里留下一个永远用不到的分类。
     * @param configKeys 配置键 → 默认值
     * @param declaredGroups 配置键 → 该规则的分组名（已按 {@code group} 字段 / 命名空间解析过）
     */
    public void registerRuleContainers(Map<String, Boolean> configKeys, Map<String, String> declaredGroups)
    {
        Map<String, List<String>> groups = new LinkedHashMap<>();

        // 硬编码容器（厨锅、烤炉）没有规则，先把它们的开关登记进来
        BUILTIN_CONTAINER_GROUPS.forEach((key, group) ->
                registerContainerKey(key, true, group, groups));

        configKeys.forEach((key, defaultValue) ->
        {
            String declared = declaredGroups.get(key);
            String group = declared == null || declared.isBlank()
                    ? "rules"
                    : NAMESPACE_TO_GROUP.getOrDefault(declared, declared);
            registerContainerKey(key, defaultValue, group, groups);
        });

        if(groups.isEmpty())
        {
            return;
        }
        // 合并进已有的分组（重复 reload 时不能把上一轮的键丢掉）
        groups.forEach((group, keys) ->
        {
            List<String> existing = CONTAINER_CATEGORIES.computeIfAbsent(group, name -> new ArrayList<>());
            for(String key : keys)
            {
                if(!existing.contains(key))
                {
                    existing.add(key);
                }
            }
        });
        // 分组变了，Configured 的界面缓存必须作废
        this.root = null;
    }

    private void registerContainerKey(String key, boolean defaultValue, String group, Map<String, List<String>> groups)
    {
        if(key == null || key.isBlank())
        {
            return;
        }
        // 值定义始终登记（老配置里的键要能保留、机器翻译不了也不能丢），只是界面不显示
        DEFAULT_VALUES.putIfAbsent(key, defaultValue);
        // 已经有值的键（上一次 reload）保持原值，只补上缺失的定义；
        // 首次登记时按磁盘上保存的值恢复，否则玩家在 Configured 里关掉的容器下次启动会自己打开
        if(!this.values.containsKey(key))
        {
            boolean saved = Boolean.parseBoolean(
                    this.loadedProps.getProperty(key, String.valueOf(defaultValue)));
            this.values.put(key, new BooleanValue(key, defaultValue, saved, TOOLTIP_KEYS.contains(key)));
        }

        if(!isGroupVisible(group))
        {
            return;
        }
        groups.computeIfAbsent(group, name -> new ArrayList<>()).add(key);
    }

    /**
     * 该分组是否应该在「容器开关」里显示。
     * 分组名就是模组命名空间（原版是 {@code vanilla}），所以除了不依赖模组的分组之外，
     * 一律要求对应模组已安装——没装的模组不该在配置界面里留一个空分类。
     */
    private static boolean isGroupVisible(String group)
    {
        if(ALWAYS_VISIBLE_GROUPS.contains(group))
        {
            return true;
        }
        return isModLoaded(group);
    }

    private static boolean isModLoaded(String modId)
    {
        try
        {
            return ModList.get().isLoaded(modId);
        }
        catch(Throwable t)
        {
            // 查询失败时按"装了"处理，宁可多显示也不要静默把开关藏起来
            return true;
        }
    }

    @Override
    public IConfigEntry getRoot()
    {
        if(this.root == null)
        {
            List<IConfigEntry> children = new ArrayList<>();

            // 容器开关：大类下按模组分子类
            List<IConfigEntry> containerChildren = new ArrayList<>();
            CONTAINER_CATEGORIES.forEach((category, keys) ->
                    containerChildren.add(new CategoryEntry(category, leafEntries(keys))));
            children.add(new CategoryEntry("containers", containerChildren));

            // 实验性 / 通用
            CATEGORIES.forEach((category, keys) ->
                    children.add(new CategoryEntry(category, leafEntries(keys))));

            this.root = new RootEntry(children);
        }
        return this.root;
    }

    /** 把配置键列表转成对应的叶子配置项。 */
    private List<IConfigEntry> leafEntries(List<String> keys)
    {
        return keys.stream()
                .map(key -> INT_DEFAULTS.containsKey(key)
                        ? (IConfigEntry) new IntegerEntry(this.intValues.get(key))
                        : LIST_DEFAULTS.containsKey(key)
                                ? new ListEntry(this.listValues.get(key))
                                : new BooleanEntry(this.values.get(key)))
                .toList();
    }

    @Override
    public ConfigType getType()
    {
        return ConfigType.UNIVERSAL;
    }

    @Override
    public String getFileName()
    {
        return FILE_NAME;
    }

    @Override
    public String getModId()
    {
        return MOD_ID;
    }

    @Override
    public void loadWorldConfig(Path path, Consumer<IModConfig> result)
    {
        // UNIVERSAL 配置是本地文件，没有"按世界加载"这回事
    }

    public static class RootEntry implements IConfigEntry
    {
        private final List<IConfigEntry> children;

        public RootEntry(List<IConfigEntry> children)
        {
            this.children = children;
        }

        @Override
        public List<IConfigEntry> getChildren()
        {
            return this.children;
        }

        @Override
        public boolean isRoot()
        {
            return true;
        }

        @Override
        public boolean isLeaf()
        {
            return false;
        }

        @Override
        public IConfigValue<?> getValue()
        {
            return null;
        }

        @Override
        public String getEntryName()
        {
            return "Root";
        }

        @Override
        public Component getTooltip()
        {
            return null;
        }

        @Override
        public String getTranslationKey()
        {
            return null;
        }
    }

    public static class BooleanEntry implements IConfigEntry
    {
        private final BooleanValue value;

        public BooleanEntry(BooleanValue value)
        {
            this.value = value;
        }

        @Override
        public List<IConfigEntry> getChildren()
        {
            return List.of();
        }

        @Override
        public boolean isRoot()
        {
            return false;
        }

        @Override
        public boolean isLeaf()
        {
            return true;
        }

        @Override
        public IConfigValue<?> getValue()
        {
            return this.value;
        }

        @Override
        public String getEntryName()
        {
            return this.value.getName();
        }

        @Override
        public Component getTooltip()
        {
            return this.value.getComment();
        }

        @Override
        public String getTranslationKey()
        {
            return this.value.getTranslationKey();
        }
    }

    public static class IntegerEntry implements IConfigEntry
    {
        private final IntegerValue value;

        public IntegerEntry(IntegerValue value)
        {
            this.value = value;
        }

        @Override
        public List<IConfigEntry> getChildren()
        {
            return List.of();
        }

        @Override
        public boolean isRoot()
        {
            return false;
        }

        @Override
        public boolean isLeaf()
        {
            return true;
        }

        @Override
        public IConfigValue<?> getValue()
        {
            return this.value;
        }

        @Override
        public String getEntryName()
        {
            return this.value.getName();
        }

        @Override
        public Component getTooltip()
        {
            return this.value.getComment();
        }

        @Override
        public String getTranslationKey()
        {
            return this.value.getTranslationKey();
        }
    }

    public static class ListEntry implements IConfigEntry
    {
        private final StringListValue value;

        public ListEntry(StringListValue value)
        {
            this.value = value;
        }

        @Override
        public List<IConfigEntry> getChildren()
        {
            return List.of();
        }

        @Override
        public boolean isRoot()
        {
            return false;
        }

        @Override
        public boolean isLeaf()
        {
            return true;
        }

        @Override
        public IConfigValue<?> getValue()
        {
            return this.value;
        }

        @Override
        public String getEntryName()
        {
            return this.value.getName();
        }

        @Override
        public Component getTooltip()
        {
            return this.value.getComment();
        }

        @Override
        public String getTranslationKey()
        {
            return this.value.getTranslationKey();
        }
    }

    public static class CategoryEntry implements IConfigEntry
    {
        private final String name;
        private final List<IConfigEntry> children;

        public CategoryEntry(String name, List<IConfigEntry> children)
        {
            this.name = name;
            this.children = children;
        }

        @Override
        public List<IConfigEntry> getChildren()
        {
            return this.children;
        }

        @Override
        public boolean isRoot()
        {
            return false;
        }

        @Override
        public boolean isLeaf()
        {
            return false;
        }

        @Override
        public IConfigValue<?> getValue()
        {
            return null;
        }

        @Override
        public String getEntryName()
        {
            return this.name;
        }

        @Override
        public Component getTooltip()
        {
            return null;
        }

        @Override
        public String getTranslationKey()
        {
            return "config." + MOD_ID + ".category." + this.name;
        }
    }
}
