package common;

/**
 * 主源集持有 Carry On 兼容键（左Alt）的按下状态。
 * 由客户端 tick 更新，供 Jade 预览（主源集）读取，避免 main 引用 client 源集。
 */
public final class CarryOnKeyState {

	private static volatile boolean pressed = false;

	private CarryOnKeyState() {
	}

	public static void setPressed(boolean value) {
		pressed = value;
	}

	public static boolean isPressed() {
		return pressed;
	}
}