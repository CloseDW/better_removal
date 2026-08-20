package com.example.adastra;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;

/**
 * Ad Astra机器方块实体的通用反射。
 *
 * 压缩机、电离高炉、燃料精炼机、氧气装载机、低温冷冻机等都继承自{@code MachineBlockEntity}，其中包含一个 public的{@code sync()}方法：
 *
 * 本模组使用原版{@link net.minecraft.inventory.Inventory}接口直接取出输出槽物品时，
 * 没有经过Ad Astra机器自身的update/sync流程。因此在取物后需要调用sync()让机器把正确的方块实体数据推送给客户端。
 */
public final class AdAstraMachineSupport {

	private AdAstraMachineSupport() {
	}

	/**
	 * mod id
	 */
	public static final String MOD_ID = "ad_astra";

	/**
	 * 机器方块实体的公共父类
	 */
	private static final String MACHINE_BLOCK_ENTITY_CLASS = "earth.terrarium.adastra.common.blockentities.base.MachineBlockEntity";

	private static final boolean LOADED = checkLoaded();

	private static boolean checkLoaded() {
		try {
			return FabricLoader.getInstance().isModLoaded(MOD_ID);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 判断给定方块实体是否为Ad Astra机器
	 */
	public static boolean isAdAstraMachine(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName(MACHINE_BLOCK_ENTITY_CLASS);
			return clazz.isInstance(blockEntity);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 调用sync()方法，把正确的方块实体数据推送给客户端
	 */
	public static void sync(BlockEntity blockEntity) {
		if (!LOADED || blockEntity == null) {
			return;
		}
		try {
			Class<?> clazz = Class.forName(MACHINE_BLOCK_ENTITY_CLASS);
			if (!clazz.isInstance(blockEntity)) {
				return;
			}
			clazz.getMethod("sync").invoke(blockEntity);
		}
		catch (Throwable t) {
			// 静默忽略
		}
	}
}
