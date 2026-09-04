package closedw.br.config;

import com.mrcrayfish.configured.api.IConfigValue;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 布尔配置项的临时值（Configured编辑时使用）。
 */
public class BooleanValue implements IConfigValue<Boolean>
{
    private final String key;
    private final boolean defaultValue;
    private final boolean initialValue;
    private boolean value;

    public BooleanValue(String key, boolean defaultValue, boolean initialValue)
    {
        this.key = key;
        this.defaultValue = defaultValue;
        this.initialValue = initialValue;
        this.value = initialValue;
    }

    @Override
    public Boolean get()
    {
        return this.value;
    }

    @Override
    public void set(Boolean value)
    {
        this.value = value;
    }

    @Override
    public Boolean getDefault()
    {
        return this.defaultValue;
    }

    @Override
    public boolean isValid(Boolean value)
    {
        return value != null;
    }

    @Override
    public boolean isDefault()
    {
        return this.value == this.defaultValue;
    }

    @Override
    public boolean isChanged()
    {
        return this.value != this.initialValue;
    }

    @Override
    public void restore()
    {
        this.value = this.defaultValue;
    }

    @Nullable
    @Override
    public Component getComment()
    {
        return null;
    }

    @Nullable
    @Override
    public String getTranslationKey()
    {
        return "config." + BetterRemovalConfig.MOD_ID + "." + this.key;
    }

    @Nullable
    @Override
    public Component getValidationHint()
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