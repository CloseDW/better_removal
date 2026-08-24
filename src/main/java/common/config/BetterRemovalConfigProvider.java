package common.config;

import com.mrcrayfish.configured.api.IModConfig;
import com.mrcrayfish.configured.api.IModConfigProvider;
import com.mrcrayfish.configured.api.ModContext;

import java.util.Set;

/**
 * Configured 的配置提供： custom.configured.providers中注册
 */
public class BetterRemovalConfigProvider implements IModConfigProvider
{
    @Override
    public Set<IModConfig> getConfigurationsForMod(ModContext context)
    {
        if(!BetterRemovalConfig.MOD_ID.equals(context.modId()))
        {
            return Set.of();
        }
        return Set.of(BetterRemovalConfig.get());
    }
}