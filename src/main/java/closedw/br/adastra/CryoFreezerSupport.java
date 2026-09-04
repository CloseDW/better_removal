package closedw.br.adastra;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Ad Astra 低温冷冻机的适配。
 */
public final class CryoFreezerSupport {

    private CryoFreezerSupport() {
    }

    public static final String MOD_ID = "ad_astra";

    private static final String CLASS = "earth.terrarium.adastra.common.blockentities.machines.CryoFreezerBlockEntity";

    private static final boolean LOADED = checkLoaded();

    private static boolean checkLoaded() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isCryoFreezer(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(CLASS);
            return clazz.isInstance(blockEntity);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static int[] getOutputSlots() {
        return new int[] { 3 };
    }
}