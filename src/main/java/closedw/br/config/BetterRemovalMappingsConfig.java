package closedw.br.config;

import closedw.br.container.menu.MenuMap;
import closedw.br.container.menu.MenuMapStore;
import closedw.br.slot.SlotRole;

import com.mrcrayfish.configured.api.ConfigType;
import com.mrcrayfish.configured.api.IConfigEntry;
import com.mrcrayfish.configured.api.IConfigValue;
import com.mrcrayfish.configured.api.IModConfig;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Configured 里"已指认的容器"的<b>只读</b>视图：每个指认过的容器一行，标签就是匹配串，
 * 右侧按钮点开能看到各角色的槽位数量。
 * <p>
 * 只读是靠 {@link #isReadOnly()} 实现的——这是 Configured 自己的机制：
 * 配置仍会出现在模组列表里，但标签变成 "View"（带锁图标）、每个值的"重置"按钮被禁用、
 * 列表界面也只允许查看（列表项按钮文案由 {@code configured.gui.edit} 变成 {@code configured.gui.view}）。
 * {@link #update} 另外做成空操作，就算有哪条路径漏了也只会有"看起来改了、保存无效"。
 * <p>
 * <b>注意</b>：这里读的是<b>本端</b>{@code config/better-removal/menu-maps.json}
 * （单人/局域网就是服务端那一份；联机时是客户端自己那份，可能与服务器的不同）。
 * 想看服务端权威数据用 {@code /br list}。
 */
public final class BetterRemovalMappingsConfig implements IModConfig {

    public static final String MOD_ID = "betterremoval";
    /** 没有对应的落盘文件，只作为这个视图的标识。 */
    public static final String FILE_NAME = "better-removal-menu-maps";

    private static final BetterRemovalMappingsConfig INSTANCE = new BetterRemovalMappingsConfig();

    private BetterRemovalMappingsConfig() {
    }

    public static BetterRemovalMappingsConfig get() {
        return INSTANCE;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ConfigType getType() {
        return ConfigType.CLIENT;
    }

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public String getTranslationKey() {
        return "config." + MOD_ID + ".mappings";
    }

    /** 只读：不落盘、不接收任何修改。 */
    @Override
    public void update(IConfigEntry entry) {
        // 只读视图：什么也不保存
    }

    @Override
    public void loadWorldConfig(Path path, Consumer<IModConfig> result) {
        // 只读视图：没有"按世界加载"这回事
    }

    @Override
    public IConfigEntry getRoot() {
        List<IConfigEntry> children = new ArrayList<>();
        List<MenuMap> maps = MenuMapStore.all();
        if (maps.isEmpty()) {
            children.add(new MappingEntry(emptyLabel(), List.of()));
        }
        else {
            for (MenuMap map : maps) {
                children.add(new MappingEntry(map.match(), roleLines(map)));
            }
        }
        return new RootEntry(children);
    }

    /** 一条映射的角色明细，本地化后的纯文本（Configured 的值是 String，必须提前解析）。 */
    private static List<String> roleLines(MenuMap map) {
        List<String> lines = new ArrayList<>();
        for (SlotRole role : List.of(SlotRole.INPUT, SlotRole.OUTPUT, SlotRole.FUEL)) {
            int count = map.slots(role).length;
            if (count > 0) {
                lines.add(translate(role.getTranslationKey()) + " ×" + count);
            }
        }
        if (lines.isEmpty()) {
            lines.add(emptyLabel());
        }
        return lines;
    }

    private static String emptyLabel() {
        return translate("betterremoval.mapping.entry_empty");
    }

    /** 客户端语言表里的文本；取不到就退回键名。 */
    private static String translate(String key) {
        try {
            return Language.getInstance().getOrDefault(key);
        }
        catch (Throwable t) {
            return key;
        }
    }

    /** 根：直接就是一条条映射，打开配置就能看到，不用点进分类。 */
    private record RootEntry(List<IConfigEntry> children) implements IConfigEntry {

        @Override
        public List<IConfigEntry> getChildren() {
            return this.children;
        }

        @Override
        public boolean isRoot() {
            return true;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public IConfigValue<?> getValue() {
            return null;
        }

        @Override
        public String getEntryName() {
            return "root";
        }

        @Nullable
        @Override
        public Component getTooltip() {
            return Component.translatable("config." + MOD_ID + ".mappings.tooltip");
        }

        @Nullable
        @Override
        public String getTranslationKey() {
            return null;
        }
    }

    /**
     * 一条映射：标签直接返回匹配串——Configured 把 {@code getTranslationKey()} 当普通文本渲染，
     * 而匹配串本来就不需要翻译。右侧是只读的"View"按钮，点开能看到角色明细。
     */
    private record MappingEntry(String label, List<String> roles) implements IConfigEntry {

        @Override
        public List<IConfigEntry> getChildren() {
            return List.of();
        }

        @Override
        public boolean isRoot() {
            return false;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public IConfigValue<?> getValue() {
            return new RolesValue(this.label, this.roles);
        }

        @Override
        public String getEntryName() {
            return this.label;
        }

        @Nullable
        @Override
        public Component getTooltip() {
            return null;
        }

        @Override
        public String getTranslationKey() {
            return this.label;
        }
    }

    /** 只读字符串列表：能读、能看，改不了。 */
    private record RolesValue(String label, List<String> roles) implements IConfigValue<List<String>> {

        @Override
        public List<String> get() {
            return this.roles;
        }

        @Override
        public List<String> getDefault() {
            return this.roles;
        }

        @Override
        public void set(List<String> value) {
            // 只读视图：忽略任何修改
        }

        @Override
        public boolean isValid(List<String> value) {
            return true;
        }

        @Override
        public boolean isDefault() {
            return true;
        }

        @Override
        public boolean isChanged() {
            return false;
        }

        @Override
        public void restore() {
            // 只读视图：没有"恢复默认"这回事
        }

        @Nullable
        @Override
        public Component getComment() {
            return null;
        }

        @Override
        public String getTranslationKey() {
            return this.label;
        }

        @Nullable
        @Override
        public Component getValidationHint() {
            return null;
        }

        @Override
        public String getName() {
            return this.label;
        }

        @Override
        public void cleanCache() {
        }

        @Override
        public boolean requiresWorldRestart() {
            return false;
        }

        @Override
        public boolean requiresGameRestart() {
            return false;
        }
    }
}
