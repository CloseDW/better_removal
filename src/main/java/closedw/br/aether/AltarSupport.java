package closedw.br.aether;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 天境神能炉。
 */
public final class AltarSupport {

    private AltarSupport() {
    }

    public static final String MOD_ID = "aether";

    private static final String ALTAR_CLASS = "com.aetherteam.aether.blockentity.AltarBlockEntity";

    private static final boolean LOADED = FreezerSupport.isLoaded();

    public static boolean isAltar(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(ALTAR_CLASS);
            return clazz.isInstance(blockEntity);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static int[] getOutputSlots() {
        return new int[] { 2 };
    }
}