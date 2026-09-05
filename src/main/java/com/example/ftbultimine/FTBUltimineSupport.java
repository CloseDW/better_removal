package com.example.ftbultimine;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * FTB Ultimine联动支持。反射访问（isLoaded()=false）。
 * 服务端按玩家维护连锁状态：
 *   FTBUltimine.instance（public static）-> getOrCreatePlayerData(player) -> FTBUltiminePlayerData
 *     - isPressed()：Ultimine键按住状态，由客户端通过KeyPressedPacket同步
 *     - hasCachedPositions() / cachedPositions()：当前连锁形状覆盖的方块位置，
 */
public final class FTBUltimineSupport {

	public static final String MOD_ID = "ftbultimine";

	private static final String MAIN_CLASS = "dev.ftb.mods.ftbultimine.FTBUltimine";
	private static final String PLAYER_DATA_CLASS = "dev.ftb.mods.ftbultimine.FTBUltiminePlayerData";

	private static final boolean LOADED = checkLoaded();

	private static final Field INSTANCE_FIELD;
	private static final Method GET_PLAYER_DATA;
	private static final Method IS_PRESSED;
	private static final Method HAS_CACHED_POSITIONS;
	private static final Method CACHED_POSITIONS;

	static {
		Field instanceField = null;
		Method getPlayerData = null;
		Method isPressed = null;
		Method hasCachedPositions = null;
		Method cachedPositions = null;
		if (LOADED) {
			try {
				Class<?> main = Class.forName(MAIN_CLASS);
				Class<?> data = Class.forName(PLAYER_DATA_CLASS);
				instanceField = main.getField("instance");
				getPlayerData = main.getMethod("getOrCreatePlayerData", PlayerEntity.class);
				isPressed = data.getMethod("isPressed");
				hasCachedPositions = data.getMethod("hasCachedPositions");
				cachedPositions = data.getMethod("cachedPositions");
			}
			catch (Throwable t) {
				instanceField = null;
				getPlayerData = null;
				isPressed = null;
				hasCachedPositions = null;
				cachedPositions = null;
			}
		}
		INSTANCE_FIELD = instanceField;
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
	 * 是否安装了 FTB Ultimine 且反射句柄可用。
	 */
	public static boolean isLoaded() {
		return LOADED && INSTANCE_FIELD != null && GET_PLAYER_DATA != null;
	}

	/**
	 * 服务端：玩家当前是否按住Ultimine键。
	 */
	public static boolean isKeyHeld(PlayerEntity player) {
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
	public static Collection<BlockPos> getShapePositions(PlayerEntity player) {
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

	private static Object getPlayerData(PlayerEntity player) throws Exception {
		Object main = INSTANCE_FIELD.get(null);
		if (main == null) {
			return null;
		}
		return GET_PLAYER_DATA.invoke(main, player);
	}
}
