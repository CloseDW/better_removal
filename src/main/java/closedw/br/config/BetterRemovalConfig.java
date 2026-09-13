package closedw.br.config;

import com.mrcrayfish.configured.api.ConfigType;
import com.mrcrayfish.configured.api.IConfigEntry;
import com.mrcrayfish.configured.api.IConfigValue;
import com.mrcrayfish.configured.api.IModConfig;
import com.mrcrayfish.configured.util.ConfigHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Better Removal的配置：为每种容器提供ON/OFF
 */
public class BetterRemovalConfig implements IModConfig
{
    public static final String MOD_ID = "better-removal";
    public static final String FILE_NAME = "better-removal.properties";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 配置分组：分组名->配置键列表
     * 不同模组的容器按模组分类
     */
    public static final Map<String, List<String>> CATEGORIES = new LinkedHashMap<>();

    /**
     * 配置键与默认值。
     */
    public static final Map<String, Boolean> DEFAULT_VALUES = new LinkedHashMap<>();

    /**
     * 整数配置键与默认值。
     */
    public static final Map<String, Integer> INT_DEFAULTS = new LinkedHashMap<>();

    /**
     * 字符串列表配置键与默认值（实验性自动探测白名单等）。
     */
    public static final Map<String, List<String>> LIST_DEFAULTS = new LinkedHashMap<>();

    static
    {
        CATEGORIES.put("general", List.of("jade_preview", "ftb_ultimine", "deposit", "restock", "ftb_ultimine_max_containers"));

        CATEGORIES.put("vanilla", List.of(
                "furnace", "blast_furnace", "smoker", "brewing_stand",
                "hopper", "dispenser", "dropper"));

        CATEGORIES.put("farmersdelight", List.of("cooking_pot", "basket"));

        CATEGORIES.put("ad_astra", List.of("compressor", "etrionic_blast_furnace", "fuel_refinery", "oxygen_loader", "cryo_freezer"));

        CATEGORIES.put("crabbersdelight", List.of("crab_trap"));

        CATEGORIES.put("aether", List.of("freezer", "altar"));

        CATEGORIES.put("vinery", List.of("fermentation_barrel", "apple_press"));

        CATEGORIES.put("fossil", List.of("analyzer", "sifter", "culture_vat", "worktable"));

        CATEGORIES.put("cookingforblockheads", List.of("oven"));

        CATEGORIES.put("farm_and_charm", List.of("fc_cooking_pot", "roaster", "stove"));

        CATEGORIES.put("experimental", List.of("experimental_auto_detect", "experimental_auto_detect_whitelist", "experimental_transfer_probe", "experimental_slot_rules"));

        DEFAULT_VALUES.put("jade_preview", true);
        DEFAULT_VALUES.put("ftb_ultimine", true);
        DEFAULT_VALUES.put("deposit", true);
        DEFAULT_VALUES.put("restock", true);
        DEFAULT_VALUES.put("furnace", true);
        DEFAULT_VALUES.put("blast_furnace", true);
        DEFAULT_VALUES.put("smoker", true);
        DEFAULT_VALUES.put("brewing_stand", true);
        DEFAULT_VALUES.put("hopper", true);
        DEFAULT_VALUES.put("dispenser", true);
        DEFAULT_VALUES.put("dropper", true);
        DEFAULT_VALUES.put("cooking_pot", true);
        DEFAULT_VALUES.put("basket", true);
        DEFAULT_VALUES.put("compressor", true);
        DEFAULT_VALUES.put("etrionic_blast_furnace", true);
        DEFAULT_VALUES.put("fuel_refinery", true);
        DEFAULT_VALUES.put("oxygen_loader", true);
        DEFAULT_VALUES.put("cryo_freezer", true);
        DEFAULT_VALUES.put("crab_trap", true);
        DEFAULT_VALUES.put("freezer", true);
        DEFAULT_VALUES.put("altar", true);
        DEFAULT_VALUES.put("fermentation_barrel", true);
        DEFAULT_VALUES.put("apple_press", true);
        DEFAULT_VALUES.put("analyzer", true);
        DEFAULT_VALUES.put("sifter", true);
        DEFAULT_VALUES.put("culture_vat", true);
        DEFAULT_VALUES.put("worktable", true);
        DEFAULT_VALUES.put("oven", true);
        DEFAULT_VALUES.put("fc_cooking_pot", true);
        DEFAULT_VALUES.put("roaster", true);
        DEFAULT_VALUES.put("stove", true);

        // 实验性：通用容器槽位自动探测，默认关闭；开启后必须配置白名单才生效
        DEFAULT_VALUES.put("experimental_auto_detect", false);

        // 实验性：主动探测模式（按住修饰键右击容器，把三次探测结果以规则 JSON 打到聊天框），默认关闭
        // 同时也决定自动探测是否允许跑"用 quickMove 主动搬运"那个后端
        DEFAULT_VALUES.put("experimental_transfer_probe", false);

        // FTB Ultimine连锁取出单次最多容器数量（64）
        INT_DEFAULTS.put("ftb_ultimine_max_containers", 64);

        LIST_DEFAULTS.put("experimental_auto_detect_whitelist", List.of());

        // 实验性：手写槽位规则，每条一条 "匹配串 角色=槽位..."，例如 ironfurnaces input=0 fuel=1 output=2
        LIST_DEFAULTS.put("experimental_slot_rules", List.of());
    }

    private final Map<String, BooleanValue> values = new LinkedHashMap<>();
    private final Map<String, IntegerValue> intValues = new LinkedHashMap<>();
    private final Map<String, StringListValue> listValues = new LinkedHashMap<>();
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
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
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
        DEFAULT_VALUES.forEach((key, defaultValue) ->
        {
            boolean value = Boolean.parseBoolean(props.getProperty(key, String.valueOf(defaultValue)));
            this.values.put(key, new BooleanValue(key, defaultValue, value));
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
            for(String part : props.getProperty(key, "").split(","))
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
        this.listValues.forEach((key, value) -> props.setProperty(key, String.join(",", value.get())));
        Path path = getPath();
        try
        {
            Files.createDirectories(path.getParent());
            try(OutputStream out = Files.newOutputStream(path))
            {
                props.store(out, "Better Removal container toggles (edited via Configured)");
            }
        }
        catch(IOException e)
        {
            LOGGER.error("Failed to save config to {}", path, e);
        }
    }

    @Override
    public IConfigEntry getRoot()
    {
        if(this.root == null)
        {
            List<IConfigEntry> children = new ArrayList<>();
            CATEGORIES.forEach((category, keys) ->
            {
                List<IConfigEntry> entries = keys.stream()
                        .map(key -> (IConfigEntry) (INT_DEFAULTS.containsKey(key)
                                ? new IntegerEntry(this.intValues.get(key))
                                : LIST_DEFAULTS.containsKey(key)
                                        ? new ListEntry(this.listValues.get(key))
                                        : new BooleanEntry(this.values.get(key))))
                        .toList();
                children.add(new CategoryEntry(category, entries));
            });
            this.root = new RootEntry(children);
        }
        return this.root;
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
        public Text getTooltip()
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
        public Text getTooltip()
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
        public Text getTooltip()
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
        public Text getTooltip()
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
        public Text getTooltip()
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
