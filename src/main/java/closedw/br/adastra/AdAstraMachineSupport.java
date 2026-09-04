package closedw.br.adastra;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Ad Astra机器方块实体的通用反射。
 */
public final class AdAstraMachineSupport {

    private AdAstraMachineSupport() {
    }

    public static final String MOD_ID = "ad_astra";

    private static final String MACHINE_BLOCK_ENTITY_CLASS = "earth.terrarium.adastra.common.blockentities.base.MachineBlockEntity";

    private static final boolean LOADED = checkLoaded();

    private static boolean checkLoaded() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        }
        catch (Throwable t) {
            return false;
        }
    }

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