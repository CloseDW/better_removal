package closedw.br.crabbersdelight;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Crabber's Delight捕蟹笼。
 */
public final class CrabTrapSupport {

    private CrabTrapSupport() {
    }

    public static final String MOD_ID = "crabbersdelight";

    private static final String CRAB_TRAP_CLASS = "alabaster.crabbersdelight.common.block.entity.CrabTrapBlockEntity";

    private static final int[] CATCH_SLOTS = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };

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

    public static boolean isCrabTrap(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(CRAB_TRAP_CLASS);
            return clazz.isInstance(blockEntity);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static int[] getCatchSlots() {
        return CATCH_SLOTS;
    }
}