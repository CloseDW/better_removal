package closedw.br.ftbultimine;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

/**
 * FTB Ultimine 联动支持。全部通过反射访问，因此不会产生硬依赖（isInstalled()=false 时自动关闭）。
 * 为了同时兼容改造前/改造后的 FTB Ultimine 1.20.1，这里按优先级尝试三条路径：
 *   1. 新版公开 API：FTBUltimineAPI.api().currentBlockSelection(player) -> Optional&lt;Collection&lt;BlockPos&gt;&gt;
 *   2. 新式单例方法：FTBUltimine.getInstance() -&gt; getOrCreatePlayerData(player)
 *   3. 旧版公开字段：FTBUltimine.instance -&gt; getOrCreatePlayerData(player)
 * 服务端按玩家维护连锁状态：
 *   FTBUltiminePlayerData
 *     - isPressed()：Ultimine键按住状态，由客户端通过 KeyPressedPacket 同步
 *     - hasCachedPositions() / cachedPositions()：当前连锁形状覆盖的方块位置
 */
public final class FTBUltimineSupport {

	public static final String MOD_ID = "ftbultimine";

	private static final String MAIN_CLASS = "dev.ftb.mods.ftbultimine.FTBUltimine";
	private static final String PLAYER_DATA_CLASS = "dev.ftb.mods.ftbultimine.FTBUltiminePlayerData";
	private static final String API_CLASS = "dev.ftb.mods.ftbultimine.api.FTBUltimineAPI";
	private static final String API_INTERFACE_CLASS = "dev.ftb.mods.ftbultimine.api.FTBUltimineAPI$API";

	private static final boolean LOADED = checkLoaded();

	// 路径1：新版公开 API
	private static final Method API_GETTER;
	private static final Method API_CURRENT_SELECTION;

	// 路径2/3：玩家数据
	private static final Field INSTANCE_FIELD;
	private static final Method INSTANCE_GETTER;
	private static final Method GET_PLAYER_DATA;
	private static final Method IS_PRESSED;
	private static final Method HAS_CACHED_POSITIONS;
	private static final Method CACHED_POSITIONS;

	static {
		Method apiGetter = null;
		Method apiCurrentSelection = null;
		Field instanceField = null;
		Method instanceGetter = null;
		Method getPlayerData = null;
		Method isPressed = null;
		Method hasCachedPositions = null;
		Method cachedPositions = null;

		if (LOADED) {
			// 路径1：新版 API（1.21.1 风格，已同步到 1.20.1）
			try {
				Class<?> apiClass = Class.forName(API_CLASS);
				Class<?> apiInterface = Class.forName(API_INTERFACE_CLASS);
				apiGetter = apiClass.getMethod("api");
				apiCurrentSelection = apiInterface.getMethod("currentBlockSelection", PlayerEntity.class);
			}
			catch (Throwable t) {
				apiGetter = null;
				apiCurrentSelection = null;
			}

			// 路径2/3：玩家数据（isKeyHeld 无论如何都需要）
			try {
				Class<?> main = Class.forName(MAIN_CLASS);
				Class<?> data = Class.forName(PLAYER_DATA_CLASS);

				try {
					// 改造后 instance 为 private，getField 会抛 NoSuchFieldException
					instanceField = main.getField("instance");
				}
				catch (Throwable ignored) {
					instanceField = null;
				}

				if (instanceField == null) {
					try {
						instanceGetter = main.getMethod("getInstance");
					}
					catch (Throwable ignored) {
						instanceGetter = null;
					}
				}

				getPlayerData = main.getMethod("getOrCreatePlayerData", PlayerEntity.class);
				isPressed = data.getMethod("isPressed");
				hasCachedPositions = data.getMethod("hasCachedPositions");
				cachedPositions = data.getMethod("cachedPositions");
			}
			catch (Throwable t) {
				instanceField = null;
				instanceGetter = null;
				getPlayerData = null;
				isPressed = null;
				hasCachedPositions = null;
				cachedPositions = null;
			}
		}

		API_GETTER = apiGetter;
		API_CURRENT_SELECTION = apiCurrentSelection;
		INSTANCE_FIELD = instanceField;
		INSTANCE_GETTER = instanceGetter;
		GET_PLAYER_DATA = getPlayerData;
		IS_PRESSED = isPressed;
		HAS_CACHED_POSITIONS = hasCachedPositions;
		CACHED_POSITIONS = cachedPositions;
	}

	private FTBUltimineSupport() {
	}

	private static boolean checkLoaded() {
		try {
			return FabricLoader.getInstance().isModLoaded(MOD_ID);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 是否安装了 FTB Ultimine。
	 */
	public static boolean isInstalled() {
		return LOADED;
	}

	/**
	 * 是否安装了 FTB Ultimine 且至少一条反射路径可用。
	 */
	public static boolean isLoaded() {
		return LOADED && (hasPlayerDataPath() || hasApiPath());
	}

	/**
	 * 是否可用新版公开 API 查询连锁选区。
	 */
	public static boolean hasApiPath() {
		return API_GETTER != null && API_CURRENT_SELECTION != null;
	}

	private static boolean hasPlayerDataPath() {
		return GET_PLAYER_DATA != null && (INSTANCE_FIELD != null || INSTANCE_GETTER != null);
	}

	/**
	 * 服务端：玩家当前是否按住Ultimine键。
	 */
	public static boolean isKeyHeld(PlayerEntity player) {
		if (!LOADED || IS_PRESSED == null || player == null) {
			return false;
		}
		try {
			Object data = getPlayerData(player);
			if (data == null) {
				return false;
			}
			return (Boolean) IS_PRESSED.invoke(data);
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 服务端：当前连锁形状覆盖的方块位置。不可用时返回null。
	 */
	@SuppressWarnings("unchecked")
	public static Collection<BlockPos> getShapePositions(PlayerEntity player) {
		if (!LOADED || player == null) {
			return null;
		}

		// 路径1：优先走新版公开 API
		if (hasApiPath()) {
			try {
				Object api = API_GETTER.invoke(null);
				if (api != null) {
					Object result = API_CURRENT_SELECTION.invoke(api, player);
					if (result instanceof Optional<?> optional) {
						if (optional.isPresent() && optional.get() instanceof Collection<?> positions && !positions.isEmpty()) {
							return (Collection<BlockPos>) positions;
						}
						// API 可用时它就是权威结果：没有选区就是没有
						return null;
					}
				}
			}
			catch (Throwable t) {
				// 落到下面的玩家数据路径
			}
		}

		// 路径2/3：直接读玩家数据
		if (HAS_CACHED_POSITIONS == null || CACHED_POSITIONS == null) {
			return null;
		}
		try {
			Object data = getPlayerData(player);
			if (data == null || !(Boolean) HAS_CACHED_POSITIONS.invoke(data)) {
				return null;
			}
			Object result = CACHED_POSITIONS.invoke(data);
			if (result instanceof Collection<?> positions && !positions.isEmpty()) {
				return (Collection<BlockPos>) positions;
			}
		}
		catch (Throwable t) {
			// 静默忽略
		}
		return null;
	}

	private static Object getPlayerData(PlayerEntity player) throws Exception {
		Object main = null;
		if (INSTANCE_GETTER != null) {
			main = INSTANCE_GETTER.invoke(null);
		}
		else if (INSTANCE_FIELD != null) {
			main = INSTANCE_FIELD.get(null);
		}
		if (main == null || GET_PLAYER_DATA == null) {
			return null;
		}
		return GET_PLAYER_DATA.invoke(main, player);
	}
}
