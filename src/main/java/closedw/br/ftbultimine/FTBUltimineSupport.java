package closedw.br.ftbultimine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * FTB Ultimine 联动支持（<b>Forge 1.20.1 专用实现</b>）。
 * <p>
 * ⚠ <b>为什么这一端不能像另三端那样"只走官方 API"</b>：Forge 1.20.1 的 FTB Ultimine 构建里
 * <b>根本没有</b> {@code dev.ftb.mods.ftbultimine.api.FTBUltimineAPI}。实测本机两个 Forge 构建：
 * <ul>
 *   <li>{@code ftb-ultimine-forge-2001.1.8.jar}：{@code api} 包下只有 {@code rightclick/*}，
 *       既没有 {@code FTBUltimineAPI.class}，也没有独立的 api jar</li>
 *   <li>{@code ftb-ultimine-forge-2001.1.5.jar}：同上</li>
 * </ul>
 * 而<b>同一个版本号</b>的 Fabric 构建 {@code ftb-ultimine-fabric-2001.1.8.jar} 里
 * {@code FTBUltimineAPI} 与 {@code FTBUltimineAPI$API} 都在。FTB 源码里
 * {@code FTBUltimineAPI.java} 位于 {@code common} 模块（本该每个加载器都有），
 * 所以那个 Forge 构建是<b>在 API 加进 common 之前</b>打的（FTB 的 1.21.1 分支已把 {@code forge}
 * 子项目注释掉，Forge 1.20.1 属遗留构建）。
 * <p>
 * 于是这一端只能反射 {@code api} 包之外的内部类。好在那个构建里
 * {@code FTBUltimine.instance} 是 <b>{@code public static}</b>（另两端的同名 jar 是 private），
 * 所以"公开字段 → {@code getOrCreatePlayerData(player)} → {@code cachedPositions()}"这条链是通的。
 * {@code getInstance()} 方法在该构建里<b>不存在</b>，故不尝试。
 * <p>
 * 上游若给 Forge 1.20.1 出了带 api 包的构建，本文件就可以整体换成与另三端相同的 api-only 实现。
 */
public final class FTBUltimineSupport {

    public static final String MOD_ID = "ftbultimine";

    private static final String MAIN_CLASS = "dev.ftb.mods.ftbultimine.FTBUltimine";
    private static final String PLAYER_DATA_CLASS = "dev.ftb.mods.ftbultimine.FTBUltiminePlayerData";

    private static final boolean LOADED = checkLoaded();

    private static final Field INSTANCE_FIELD;
    private static final Method GET_PLAYER_DATA;
    private static final Method HAS_CACHED_POSITIONS;
    private static final Method CACHED_POSITIONS;

    static {
        Field instanceField = null;
        Method getPlayerData = null;
        Method hasCachedPositions = null;
        Method cachedPositions = null;

        if (LOADED) {
            // 每一项单独 try/catch：某一项解析失败不该把其它句柄一起清掉
            try {
                Class<?> main = Class.forName(MAIN_CLASS);
                try {
                    // 该构建里 instance 是 public static；getField 只找 public 字段，正好命中
                    instanceField = main.getField("instance");
                }
                catch (Throwable ignored) {
                    instanceField = null;
                }
                try {
                    getPlayerData = main.getMethod("getOrCreatePlayerData", Player.class);
                }
                catch (Throwable ignored) {
                    getPlayerData = null;
                }
            }
            catch (Throwable t) {
                instanceField = null;
                getPlayerData = null;
            }

            try {
                Class<?> data = Class.forName(PLAYER_DATA_CLASS);
                try {
                    hasCachedPositions = data.getMethod("hasCachedPositions");
                }
                catch (Throwable ignored) {
                    hasCachedPositions = null;
                }
                try {
                    cachedPositions = data.getMethod("cachedPositions");
                }
                catch (Throwable ignored) {
                    cachedPositions = null;
                }
            }
            catch (Throwable t) {
                hasCachedPositions = null;
                cachedPositions = null;
            }
        }

        INSTANCE_FIELD = instanceField;
        GET_PLAYER_DATA = getPlayerData;
        HAS_CACHED_POSITIONS = hasCachedPositions;
        CACHED_POSITIONS = cachedPositions;
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
     * 是否安装了 FTB Ultimine（不保证反射接得上）。
     * 与 {@link #isLoaded()} 配对使用，才能区分"没装"和"装了但接不上"——后者必须告警。
     */
    public static boolean isInstalled() {
        return LOADED;
    }

    /**
     * 是否安装了 FTB Ultimine 且内部类反射可用。
     */
    public static boolean isLoaded() {
        return LOADED && hasPlayerDataPath();
    }

    private static boolean hasPlayerDataPath() {
        return INSTANCE_FIELD != null && GET_PLAYER_DATA != null
                && HAS_CACHED_POSITIONS != null && CACHED_POSITIONS != null;
    }

    /**
     * 服务端：当前连锁形状覆盖的方块位置；没有连锁选区（或反射不可用）时返回 null。
     * <p>
     * {@code hasCachedPositions()} 为假等价于"没按住 Ultimine 键或形状无效"，
     * 所以调用方不需要另做按键预检（另三端的 {@code currentBlockSelection} 同理）。
     */
    @SuppressWarnings("unchecked")
    public static Collection<BlockPos> getShapePositions(Player player) {
        if (!hasPlayerDataPath() || player == null) {
            return null;
        }
        try {
            Object main = INSTANCE_FIELD.get(null);
            if (main == null) {
                return null;
            }
            Object data = GET_PLAYER_DATA.invoke(main, player);
            if (data == null || !(Boolean) HAS_CACHED_POSITIONS.invoke(data)) {
                return null;
            }
            Object result = CACHED_POSITIONS.invoke(data);
            if (result instanceof Collection<?> positions && !positions.isEmpty()) {
                return (Collection<BlockPos>) positions;
            }
        }
        catch (Throwable t) {
            // 静默降级：连锁关闭，单容器取出/放入照常
        }
        return null;
    }
}
