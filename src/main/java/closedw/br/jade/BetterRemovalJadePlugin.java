package closedw.br.jade;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 插件入口（NeoForge 通过 @WailaPlugin 注解被 Jade 反射扫描发现）。
 * 仅在安装了 Jade 的环境中运行。
 */
@WailaPlugin
@SuppressWarnings("unused")
public class BetterRemovalJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new BetterRemovalServerData(), BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new ExtractionPreviewProvider(), Block.class);
    }
}