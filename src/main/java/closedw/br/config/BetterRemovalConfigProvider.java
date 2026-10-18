package closedw.br.config;

import com.mrcrayfish.configured.api.IModConfig;
import com.mrcrayfish.configured.api.IModConfigProvider;
import com.mrcrayfish.configured.api.ModContext;

import java.util.Set;

/**
 * Configured 的配置提供： custom.configured.providers中注册
 * <p>
 * 同一个 mod id 下暴露两个 {@link IModConfig}：
 * ① 可编辑的 {@link BetterRemovalConfig}（config/better-removal.properties）；
 * ② 只读的 {@link BetterRemovalMappingsConfig}（「已指认的容器」视图，不落盘）。
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
        return Set.of(BetterRemovalConfig.get(), BetterRemovalMappingsConfig.get());
    }
}
