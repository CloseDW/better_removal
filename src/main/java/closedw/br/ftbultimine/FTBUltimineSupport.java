package closedw.br.ftbultimine;

import net.neoforged.fml.ModList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

/**
 * FTB Ultimine 联动支持
 */
public final class FTBUltimineSupport {

    public static final String MOD_ID = "ftbultimine";

    private static final String API_CLASS = "dev.ftb.mods.ftbultimine.api.FTBUltimineAPI";
    private static final String API_INTERFACE_CLASS = "dev.ftb.mods.ftbultimine.api.FTBUltimineAPI$API";

    private static final boolean LOADED = checkLoaded();

    private static final Method API_GETTER;
    private static final Method API_CURRENT_SELECTION;

    static {
        Method apiGetter = null;
        Method apiCurrentSelection = null;

        if (LOADED) {
            try {
                Class<?> apiClass = Class.forName(API_CLASS);
                Class<?> apiInterface = Class.forName(API_INTERFACE_CLASS);
                apiGetter = apiClass.getMethod("api");
                apiCurrentSelection = apiInterface.getMethod("currentBlockSelection", Player.class);
            }
            catch (Throwable t) {
                apiGetter = null;
                apiCurrentSelection = null;
            }
        }

        API_GETTER = apiGetter;
        API_CURRENT_SELECTION = apiCurrentSelection;
    }

    private FTBUltimineSupport() {
    }

    private static boolean checkLoaded() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        }
        catch (Throwable t) {
            return false;
        }
    }

    /**
     * 是否安装了 FTB Ultimine 且官方 API 反射可用。
     */
    public static boolean isLoaded() {
        return LOADED && hasApiPath();
    }

    private static boolean hasApiPath() {
        return API_GETTER != null && API_CURRENT_SELECTION != null;
    }

    /**
     * 服务端：当前连锁形状覆盖的方块位置；没有连锁选区（或 API 不可用）时返回 null。
     */
    @SuppressWarnings("unchecked")
    public static Collection<BlockPos> getShapePositions(Player player) {
        if (!hasApiPath() || player == null) {
            return null;
        }
        try {
            Object api = API_GETTER.invoke(null);
            if (api == null) {
                return null;
            }
            Object result = API_CURRENT_SELECTION.invoke(api, player);
            if (result instanceof Optional<?> optional
                    && optional.isPresent()
                    && optional.get() instanceof Collection<?> positions
                    && !positions.isEmpty()) {
                return (Collection<BlockPos>) positions;
            }
        }
        catch (Throwable t) {
            // 静默降级：连锁关闭，单容器取出/放入照常
        }
        return null;
    }
}
