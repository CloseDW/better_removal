package closedw.br.client;

import closedw.br.BetterRemoval;
import closedw.br.slot.SlotRole;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 指认会话期间把系统光标换成"带角色标记"的图标（滚轮换画笔，光标跟着换）。
 * <p>
 * 原版 Minecraft 不管理光标（1.20.1/1.21.1 的 MC 里都没有创建光标的代码，
 * 唯一用 GLFWImage 的地方是 {@code Window#setIcon}，那是窗口图标），所以这里直接调 GLFW：
 * {@code glfwCreateCursor} + {@code glfwSetCursor}，会话结束再还回系统默认光标。
 * <p>
 * 贴图路径与"缺图回退"都交给 {@link MappingIcons}（同一份资源也用来画区域右上角的标记）。
 */
public final class MappingCursors {

	private static final Logger LOGGER = BetterRemoval.LOGGER;

	/**
	 * 热点：图内像素坐标，系统把这一点对准鼠标位置。
	 * 默认 {@code (0,0)} —— 和 Windows 默认箭头一致（箭头尖端就在左上角）。
	 * 如果贴图里尖端画在别处，改这两个值（否则点击会偏）。
	 */
	private static final int HOTSPOT_X = 0;
	private static final int HOTSPOT_Y = 0;

	/** 会话期间每隔多少帧兜底重设一次光标（窗口失焦再聚焦时 GLFW 可能把它重置掉）。 */
	private static final int REFRESH_FRAMES = 60;

	/** 已经建好的光标句柄。 */
	private static final Map<SlotRole, Long> CACHE = new EnumMap<>(SlotRole.class);
	/** 贴图缺失/损坏的角色，记下来免得每帧重试。 */
	private static final Set<SlotRole> MISSING = EnumSet.noneOf(SlotRole.class);

	private static long currentHandle = -1L;
	private static int refreshCountdown;

	private MappingCursors() {
	}

	/**
	 * 按当前画笔设置光标；{@code role == null} 表示还没选画笔 → 用系统默认光标。
	 * 每帧调用即可（值没变且没到兜底间隔时不会真的去调 GLFW）。
	 */
	public static void apply(SlotRole role) {
		long window = window();
		if (window == 0L) {
			return;
		}
		long handle = role == null ? 0L : cursorFor(role);
		if (handle == currentHandle && --refreshCountdown > 0) {
			return;
		}
		refreshCountdown = REFRESH_FRAMES;
		setCursor(window, handle);
		currentHandle = handle;
	}

	/** 恢复系统默认光标，并释放本次会话建出来的光标（会话结束/界面关闭时调用）。 */
	public static void restore() {
		long window = window();
		if (window != 0L && currentHandle > 0L) {
			// 必须先换回默认再销毁，不然窗口可能还引用着已经释放的句柄
			setCursor(window, 0L);
		}
		currentHandle = -1L;
		refreshCountdown = 0;
		CACHE.values().forEach(MappingCursors::destroy);
		CACHE.clear();
	}

	// ---------- 内部 ----------

	private static long cursorFor(SlotRole role) {
		Long cached = CACHE.get(role);
		if (cached != null) {
			return cached;
		}
		if (MISSING.contains(role)) {
			return 0L;
		}
		long handle = create(role);
		if (handle == 0L) {
			MISSING.add(role);
			LOGGER.info("[mapping] cursor texture for {} is missing or unusable, keeping the default cursor", role);
			return 0L;
		}
		CACHE.put(role, handle);
		return handle;
	}

	/** 读 PNG 建一个 GLFW 光标；失败返回 0（调用方回退默认光标）。 */
	private static long create(SlotRole role) {
		try (NativeImage image = MappingIcons.open(role)) {
			if (image == null) {
				return 0L;
			}
			String file = String.valueOf(MappingIcons.texture(role));
			int width = image.getWidth();
			int height = image.getHeight();
			if (width <= 0 || height <= 0) {
				return 0L;
			}
			if (width > 64 || height > 64) {
				LOGGER.warn("[mapping] cursor {} is {}x{}; the OS scales it down and it will look blurry",
						file, width, height);
			}
			ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
			GLFWImage glfwImage = GLFWImage.malloc();
			try {
				// 1.20.1 的 NativeImage 没有 Mojang 的 getPixelsRGBA()，等价方法是 copyPixelsRgba()
				pixels.asIntBuffer().put(image.copyPixelsRgba());
				glfwImage.width(width);
				glfwImage.height(height);
				glfwImage.pixels(pixels);
				return GLFW.glfwCreateCursor(glfwImage, HOTSPOT_X, HOTSPOT_Y);
			}
			finally {
				glfwImage.free();
				MemoryUtil.memFree(pixels);
			}
		}
		catch (Throwable t) {
			LOGGER.warn("[mapping] failed to load cursor for {}: {}", role, t.toString());
			return 0L;
		}
	}

	private static void setCursor(long window, long handle) {
		try {
			GLFW.glfwSetCursor(window, handle);
		}
		catch (Throwable t) {
			// 某些平台/状态下设置失败不该拖垮渲染
		}
	}

	private static void destroy(long handle) {
		if (handle <= 0L) {
			return;
		}
		try {
			GLFW.glfwDestroyCursor(handle);
		}
		catch (Throwable ignored) {
			// 释放失败无所谓，最多留一个句柄
		}
	}

	private static long window() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return 0L;
		}
		return mc.getWindow().getHandle();
	}
}
