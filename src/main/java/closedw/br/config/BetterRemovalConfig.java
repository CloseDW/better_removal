package closedw.br.config;

import com.mrcrayfish.configured.api.ActionResult;
import com.mrcrayfish.configured.api.ConfigType;
import com.mrcrayfish.configured.api.IConfigEntry;
import com.mrcrayfish.configured.api.IConfigValue;
import com.mrcrayfish.configured.api.IModConfig;
import com.mrcrayfish.configured.util.ConfigHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
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

/**
 * Better Removal的配置：为每种容器提供ON/OFF
 */
public class BetterRemovalConfig implements IModConfig
{
    public static final String MOD_ID = "betterremoval";
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

    static
    {
        CATEGORIES.put("general", List.of("jade_preview"));

        CATEGORIES.put("vanilla", List.of(
                "furnace", "blast_furnace", "smoker", "brewing_stand",
                "hopper", "dispenser", "dropper"));

        CATEGORIES.put("farmersdelight", List.of("cooking_pot", "basket"));

        CATEGORIES.put("ad_astra", List.of("compressor", "etrionic_blast_furnace", "fuel_refinery", "oxygen_loader", "cryo_freezer"));

        CATEGORIES.put("crabbersdelight", List.of("crab_trap"));

        CATEGORIES.put("aether", List.of("freezer", "altar"));

        CATEGORIES.put("vinery", List.of("fermentation_barrel", "apple_press"));

        CATEGORIES.put("fossil", List.of("analyzer", "sifter", "culture_vat", "worktable"));

        DEFAULT_VALUES.put("jade_preview", true);
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
    }

    private final Map<String, BooleanValue> values = new LinkedHashMap<>();
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
        DEFAULT_VALUES.forEach((key, defaultValue) ->
        {
            boolean value = Boolean.parseBoolean(props.getProperty(key, String.valueOf(defaultValue)));
            this.values.put(key, new BooleanValue(key, defaultValue, value));
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

    @Override
    public ActionResult update(IConfigEntry entry)
    {
        Set<IConfigValue<?>> changed = ConfigHelper.getChangedValues(entry);
        if(changed.isEmpty())
        {
            return ActionResult.success();
        }

        Properties props = new Properties();
        this.values.forEach((key, value) -> props.setProperty(key, String.valueOf(value.get())));
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
        return ActionResult.success();
    }

    @Override
    public IConfigEntry createRootEntry()
    {
        if(this.root == null)
        {
            List<IConfigEntry> children = new ArrayList<>();
            CATEGORIES.forEach((category, keys) ->
            {
                List<IConfigEntry> entries = keys.stream()
                        .map(key -> (IConfigEntry) new BooleanEntry(this.values.get(key)))
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
    public ActionResult loadWorldConfig(Path path)
    {
        return ActionResult.success();
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