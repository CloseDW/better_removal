package closedw.br.config;

import com.mrcrayfish.configured.api.IConfigValue;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 字符串列表配置项的临时值（Configured编辑时使用）。
 * Configured 会先按元素类型探测（ListTypes.fromElementValidator），
 * 因 {@link #isValid(List)} 接受字符串列表。
 */
public class StringListValue implements IConfigValue<List<String>>
{
    private final String key;
    private final List<String> defaultValue;
    private final List<String> initialValue;
    private List<String> value;

    public StringListValue(String key, List<String> defaultValue, List<String> initialValue)
    {
        this.key = key;
        this.defaultValue = List.copyOf(defaultValue);
        this.initialValue = List.copyOf(initialValue);
        this.value = new ArrayList<>(initialValue);
    }

    @Override
    public List<String> get()
    {
        return this.value;
    }

    @Override
    public void set(List<String> value)
    {
        this.value = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    @Override
    public List<String> getDefault()
    {
        return this.defaultValue;
    }

    @Override
    public boolean isValid(List<String> value)
    {
        return value != null && value.stream().allMatch(entry -> entry != null && !entry.isBlank());
    }

    @Override
    public boolean isDefault()
    {
        return this.value.equals(this.defaultValue);
    }

    @Override
    public boolean isChanged()
    {
        return !this.value.equals(this.initialValue);
    }

    @Override
    public void restore()
    {
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Nullable
    @Override
    public Text getComment()
    {
        return Text.translatable(this.getTranslationKey() + ".tooltip");
    }

    @Nullable
    @Override
    public String getTranslationKey()
    {
        return "config." + BetterRemovalConfig.MOD_ID + "." + this.key;
    }

    @Nullable
    @Override
    public Text getValidationHint()
    {
        return null;
    }

    @Override
    public String getName()
    {
        return this.key;
    }

    @Override
    public void cleanCache()
    {
    }

    @Override
    public boolean requiresWorldRestart()
    {
        return false;
    }

    @Override
    public boolean requiresGameRestart()
    {
        return false;
    }
}
