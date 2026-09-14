package closedw.br.cookingforblockheads;

import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Cooking for Blockheads烤炉的兼容。
 * 使用反射访问，try/catch。
 * 烤炉槽位布局（OvenBlockEntity 内部容器，共20格）：
 *   0-2   输入槽
 *   3     燃料槽
 *   4-6   输出槽
 *   7-15  3x3加工格
 *   16-19 工具槽
 * 烤炉方块实体本身不是Container，反射拿内部容器后复用通用读写。
 */
public final class CookingForBlockheadsSupport {

    private CookingForBlockheadsSupport() {
    }

    public static final String MOD_ID = "cookingforblockheads";

    private static final String OVEN_CLASS = "net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity";

    private static final String CONFIG_CLASS = "net.blay09.mods.cookingforblockheads.CookingForBlockheadsConfig";

    private static final int[] INPUT_SLOTS = { 0, 1, 2 };
    private static final int[] FUEL_SLOTS = { 3 };
    private static final int[] OUTPUT_SLOTS = { 4, 5, 6 };
    private static final int[] ALL_SLOTS = { 0, 1, 2, 3, 4, 5, 6 };

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

    /** 判断给定方块是否为烤炉（含全部染色变体，按方块实体类型判断）。 */
    public static boolean isOven(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return false;
        }
        try {
            return Class.forName(OVEN_CLASS).isInstance(blockEntity);
        }
        catch (Throwable t) {
            return false;
        }
    }

    /**
     * CFB配置中的“禁止烤炉自动化”（disallowOvenAutomation）是否开启。
     * 开启时本模组对烤炉不动作。读不到配置时按未开启处理。
     */
    public static boolean isAutomationDisallowed() {
        if (!LOADED) {
            return false;
        }
        try {
            Class<?> configClass = Class.forName(CONFIG_CLASS);
            Object data = configClass.getMethod("getActive").invoke(null);
            if (data == null) {
                return false;
            }
            return data.getClass().getField("disallowOvenAutomation").getBoolean(data);
        }
        catch (Throwable t) {
            return false;
        }
    }

    /**
     * 获取烤炉的内部容器。返回null表示不可用。
     * 注意：开关由调用方通过 isAutomationDisallowed() 检查。
     */
    public static Container getInternalContainer(BlockEntity blockEntity) {
        if (!LOADED || blockEntity == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(OVEN_CLASS);
            if (!clazz.isInstance(blockEntity)) {
                return null;
            }
            Object container = clazz.getMethod("getInternalContainer").invoke(blockEntity);
            return container instanceof Container c ? c : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    public static int[] getInputSlots() {
        return INPUT_SLOTS;
    }

    public static int[] getFuelSlots() {
        return FUEL_SLOTS;
    }

    public static int[] getOutputSlots() {
        return OUTPUT_SLOTS;
    }

    public static int[] getAllSlots() {
        return ALL_SLOTS;
    }
}
