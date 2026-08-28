package common.jade;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * Jade 插件入口。通过 fabric.mod.json 的 "jade" entrypoint 加载。
 * 仅在安装了 Jade 且加载本插件的环境中运行。
 */
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