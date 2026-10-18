package closedw.br.ftbultimine;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

/**
 * FTB Ultimine 联动支持 —— <b>只走官方公开 API</b>（{@code dev.ftb.mods.ftbultimine.api}）。
 * <p>
 * FTB Ultimine 的接口在大版本之间可能大改，小版本之间几乎不动；而 {@code api} 包是它<b>唯一承诺稳定</b>
 * 的入口。所以这里只反射这一条路：
 * <pre>
 *   FTBUltimineAPI.api().currentBlockSelection(player) → Optional&lt;Collection&lt;BlockPos&gt;&gt;
 * </pre>
 * 早先还反射过 {@code FTBUltimine.getInstance()} 与 {@code FTBUltimine.instance} 字段，以及
 * {@code FTBUltiminePlayerData} 的 {@code isPressed()/cachedPositions()}——那些都在 {@code api} 包之外，
 * 上游一改就失效，已按作者要求删除。
 * <p>
 * <b>没有"按键是否按住"的独立入口了</b>：{@code currentBlockSelection} 在玩家没有连锁选区时
 * 返回 {@code Optional.empty()}，语义上等价于"没按住 Ultimine 键或形状无效"，所以调用方不再需要预检。
 * <p>
 * <b>Forge 1.20.1 例外</b>：那一端的 FTB Ultimine 构建里<b>没有</b> {@code api} 包
 * （实测 {@code ftb-ultimine-forge-2001.1.8.jar} 的 {@code api} 下只有 {@code rightclick/*}），
 * 所以它的实现只能反射内部类——见 Forge 工程里同名文件的类注释。
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
				apiCurrentSelection = apiInterface.getMethod("currentBlockSelection", PlayerEntity.class);
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
			return FabricLoader.getInstance().isModLoaded(MOD_ID);
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
	public static Collection<BlockPos> getShapePositions(PlayerEntity player) {
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
