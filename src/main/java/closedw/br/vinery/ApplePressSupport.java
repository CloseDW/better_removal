package closedw.br.vinery;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Vinery苹果压榨器。
 */
public final class ApplePressSupport {

    private ApplePressSupport() {
    }

    public static final String MOD_ID = "vinery";

    private static final String CLASS = "net.satisfy.vinery.core.block.entity.ApplePressBlockEntity";

    private static final boolean LOADED = FermentationBarrelSupport.isLoaded();

    public static boolean isApplePress(BlockEntity blockEntity) {
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