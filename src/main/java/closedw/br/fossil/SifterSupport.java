package closedw.br.fossil;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Fossils and Archeology: Revival 筛子。
 */
public final class SifterSupport {

    private SifterSupport() {
    }

    public static final String MOD_ID = "fossil";

    private static final String SIFTER_CLASS = "com.github.teamfossilsarcheology.fossil.block.entity.SifterBlockEntity";

    private static final int[] OUTPUT_SLOTS = { 1, 2, 3, 4, 5 };

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

    public static boolean isSifter(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return false;
        }
        try {
            return Class.forName(SIFTER_CLASS).isInstance(blockEntity);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static int[] getOutputSlots() {
        return OUTPUT_SLOTS;
    }
}