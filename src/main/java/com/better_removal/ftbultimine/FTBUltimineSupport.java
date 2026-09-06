package com.better_removal.ftbultimine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * FTB Ultimine联动支持。反射访问，未安装时静默降级（isLoaded()=false）。
 *
 * 服务端按玩家维护连锁状态（Ultimine 2001.1.x，1.20.1）：
 *   FTBUltimine.instance（public static）-> getOrCreatePlayerData(player) -> FTBUltiminePlayerData
 *     - isPressed()：Ultimine键（默认~）按住状态，由客户端通过KeyPressedPacket同步
 *     - hasCachedPositions() / cachedPositions()：当前连锁形状覆盖的方块位置，
 *       玩家每tick刷新（playerTick -> checkBlocks），右击时也会针对点击位置刷新，因此始终新鲜
 *
 * 注：RightClickHandler API 在 Forge 端会经 RightClickDispatcher 分发，但为了与 Fabric 分支
 * 保持一致并避免编译期依赖，这里直接读取玩家数据，不注册handler。
 * 为兼容未来版本（1.21 起 instance 字段改为 private、改由 getInstance() 提供），
 * 解析时先尝试 instance 字段，失败后回退 getInstance() 方法。
 */
public final class FTBUltimineSupport {

	public static final String MOD_ID = "ftbultimine";

	private static final String MAIN_CLASS = "dev.ftb.mods.ftbultimine.FTBUltimine";
	private static final String PLAYER_DATA_CLASS = "dev.ftb.mods.ftbultimine.FTBUltiminePlayerData";

	private static final boolean LOADED = checkLoaded();

	/** 反射句柄在类加载时一次性解析 */
	private static final Object INSTANCE_RESOLVER;
	private static final Method GET_PLAYER_DATA;
	private static final Method IS_PRESSED;
	private static final Method HAS_CACHED_POSITIONS;
	private static final Method CACHED_POSITIONS;

	static {
		Field instanceField = null;
		Method getInstance = null;
		Method getPlayerData = null;
		Method isPressed = null;
		Method hasCachedPositions = null;
		Method cachedPositions = null;
		if (LOADED) {
			try {
				Class<?> main = Class.forName(MAIN_CLASS);
				Class<?> data = Class.forName(PLAYER_DATA_CLASS);
				try {
					Field field = main.getField("instance");
					field.setAccessible(true);
					instanceField = field;
				}
				catch (NoSuchFieldException ignored) {
					// 新版本 instance 为 private，改用 getInstance()
					getInstance = main.getMethod("getInstance");
				}
				getPlayerData = main.getMethod("getOrCreatePlayerData", Player.class);
				isPressed = data.getMethod("isPressed");
				hasCachedPositions = data.getMethod("hasCachedPositions");
				cachedPositions = data.getMethod("cachedPositions");
			}
			catch (Throwable t) {
				instanceField = null;
				getInstance = null;
				getPlayerData = null;
				isPressed = null;
				hasCachedPositions = null;
				cachedPositions = null;
			}
		}
		INSTANCE_RESOLVER = instanceField != null ? instanceField : (Method) getInstance;
		GET_PLAYER_DATA = getPlayerData;
		IS_PRESSED = isPressed;
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
	 * 是否安装了 FTB Ultimine 且反射句柄可用。
	 */
	public static boolean isLoaded() {
		return LOADED && INSTANCE_RESOLVER != null && GET_PLAYER_DATA != null;
	}

	/**
	 * 服务端：玩家当前是否按住Ultimine键。
	 */
	public static boolean isKeyHeld(Player player) {
		if (!isLoaded() || IS_PRESSED == null || player == null) {
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
	public static Collection<BlockPos> getShapePositions(Player player) {
		if (!isLoaded() || HAS_CACHED_POSITIONS == null || CACHED_POSITIONS == null || player == null) {
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

	private static Object getPlayerData(Player player) throws Exception {
		Object main;
		if (INSTANCE_RESOLVER instanceof Field field) {
			main = field.get(null);
		}
		else {
			main = ((Method) INSTANCE_RESOLVER).invoke(null);
		}
		if (main == null) {
			return null;
		}
		return GET_PLAYER_DATA.invoke(main, player);
	}
}
