package closedw.br.fossil;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Fossils and Archeology: Revival 分析仪。
 */
public final class AnalyzerSupport {

    private AnalyzerSupport() {
    }

    public static final String MOD_ID = "fossil";

    private static final String ANALYZER_CLASS = "com.github.teamfossilsarcheology.fossil.block.entity.AnalyzerBlockEntity";

    private static final int[] OUTPUT_SLOTS = { 9, 10, 11, 12 };

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

    public static boolean isAnalyzer(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(ANALYZER_CLASS);
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