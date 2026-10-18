package closedw.br.client;

import closedw.br.BetterRemoval;
import closedw.br.BetterRemovalClient;
import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionModeManager;
import closedw.br.mapping.MappingSessionState;
import closedw.br.networking.MappingUpdateC2SPacket;
import closedw.br.slot.SlotRole;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手动指认的客户端部分：把容器界面里的槽位标成"输入 / 燃料 / 输出 / 忽略"。
 * <p>
 * 交互（全部在按住修饰键的前提下进行，默认左 Alt、可改键）：
 * <ol>
 *   <li>指认模式下修饰键右击容器 —— 界面照常打开</li>
 *   <li><b>按住修饰键，滚轮选"画笔"</b>：输入 → 输出 → 燃料 → 忽略。第一次滚动把画笔设为"输入"，
 *       并且立刻涂到当前悬停的槽位上</li>
 *   <li><b>按住修饰键，把鼠标滑过 / 停在槽位上</b>（约 {@link #DWELL_MS} 毫秒）就涂上当前画笔——
 *       这就是"一片一片涂"的方式，不必每格再滚一次</li>
 *   <li>想看不想改：松开修饰键，滚轮与悬停都不会改动任何东西</li>
 * </ol>
 * 画笔没选之前（{@code brush == null}）滑过也不会改动任何东西，避免误擦已有标签。
 * <p>
 * <b>刻意不接管任何鼠标按键</b>：锁槽 / 拖拽搬运类模组（IPN、malilib 系的模组）也用
 * 修饰键+鼠标划动，而且它们的输入走自己的管线，甚至在事件总线之前就把点击吞掉——
 * 我们一旦也用鼠标键，轻则两个功能同时触发，重则连事件都收不到（拖拽直接失效）。
 * 只靠"修饰键+鼠标移动"就没有这个问题：不按键，谁都不会来抢。
 * 于是槽位里原来的点击行为完全不受影响（左键搬物品、右键分堆照旧）。
 * 角色表以服务端回传的权威值为准，本地先乐观更新一次，免得有延迟感。
 * <p>
 * 叠层（非矩形区域分组 + 区域右上角小小的 I/O/F + GUI 上沿外侧的画笔状态条）一律用「GUI 内部坐标」画——
 * 详见 {@link #renderForeground} 里的坐标系说明。
 * <p>
 * <b>1.20.1 移植说明</b>：容器前景的渲染挂点由
 * {@code closedw.br.mixin.client.HandledScreenMappingMixin} 提供（注入 {@code HandledScreen#drawForeground}
 * 调用之后，语义与 NeoForge 的 {@code ContainerScreenEvent.Render.Foreground} 一致）；
 * 界面原点与"鼠标下的槽位"通过 {@link MappingScreenAccess} 取。
 */
public final class MappingSession {

	/** 角色色：输入=绿、燃料=金、输出=青（用作区域底色/边框/标签胶囊的基色）。 */
	private static final int COLOR_INPUT = 0xFF55FF55;
	private static final int COLOR_FUEL = 0xFFFFAA00;
	private static final int COLOR_OUTPUT = 0xFF55FFFF;
	/** GUI 上沿外侧那条画笔状态条：高度 / 九宫格端帽与边宽 / 文字配色。 */
	private static final int BAR_HEIGHT = 12;
	/** 九宫格左右端帽宽度（源图里是 1px 黑边 + 2px 高光或阴影）。 */
	private static final int BAR_CAP = 3;
	/** 九宫格上下边高度（源图里是 1px 边线）。 */
	private static final int BAR_EDGE = 1;
	/** 浅灰底上用原版深灰文字；出错用深红。 */
	private static final int COLOR_BAR_TEXT = 0xFF404040;
	private static final int COLOR_BAR_ERROR = 0xFFAA0000;
	/** 贴图缺失时的兜底配色（原面板同色）。 */
	private static final int COLOR_BAR_FALLBACK = 0xFFC6C6C6;
	private static final int COLOR_BAR_FALLBACK_EDGE = 0xFF000000;
	/** 被"忽略"擦掉的槽位标记（灰色小叉）。 */
	private static final int COLOR_ERASED = 0xA0AAAAAA;

	/** 单次滚轮事件最多前进几档，防止暴力滚动一下子跳完一圈。 */
	private static final int MAX_STEPS_PER_EVENT = 4;

	/**
	 * 同一个槽位上停留多久才涂上画笔（毫秒）。
	 * <p>
	 * 调得短 = "滑过就涂"，一次能刷一大片；调得长 = 要停一下才涂，不容易蹭到路过的槽位。
	 * 现在是"滑过就涂"这一档：修饰键按住时鼠标扫过的槽位（每格停够这么久）都会被涂上，
	 * 想精确一点就在目标槽位上多停一拍、或者松开修饰键再移开。
	 */
	private static final long DWELL_MS = 90L;

	/** 区域右上角那个小标记的底色（半透明深色，压着物品也能看清字母）。 */
	private static final int COLOR_BADGE_BG = 0x90000000;

	/** 按住修饰键时记录下来的槽位（菜单索引）；-1 表示当前没有记录。 */
	private static int recordedSlot = -1;

	/**
	 * 本次指认会话所属的那一个界面。
	 * <p>
	 * 叠层与滚轮都只对这个界面生效——否则会话还活着时打开玩家背包（或任何别的容器界面），
	 * 标签就会画到不相干的界面上（两者的菜单槽位号根本不是一套）。
	 * 会话结束时会由收包处把它清空（见 {@link #onSessionEnded()}），不依赖渲染钩子。
	 */
	private static volatile Screen sessionScreen;

	private static final Logger LOGGER = BetterRemoval.LOGGER;

	/** 诊断用：一次会话里每种"没生效的原因"只记一条日志，避免刷屏。收包线程与渲染线程都会碰它，必须并发安全。 */
	private static final Set<String> DIAGNOSED = ConcurrentHashMap.newKeySet();

	/** 滚轮选中的画笔角色；悬停到槽位就涂上它。{@code null} = 还没选，此时悬停不改动任何东西。 */
	private static SlotRole brush;
	/** 画笔停顿判定：当前停住的槽位与停住时刻。 */
	private static int dwellSlot = -1;
	private static long dwellSince;
	/** 本次会话里被"忽略"画笔擦掉的槽位（只用于画个灰叉当反馈）。 */
	private static final Set<Integer> erasedSlots = new LinkedHashSet<>();
	/**
	 * 滚轮累积量：滚轮一次"咔哒"可能拆成多个小数事件，累积够 1 档才切换一次，
	 * 免得一滚就跳过好几档。
	 */
	private static double scrollAccumulator;

	private MappingSession() {
	}

	/** 指认会话是否处于激活状态（服务端说了算，客户端模式也要还在指认上）。 */
	private static boolean active() {
		return MappingSessionState.isActive()
				&& InteractionModeManager.getClientState().behavior() == Behavior.MAPPING;
	}

	/**
	 * 滚轮换画笔；返回 true 表示这次滚动已被消费。
	 * <p>
	 * <b>必须按住修饰键</b>：修饰键是"指认操作"的总开关——换画笔与涂色都只在按住它时生效，
	 * 松开修饰键后滚轮完全不响应（连"滚一下切到输入"也不做）。这是有意的设计：
	 * 会话期间玩家可能松手去看/去点别的东西，此时滚轮不该改动任何状态。
	 *
	 * @param mouseX / mouseY 缩放后的屏幕坐标（与渲染回调同一套）
	 * @param scrollDeltaY   与 NeoForge {@code getScrollDeltaY()} 同义（向上为正）
	 */
	public static boolean onMouseScrolled(HandledScreen<?> screen, double mouseX, double mouseY, double scrollDeltaY) {
		if (!active()) {
			return false;
		}
		if (ModeWheel.isOpen()) {
			note("the mode wheel is open");
			return false;
		}
		if (screen == null) {
			return false;
		}
		// 修饰键是"指认操作"的总开关：没按住就什么都不做（换画笔也不做）
		if (!BetterRemovalClient.isExtractKeyPhysicallyDown()) {
			note("the modifier key is not held");
			return false;
		}
		if (!bindScreen(screen)) {
			// 会话还开着，但玩家打开的是别的界面（玩家背包等）：不响应、也不消费滚动
			note("the open screen is not the mapped container: " + screen.getClass().getSimpleName());
			return false;
		}
		// 鼠标得在界面范围内：这样"在界面里滚轮就换画笔和光标"，又不会抢 JEI 物品栏那种界面外的滚动
		if (!insideScreen(screen, mouseX, mouseY)) {
			note("the mouse is outside the GUI");
			return false;
		}
		// 刷新"记录槽位"（高亮用）；真正要涂的目标另算：只有鼠标正停在槽位上才涂
		updateRecordedSlot(screen, mouseX, mouseY);
		Slot hovered = hoveredUsableSlot(screen, mouseX, mouseY);
		double delta = scrollDeltaY;
		if (delta == 0) {
			return false;
		}
		// 滚轮向下 = 前进一档（和本模组模式滚轮的约定一致：向上是上一个、向下是下一个）
		delta = -delta;
		if (Math.signum(delta) != Math.signum(scrollAccumulator)) {
			// 方向反转：丢掉半格累积，免得来回滚时"卡住不动"
			scrollAccumulator = 0;
		}
		scrollAccumulator += delta;
		int steps = (int) scrollAccumulator;
		if (steps == 0) {
			// 还没凑够一档：先吃掉这次滚动，别让下面的界面跟着滚
			return true;
		}
		scrollAccumulator -= steps;
		steps = Math.max(-MAX_STEPS_PER_EVENT, Math.min(MAX_STEPS_PER_EVENT, steps));
		cycleBrush(steps);
		// 诊断：每种画笔每次会话只记一条，用来确认滚轮到底有没有生效
		trace("brush is now " + brush);
		// 按住修饰键、且鼠标正停在槽位上，就顺手涂上；
		// 只是想把画笔/光标换一支的话，不会动到"最后停过的那一格"
		if (hovered != null && BetterRemovalClient.isExtractKeyPhysicallyDown()) {
			applyBrush(hovered.getIndex());
			// 同一格刚涂过，重置停顿计时，避免紧接着又走一次停顿涂抹
			dwellSlot = hovered.getIndex();
			dwellSince = System.currentTimeMillis();
		}
		return true;
	}

	/** 诊断日志：滚轮被挡下时记一条，同一条原因每次会话只记一次。 */
	private static void note(String reason) {
		if (DIAGNOSED.add(reason)) {
			LOGGER.info("[mapping] scroll ignored: {}", reason);
		}
	}

	/** 诊断日志：正常事件（不是"被挡下"），同样每种每次会话只记一条。 */
	private static void trace(String what) {
		if (DIAGNOSED.add(what)) {
			LOGGER.info("[mapping] {}", what);
		}
	}

	/** 鼠标（屏幕坐标）是否落在界面矩形内。 */
	private static boolean insideScreen(HandledScreen<?> screen, double mouseX, double mouseY) {
		MappingScreenAccess access = (MappingScreenAccess) screen;
		double x = mouseX - access.betterremoval$guiLeft();
		double y = mouseY - access.betterremoval$guiTop();
		return x >= 0 && x < access.betterremoval$backgroundWidth() && y >= 0 && y < access.betterremoval$backgroundHeight();
	}

	/** 换画笔：正数前进（输入→输出→燃料→忽略），负数后退。第一次滚轮先把画笔设成"输入"。 */
	private static void cycleBrush(int steps) {
		if (brush == null) {
			// 还没选过画笔：第一次滚动先把最常见的那支选上；方向忽略
			brush = SlotRole.INPUT;
			return;
		}
		int count = Math.abs(steps);
		for (int i = 0; i < count; i++) {
			brush = steps > 0 ? brush.next() : brush.previous();
		}
	}

	/**
	 * 把当前画笔涂到某个槽位；没选画笔、或角色本来就一致时不发包。
	 */
	private static void applyBrush(int menuIndex) {
		if (menuIndex < 0 || brush == null) {
			return;
		}
		SlotRole current = MappingSessionState.getRole(menuIndex);
		if (current == brush) {
			return;
		}
		MappingSessionState.setLocalRole(menuIndex, brush);
		// 擦掉（忽略画笔）时留个灰叉当反馈；重新上色就把叉去掉
		if (brush == SlotRole.NONE) {
			erasedSlots.add(menuIndex);
		}
		else {
			erasedSlots.remove(menuIndex);
		}
		ClientPlayNetworking.send(new MappingUpdateC2SPacket(menuIndex, brush));
	}

	/**
	 * 滑过就涂：按住修饰键时，鼠标在某个槽位上停够 {@link #DWELL_MS} 就把它涂成当前画笔。
	 * <p>
	 * 这是现在唯一的涂色方式（不再用鼠标按键，见类注释）：想涂一排就按住修饰键扫过去，
	 * 鼠标停在某格上也会涂上。同一个槽位只会被涂一次（{@link #applyBrush} 里值没变就跳过），
	 * 所以停在原地不会反复改；换到别的槽位会重新计时。
	 */
	private static void applyBrushOnDwell() {
		if (recordedSlot < 0) {
			dwellSlot = -1;
			return;
		}
		long now = System.currentTimeMillis();
		if (dwellSlot != recordedSlot) {
			dwellSlot = recordedSlot;
			dwellSince = now;
			return;
		}
		if (now - dwellSince < DWELL_MS) {
			return;
		}
		applyBrush(recordedSlot);
	}

	/** 会话开始/结束时的复位：记录槽位、画笔、停顿判定。 */
	private static void resetSessionState() {
		recordedSlot = -1;
		brush = null;
		dwellSlot = -1;
		erasedSlots.clear();
		scrollAccumulator = 0;
	}

	/** 会话结束：连"这个会话属于哪个界面"一起清掉，并把换过的系统光标还回去。只在渲染线程调用。 */
	private static void endSessionState() {
		onSessionEnded();
		resetSessionState();
		MappingCursors.restore();
	}

	/**
	 * 会话结束（服务端已经通知）：立刻丢掉绑定的界面引用。
	 * <p>
	 * <b>必须由收包处主动调用</b>，不能只靠 {@link #renderForeground} 里的 {@code !active()} 分支：
	 * 会话结束的典型时刻正是"没有容器界面在渲染"（玩家关掉 GUI，服务端才收到关闭并结束会话），
	 * 那时渲染钩子根本不会跑，旧的 {@code Screen} 引用就会一直留着——
	 * 下一个会话打开的是<b>新的</b> Screen 实例，永远匹配不上，于是"所有容器都指认不了"。
	 * <p>
	 * 这里刻意不碰画笔与系统光标：它们只在界面里可见，由渲染钩子按 {@code !active()} 收尾。
	 */
	public static void onSessionEnded() {
		sessionScreen = null;
		DIAGNOSED.clear();
	}

	/**
	 * 把会话绑定到"它所属的那个界面"，并回答给定界面是不是它。
	 * <p>
	 * 判据是<b>会话的菜单槽位总数</b>（服务端下发，权威）：对不上就说明这不是会话所属的容器
	 * （玩家背包 46 格、别的容器格数不同），一律不画、不响应滚轮。
	 * 对得上就改绑到当前这个界面——同一个容器被关掉再打开会得到新的 Screen 实例，
	 * 而会话结束发生在没有界面渲染的时候，不能指望旧引用被及时清掉。
	 */
	private static boolean bindScreen(HandledScreen<?> screen) {
		if (screen == null) {
			return false;
		}
		int expected = MappingSessionState.getMenuSize();
		if (expected <= 0 || screen.getScreenHandler().slots.size() != expected) {
			return false;
		}
		if (sessionScreen == screen) {
			return true;
		}
		LOGGER.info("[mapping] {} {} (menuSize={})",
				sessionScreen == null ? "session bound to" : "rebound to",
				screen.getClass().getSimpleName(), expected);
		sessionScreen = screen;
		DIAGNOSED.clear();
		resetSessionState();
		return true;
	}

	/** 按当前悬停位置刷新"记录槽位"；没按住修饰键就清空。 */
	private static void updateRecordedSlot(HandledScreen<?> screen, double mouseX, double mouseY) {
		if (!BetterRemovalClient.isExtractKeyPhysicallyDown()) {
			// 松开修饰键：不再记录、也不再涂色。
			// 刻意<b>不</b>动 scrollAccumulator——滚轮换画笔不要求按住修饰键，
			// 在这里清零会让"松手后滚轮永远凑不满一档"，画笔就再也换不了。
			recordedSlot = -1;
			return;
		}
		Slot slot = hoveredUsableSlot(screen, mouseX, mouseY);
		if (slot != null) {
			recordedSlot = slot.getIndex();
		}
	}

	/**
	 * 鼠标下可用于指认的槽位；不适用时返回 null。
	 * 玩家自己的背包槽位与隐藏槽位都不参与指认。
	 * <p>
	 * {@code betterremoval$slotAt} 走的就是原版 {@code HandledScreen#getSlotAt}，只返回
	 * {@code isEnabled()} 的槽位；1.20.1 没有 1.21 的 {@code Slot#isFake()}（合成预览/模板槽），
	 * 原版 1.20.1 的容器菜单里也不存在这类槽位，所以不需要额外判断。
	 */
	private static Slot hoveredUsableSlot(HandledScreen<?> screen, double mouseX, double mouseY) {
		Slot slot;
		try {
			slot = ((MappingScreenAccess) screen).betterremoval$slotAt(mouseX, mouseY);
		}
		catch (Throwable t) {
			return null;
		}
		if (slot == null) {
			return null;
		}
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return null;
		}
		if (!slot.isEnabled() || slot.inventory == player.getInventory()) {
			return null;
		}
		return slot;
	}

	// ---------- 绘制：区域分组 + 底部状态条 ----------

	/** 一组四邻相接的同角色槽位。刻意不存包围盒：区域可以是非矩形（L 形、十字形都支持）。 */
	private record Region(SlotRole role, List<Slot> members) {
	}

	/**
	 * 在容器界面上画分组区域、聚焦角标与底部状态条。
	 * <p>
	 * 由 {@code HandledScreenMappingMixin} 在 {@code HandledScreen#drawForeground} 之后调用，
	 * 因此 {@code context} 的矩阵已经平移到界面原点。
	 */
	public static void renderForeground(HandledScreen<?> screen, DrawContext context, int mouseX, int mouseY) {
		if (!active()) {
			// 会话结束就清掉记录，免得下一次会话的高亮落到别的容器的槽位上
			endSessionState();
			return;
		}
		if (!bindScreen(screen)) {
			// 会话还开着，但当前界面不是它所属的那一个（例如玩家背包）：
			// 菜单槽位号对不上，一律不画叠层、也不碰光标
			return;
		}
		TextRenderer font = MinecraftClient.getInstance().textRenderer;

		// 前景钩子每帧都会来一次：刷新"记录槽位"，并处理"停住一会儿就涂上画笔"
		updateRecordedSlot(screen, mouseX, mouseY);
		applyBrushOnDwell();

		// 坐标系注意：1.20.1 的 HandledScreen#render 是
		//   push() → translate(this.x, this.y) → 画槽位 → drawForeground(...) → pop()
		// （反编译源 HandledScreen.java:98-99-118-150），所以这里画的必须是「GUI 内部坐标」，
		// 和 Slot.x / Slot.y 同一套。再加一次界面原点会让整层标签被平移两次，
		// 偏移量正好是界面的位置——表现出来就是标签跑到界面右侧 JEI 物品栏那一带。
		ScreenHandler menu = screen.getScreenHandler();

		// 1) 相邻同角色槽位合并成区域：逐格铺底色、只描"没有同角色邻居"的边，非矩形形状也能贴合
		for (Region region : buildRegions(menu, MappingSessionState.getRoles())) {
			drawRegion(context, font, region);
		}

		// 2) 被"忽略"擦掉的槽位给个灰色小叉，不然玩家会以为没生效
		drawErasedMarks(context, menu);

		// 3) 记录中的槽位：四角呼吸角标，标出"滚轮/滑过改的就是它"
		if (recordedSlot >= 0 && recordedSlot < menu.slots.size()) {
			Slot slot = menu.getSlot(recordedSlot);
			if (slot.isEnabled()) {
				drawFocus(context, slot);
			}
		}

		// 4) GUI 上沿外侧的画笔状态条
		drawBrushBar(context, font, screen);

		// 5) 光标跟着画笔换：I/O/F 各一张贴图，缺图就回退系统默认光标
		MappingCursors.apply(brush);
	}

	/** 界面关闭：会话结束，把系统光标还回去（关掉界面后不会再触发容器前景钩子）。 */
	public static void onScreenClosing() {
		MappingCursors.restore();
	}

	/**
	 * 把相邻的同角色槽位合并成区域。
	 * 四邻相接才算相邻（原版槽位间距 18px = 16 图标 + 2 空隙，留一点容差兼容模组自己排的间距）。
	 */
	private static List<Region> buildRegions(ScreenHandler menu, Map<Integer, SlotRole> roles) {
		Map<Integer, Slot> tagged = new LinkedHashMap<>();
		roles.forEach((index, role) -> {
			if (index == null || role == null || role == SlotRole.NONE || index < 0 || index >= menu.slots.size()) {
				return;
			}
			Slot slot = menu.getSlot(index);
			if (slot.isEnabled()) {
				tagged.put(index, slot);
			}
		});

		List<Region> regions = new ArrayList<>();
		Set<Integer> visited = new HashSet<>();
		for (Integer start : tagged.keySet()) {
			if (!visited.add(start)) {
				continue;
			}
			SlotRole role = roles.get(start);
			List<Slot> members = new ArrayList<>();
			Deque<Integer> queue = new ArrayDeque<>();
			queue.add(start);
			while (!queue.isEmpty()) {
				Slot slot = tagged.get(queue.poll());
				members.add(slot);
				for (Map.Entry<Integer, Slot> candidate : tagged.entrySet()) {
					if (visited.contains(candidate.getKey()) || roles.get(candidate.getKey()) != role) {
						continue;
					}
					if (!adjacent(slot, candidate.getValue())) {
						continue;
					}
					visited.add(candidate.getKey());
					queue.add(candidate.getKey());
				}
			}
			regions.add(new Region(role, members));
		}
		return regions;
	}

	/** 两个槽位是否紧挨着。 */
	private static boolean adjacent(Slot a, Slot b) {
		int dx = Math.abs(a.x - b.x);
		int dy = Math.abs(a.y - b.y);
		return (dy <= 2 && dx >= 16 && dx <= 22) || (dx <= 2 && dy >= 16 && dy <= 22);
	}

	/**
	 * 画一块区域：每格铺低透明度角色色，再只描"外侧边"（没有同角色邻居的那几条边），
	 * 于是相邻格子自然连成一块，L 形、十字形这种非矩形形状也能贴合真实轮廓。
	 * 区域右上角挂一个角色图标（见 {@link #drawRegionBadge}）。
	 */
	private static void drawRegion(DrawContext context, TextRenderer font, Region region) {
		List<Slot> members = region.members();
		int rgb = color(region.role()) & 0xFFFFFF;
		int background = 0x30000000 | rgb;
		int outline = 0xB0000000 | rgb;
		int accent = 0xE0000000 | rgb;
		int arm = 5;
		int thick = 2;

		for (Slot slot : members) {
			context.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, background);
		}

		for (Slot slot : members) {
			boolean up = hasNeighbour(members, slot, 0, -1);
			boolean down = hasNeighbour(members, slot, 0, 1);
			boolean left = hasNeighbour(members, slot, -1, 0);
			boolean right = hasNeighbour(members, slot, 1, 0);
			int x1 = slot.x - 1;
			int y1 = slot.y - 1;
			int x2 = slot.x + 17;
			int y2 = slot.y + 17;
			if (!up) {
				context.fill(x1, y1, x2, y1 + 1, outline);
			}
			if (!down) {
				context.fill(x1, y2 - 1, x2, y2, outline);
			}
			if (!left) {
				context.fill(x1, y1, x1 + 1, y2, outline);
			}
			if (!right) {
				context.fill(x2 - 1, y1, x2, y2, outline);
			}
			// 凸角加粗：两个相邻方向都没有同角色邻居时，这个角就是外轮廓的拐角
			if (!up && !left) {
				context.fill(x1, y1, x1 + arm, y1 + thick, accent);
				context.fill(x1, y1, x1 + thick, y1 + arm, accent);
			}
			if (!up && !right) {
				context.fill(x2 - arm, y1, x2, y1 + thick, accent);
				context.fill(x2 - thick, y1, x2, y1 + arm, accent);
			}
			if (!down && !left) {
				context.fill(x1, y2 - thick, x1 + arm, y2, accent);
				context.fill(x1, y2 - arm, x1 + thick, y2, accent);
			}
			if (!down && !right) {
				context.fill(x2 - arm, y2 - thick, x2, y2, accent);
				context.fill(x2 - thick, y2 - arm, x2, y2, accent);
			}
		}

		drawRegionBadge(context, font, region);
	}

	/** 该格在指定方向（dx/dy 取 -1/0/1）上有没有同角色邻居。 */
	private static boolean hasNeighbour(List<Slot> members, Slot slot, int dx, int dy) {
		for (Slot other : members) {
			if (other == slot) {
				continue;
			}
			int ox = other.x - slot.x;
			int oy = other.y - slot.y;
			if (dx != 0) {
				if (Integer.signum(ox) != dx || Math.abs(oy) > 2) {
					continue;
				}
				int gap = Math.abs(ox);
				if (gap >= 16 && gap <= 22) {
					return true;
				}
			}
			else {
				if (Integer.signum(oy) != dy || Math.abs(ox) > 2) {
					continue;
				}
				int gap = Math.abs(oy);
				if (gap >= 16 && gap <= 22) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 区域右上角内侧一个小小的 I/O/F：取"最上面那一行里最靠右"的那一格，
	 * 在它的右上角贴一个半透明深色小底片 + 角色色字母——
	 * 压着物品也看得清，又不像图标那样占掉半格。
	 */
	private static void drawRegionBadge(DrawContext context, TextRenderer font, Region region) {
		Slot target = null;
		for (Slot slot : region.members()) {
			if (target == null || slot.y < target.y || (slot.y == target.y && slot.x > target.x)) {
				target = slot;
			}
		}
		if (target == null) {
			return;
		}
		String tag = tag(region.role());
		int width = font.getWidth(tag) + 3;
		int height = 9;
		int x = target.x + 16 - width;
		int y = target.y;
		context.fill(x, y, x + width, y + height, COLOR_BADGE_BG);
		context.drawText(font, tag, x + 2, y, color(region.role()), false);
	}

	/** 图标缺失时的回退字母。 */
	private static String tag(SlotRole role) {
		return switch (role) {
			case INPUT -> "I";
			case FUEL -> "F";
			case OUTPUT -> "O";
			case NONE -> "-";
		};
	}

	/** 被"忽略"画笔擦掉的槽位：画一个灰色小叉。 */
	private static void drawErasedMarks(DrawContext context, ScreenHandler menu) {
		for (int index : erasedSlots) {
			if (index < 0 || index >= menu.slots.size()) {
				continue;
			}
			Slot slot = menu.getSlot(index);
			if (!slot.isEnabled()) {
				continue;
			}
			int ox = slot.x + 5;
			int oy = slot.y + 5;
			for (int i = 0; i < 6; i++) {
				context.fill(ox + i, oy + i, ox + i + 1, oy + i + 1, COLOR_ERASED);
				context.fill(ox + 5 - i, oy + i, ox + 6 - i, oy + i + 1, COLOR_ERASED);
			}
		}
	}

	/** 记录中的槽位：四角白色角标 + 轻微呼吸，替掉原来那圈死白框。 */
	private static void drawFocus(DrawContext context, Slot slot) {
		double phase = (Util.getMeasuringTimeMs() % 1000L) / 1000.0 * Math.PI * 2.0;
		int alpha = 0x60 + (int) ((Math.sin(phase) * 0.5 + 0.5) * 0x60);
		int color = (alpha << 24) | 0xFFFFFF;
		int x = slot.x;
		int y = slot.y;
		int arm = 5;
		int thick = 2;
		context.fill(x - 2, y - 2, x - 2 + arm, y - 2 + thick, color);
		context.fill(x - 2, y - 2, x - 2 + thick, y - 2 + arm, color);
		context.fill(x + 18 - arm, y - 2, x + 18, y - 2 + thick, color);
		context.fill(x + 18 - thick, y - 2, x + 18, y - 2 + arm, color);
		context.fill(x - 2, y + 18 - thick, x - 2 + arm, y + 18, color);
		context.fill(x - 2, y + 18 - arm, x - 2 + thick, y + 18, color);
		context.fill(x + 18 - arm, y + 18 - thick, x + 18, y + 18, color);
		context.fill(x + 18 - thick, y + 18 - arm, x + 18, y + 18, color);
	}

	/**
	 * GUI 上沿外侧那条画笔状态条：宽度和界面同宽，紧贴界面顶边。
	 * <p>
	 * 背景走 {@link MappingIcons#MAPPING_BAR} 的九宫格（左右 {@link #BAR_CAP} 端帽、
	 * 上下 {@link #BAR_EDGE} 边线保持原样，中间填充横向拉伸）——图是那张 30×148 竖面板
	 * 逆时针旋转 90° 烘焙出来的。内容只有"画笔色块 + 当前画笔名"；
	 * 出错时那一行临时换成深红错误文案（放不下就截断）。
	 */
	private static void drawBrushBar(DrawContext context, TextRenderer font, HandledScreen<?> screen) {
		MappingScreenAccess access = (MappingScreenAccess) screen;
		int width = access.betterremoval$backgroundWidth();
		// 紧贴界面上沿的外侧；界面贴着窗口顶端、上面放不下时，改画到界面下沿
		int y = access.betterremoval$guiTop() >= BAR_HEIGHT ? -BAR_HEIGHT : access.betterremoval$backgroundHeight();

		drawBarBackground(context, 0, y, width, BAR_HEIGHT);

		int swatch = 6;
		int swatchX = 4;
		int swatchY = y + (BAR_HEIGHT - swatch) / 2;
		context.fill(swatchX - 1, swatchY - 1, swatchX + swatch + 1, swatchY + swatch + 1, 0xFF000000);
		context.fill(swatchX, swatchY, swatchX + swatch, swatchY + swatch,
				brush == null ? 0xFF808080 : color(brush));

		boolean error = !MappingSessionState.isOk() && !MappingSessionState.getMessage().isEmpty();
		Text text = error
				? Text.translatable(MappingSessionState.getMessage())
				: Text.translatable("better-removal.mapping.bar_brush",
						brush == null
								? Text.translatable("better-removal.mapping.brush_none")
								: Text.translatable(brush.getTranslationKey()));
		int textX = swatchX + swatch + 4;
		int textY = y + (BAR_HEIGHT - 8) / 2;
		int available = width - textX - 4;
		if (available > 0 && font.getWidth(text) > available) {
			// 超宽就截断，别把字画到条外面去
			text = Text.literal(font.trimToWidth(text.getString(), available));
		}
		context.drawText(font, text, textX, textY, error ? COLOR_BAR_ERROR : COLOR_BAR_TEXT, false);
	}

	/**
	 * 把状态条背景九宫格铺开：左端帽 / 中段横向拉伸 / 右端帽，上下各留一条边线。
	 * 源图读不到时退化成"黑边 + 原面板灰"的纯色条，保证功能还在。
	 */
	private static void drawBarBackground(DrawContext context, int x, int y, int width, int height) {
		int[] source = MappingIcons.size(MappingIcons.MAPPING_BAR);
		if (source == null || source[0] < BAR_CAP * 2 + 1 || source[1] < BAR_EDGE * 2 + 1) {
			context.fill(x, y, x + width, y + height, COLOR_BAR_FALLBACK);
			context.fill(x, y, x + width, y + BAR_EDGE, COLOR_BAR_FALLBACK_EDGE);
			context.fill(x, y + height - BAR_EDGE, x + width, y + height, COLOR_BAR_FALLBACK_EDGE);
			return;
		}
		int srcW = source[0];
		int srcH = source[1];
		int middleWidth = width - BAR_CAP * 2;
		int middleHeight = height - BAR_EDGE * 2;
		Identifier texture = MappingIcons.MAPPING_BAR;
		int[] vOffsets = { 0, BAR_EDGE, srcH - BAR_EDGE };
		int[] vHeights = { BAR_EDGE, srcH - BAR_EDGE * 2, BAR_EDGE };
		int[] destY = { y, y + BAR_EDGE, y + height - BAR_EDGE };
		int[] destHeights = { BAR_EDGE, middleHeight, BAR_EDGE };
		for (int row = 0; row < 3; row++) {
			int v = vOffsets[row];
			int vh = vHeights[row];
			int dy = destY[row];
			int dh = destHeights[row];
			if (vh <= 0 || dh <= 0) {
				continue;
			}
			context.drawTexture(texture, x, dy, BAR_CAP, dh, 0.0F, (float) v, BAR_CAP, vh, srcW, srcH);
			if (middleWidth > 0) {
				context.drawTexture(texture, x + BAR_CAP, dy, middleWidth, dh, (float) BAR_CAP, (float) v, 1, vh, srcW, srcH);
			}
			context.drawTexture(texture, x + width - BAR_CAP, dy, BAR_CAP, dh,
					(float) (srcW - BAR_CAP), (float) v, BAR_CAP, vh, srcW, srcH);
		}
	}

	private static int color(SlotRole role) {
		return switch (role) {
			case INPUT -> COLOR_INPUT;
			case FUEL -> COLOR_FUEL;
			case OUTPUT, NONE -> COLOR_OUTPUT;
		};
	}
}
