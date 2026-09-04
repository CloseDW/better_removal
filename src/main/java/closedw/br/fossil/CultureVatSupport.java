package closedw.br.fossil;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Fossils and Archeology: Revival 培养槽。
 */
public final class CultureVatSupport {

    private CultureVatSupport() {
    }

    public static final String MOD_ID = "fossil";

    private static final String CULTURE_VAT_CLASS = "com.github.teamfossilsarcheology.fossil.block.entity.CultureVatBlockEntity";

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

    public static boolean isCultureVat(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return false;
        }
        try {
            return Class.forName(CULTURE_VAT_CLASS).isInstance(blockEntity);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static int[] getOutputSlots() {
        return new int[] { 2 };
    }
}