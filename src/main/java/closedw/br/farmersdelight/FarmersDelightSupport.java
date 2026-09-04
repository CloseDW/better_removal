package closedw.br.farmersdelight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Farmer's Delight兼容（原版）。
 * 厨锅通过反射访问 getInventory() 返回的 ItemStackHandler。
 */
public final class FarmersDelightSupport {

    private FarmersDelightSupport() {
    }

    public static final String MOD_ID = "farmersdelight";

    private static final String COOKING_POT_CLASS = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity";

    private static final String BASKET_CLASS = "vectorwing.farmersdelight.common.block.entity.BasketBlockEntity";

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

    public static boolean isCookingPot(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(COOKING_POT_CLASS);
            return clazz.isInstance(blockEntity);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static boolean isBasket(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(BASKET_CLASS);
            return clazz.isInstance(blockEntity);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static ItemStack getSlot(Level level, BlockPos pos, BlockEntity blockEntity, int slot) {
        if (!LOADED || blockEntity == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(COOKING_POT_CLASS);
            if (!clazz.isInstance(blockEntity)) {
                return null;
            }
            Object inventory = clazz.getMethod("getInventory").invoke(blockEntity);
            if (inventory == null) {
                return null;
            }
            Object stackObj = inventory.getClass().getMethod("getStackInSlot", int.class).invoke(inventory, slot);
            ItemStack stack = (ItemStack) stackObj;
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            return stack.copy();
        }
        catch (Throwable t) {
            return null;
        }
    }

    public static void removeFromSlot(Level level, BlockPos pos, BlockEntity blockEntity, int slot, int count) {
        if (!LOADED || blockEntity == null || count <= 0) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(COOKING_POT_CLASS);
            if (!clazz.isInstance(blockEntity)) {
                return;
            }
            Object inventory = clazz.getMethod("getInventory").invoke(blockEntity);
            if (inventory == null) {
                return;
            }
            Object stackObj = inventory.getClass().getMethod("getStackInSlot", int.class).invoke(inventory, slot);
            ItemStack stack = (ItemStack) stackObj;
            if (stack == null || stack.isEmpty()) {
                return;
            }
            if (count >= stack.getCount()) {
                inventory.getClass().getMethod("setStackInSlot", int.class, ItemStack.class)
                        .invoke(inventory, slot, ItemStack.EMPTY);
            }
            else {
                stack.shrink(count);
                inventory.getClass().getMethod("setStackInSlot", int.class, ItemStack.class)
                        .invoke(inventory, slot, stack);
            }
        }
        catch (Throwable t) {

        }
    }
}