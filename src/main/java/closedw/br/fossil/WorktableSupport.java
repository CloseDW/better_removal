package closedw.br.fossil;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Fossils and Archeology: Revival 考古工作台。
 */
public final class WorktableSupport {

    private WorktableSupport() {
    }

    public static final String MOD_ID = "fossil";

    private static final String WORKTABLE_CLASS = "com.github.teamfossilsarcheology.fossil.block.entity.WorktableBlockEntity";

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

    public static boolean isWorktable(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return false;
        }
        try {
            return Class.forName(WORKTABLE_CLASS).isInstance(blockEntity);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static int[] getOutputSlots() {
        return new int[] { 2 };
    }
}