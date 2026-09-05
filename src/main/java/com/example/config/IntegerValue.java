package com.example.config;

import com.mrcrayfish.configured.api.IConfigValue;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * 整数配置项的临时值（Configured编辑时使用）。
 */
public class IntegerValue implements IConfigValue<Integer>
{
    private final String key;
    private final int defaultValue;
    private final int initialValue;
    private int value;

    public IntegerValue(String key, int defaultValue, int initialValue)
    {
        this.key = key;
        this.defaultValue = defaultValue;
        this.initialValue = initialValue;
        this.value = initialValue;
    }

    @Override
    public Integer get()
    {
        return this.value;
    }

    @Override
    public void set(Integer value)
    {
        this.value = value;
    }

    @Override
    public Integer getDefault()
    {
        return this.defaultValue;
    }

    @Override
    public boolean isValid(Integer value)
    {
        return value != null && value >= 0;
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
    public Text getComment()
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
