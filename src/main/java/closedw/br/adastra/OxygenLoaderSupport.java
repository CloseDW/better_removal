package closedw.br.adastra;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Ad Astra 氧气装载机的适配。
 */
public final class OxygenLoaderSupport {

    private OxygenLoaderSupport() {
    }

    public static final String MOD_ID = "ad_astra";

    private static final String CLASS = "earth.terrarium.adastra.common.blockentities.machines.OxygenLoaderBlockEntity";

    private static final int[] OUTPUT_SLOTS = { 2, 4 };

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

    public static boolean isOxygenLoader(BlockEntity blockEntity) {
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
        return OUTPUT_SLOTS;
    }
}