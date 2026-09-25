package closedw.br.container.support;

import closedw.br.BetterRemoval;
import closedw.br.config.BetterRemovalConfig;
import closedw.br.container.builtin.CookingPotSupport;
import closedw.br.container.builtin.OvenSupport;

import net.minecraft.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 容器注册表：所有容器类型在这里注册一次，调用方按「第一个匹配」查询。
 * 取物/放入/补货/连锁/预览都只面向这里。
 */
public final class ContainerRegistry {

	private static final List<ContainerSupport> SUPPORTS = new ArrayList<>();

	static {
		registerAll();
	}

	private ContainerRegistry() {
	}

	/** 触发静态初始化（注册容器）。 */
	public static void init() {
	}

	private static void reg(ContainerSupport support) {
		SUPPORTS.add(support);
	}

	private static void registerAll() {
		// ---------- 必须硬编码的容器 ----------
		// 这两个的方块实体没有实现原版 Inventory（厨锅用自定义 ItemStackHandler，烤炉要反射取内部容器），
		// 槽位号对规则而言不可寻址，所以只能留在代码里。
		reg(new CookingPotSupport());
		reg(new OvenSupport());

		// ---------- 声明式规则（内置 json / 用户 json / Configured）----------
		// 原版容器与各模组容器的槽位布局都在 src/main/resources/better-removal/containers/*.json
		reg(new RuleContainerSupport());

		// ---------- 实验性：白名单自动探测（总开关默认关闭）----------
		reg(new AutoDetectContainerSupport());
	}

	/**
	 * 启动自检：防止新增容器时「漏改一处静默失效」。检查配置键是否重复、是否真的登记进了配置项。
	 * <p>
	 * <b>必须在配置文件初始化完成之后调用</b>（见 {@code BetterRemoval}）。放到类初始化里做是错的：
	 * 那时 Configured 的配置项还没建好，每个键都会被判成"没有对应配置项"而误报一片。
	 */
	public static void validate() {
		Set<String> seen = new HashSet<>();
		for (ContainerSupport support : SUPPORTS) {
			String key = support.configKey();
			if (key == null) {
				continue;
			}
			if (!seen.add(key)) {
				BetterRemoval.LOGGER.error("[container] duplicate config key {} (the later registration wins)", key);
			}
			if (!configHasKey(key)) {
				BetterRemoval.LOGGER.warn("[container] config key {} has no matching config entry, so it will not show up in Configured", key);
			}
		}
	}

	private static boolean configHasKey(String key) {
		try {
			return BetterRemovalConfig.DEFAULT_VALUES.containsKey(key);
		}
		catch (Throwable t) {
			// 未安装 Configured，或配置还没初始化：无法检查，别误报
			return true;
		}
	}

	/**
	 * 找出第一个匹配且可用的容器类型；没有则返回 null。
	 * 匹配上但被显式关闭的容器（{@link ContainerSupport#explicitlyDisabled}）会让查询就此终止并返回 null，
	 * 而不是继续往下走到兜底实现——否则玩家关掉一条规则后，同一个方块会被自动探测「猜着」复活。
	 * 由容器自己的配置键关掉的实现（{@link ContainerSupport#enabled()}）则只是被跳过，查询继续。
	 */
	public static ContainerSupport find(BlockEntity blockEntity) {
		if (blockEntity == null) {
			return null;
		}
		for (ContainerSupport support : SUPPORTS) {
			boolean matched;
			try {
				matched = support.matches(blockEntity);
			}
			catch (Throwable t) {
				// 单个容器的匹配逻辑异常不应影响其它容器
				continue;
			}
			if (!matched) {
				continue;
			}
			try {
				if (support.explicitlyDisabled(blockEntity)) {
					return null;
				}
			}
			catch (Throwable t) {
				// 判定异常时按「没被关闭」处理，宁可继续用也不静默失效
			}
			if (support.enabled()) {
				return support;
			}
		}
		return null;
	}
}
