package com.example.jade;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * Jade入口。仅在安装了Jade的环境中运行。
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