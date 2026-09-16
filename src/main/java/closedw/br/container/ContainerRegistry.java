package closedw.br.container;

import closedw.br.BetterRemoval;
import closedw.br.ExtractionMode;
import closedw.br.config.BetterRemovalConfig;
import closedw.br.adastra.CompressorSupport;
import closedw.br.adastra.CryoFreezerSupport;
import closedw.br.adastra.EtrionicBlastFurnaceSupport;
import closedw.br.adastra.FuelRefinerySupport;
import closedw.br.adastra.OxygenLoaderSupport;
import closedw.br.aether.AltarSupport;
import closedw.br.aether.FreezerSupport;
import closedw.br.crabbersdelight.CrabTrapSupport;
import closedw.br.farmandcharm.FarmAndCharmSupport;
import closedw.br.farmersdelight.FarmersDelightSupport;
import closedw.br.fossil.AnalyzerSupport;
import closedw.br.fossil.CultureVatSupport;
import closedw.br.fossil.SifterSupport;
import closedw.br.fossil.WorktableSupport;
import closedw.br.vinery.ApplePressSupport;
import closedw.br.vinery.FermentationBarrelSupport;
import net.minecraft.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.SmokerBlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 容器注册表：所有容器类型（硬编码兼容 + 实验性规则/探测）在这里注册一次，
 * 调用方按“第一个匹配”查询。取物/放入/补货/连锁/预览都只面向这里，不再有平行分派链。
 */
public final class ContainerRegistry {

	private static final List<ContainerSupport> SUPPORTS = new ArrayList<>();

	static {
		registerAll();
		validate();
	}

	private ContainerRegistry() {
	}

	/** 触发静态初始化（注册 + 自检）。 */
	public static void init() {
	}

	private static void reg(ContainerSupport support) {
		SUPPORTS.add(support);
	}

	private static void registerAll() {
		// ---------- 原版 ----------
		reg(new SimpleContainerSupport("furnace", be -> be instanceof FurnaceBlockEntity)
				.extract(ExtractionMode.INPUT, 0).extract(ExtractionMode.FUEL, 1).extract(ExtractionMode.OUTPUT, 2)
				.deposit(ExtractionMode.INPUT, 0).deposit(ExtractionMode.FUEL, 1));
		reg(new SimpleContainerSupport("blast_furnace", be -> be instanceof BlastFurnaceBlockEntity)
				.extract(ExtractionMode.INPUT, 0).extract(ExtractionMode.FUEL, 1).extract(ExtractionMode.OUTPUT, 2)
				.deposit(ExtractionMode.INPUT, 0).deposit(ExtractionMode.FUEL, 1));
		reg(new SimpleContainerSupport("smoker", be -> be instanceof SmokerBlockEntity)
				.extract(ExtractionMode.INPUT, 0).extract(ExtractionMode.FUEL, 1).extract(ExtractionMode.OUTPUT, 2)
				.deposit(ExtractionMode.INPUT, 0).deposit(ExtractionMode.FUEL, 1));
		// 0-2 药水槽，3 材料槽（下界疣等），4 燃料槽（烈焰粉）
		reg(new SimpleContainerSupport("brewing_stand", be -> be instanceof BrewingStandBlockEntity)
				.extract(ExtractionMode.INPUT, 3).extract(ExtractionMode.FUEL, 4).extract(ExtractionMode.OUTPUT, 0, 1, 2)
				.deposit(ExtractionMode.INPUT, 3).deposit(ExtractionMode.FUEL, 4));
		// 没有单独输入/燃料槽的容器，所有模式都取放全部槽位
		reg(new SimpleContainerSupport("hopper", be -> be instanceof HopperBlockEntity).allModes());
		reg(new SimpleContainerSupport("dropper", be -> be instanceof DropperBlockEntity).allModes());
		// 注意：DropperBlockEntity 继承自 DispenserBlockEntity，发射器必须排除投掷器
		reg(new SimpleContainerSupport("dispenser",
				be -> be instanceof DispenserBlockEntity && !(be instanceof DropperBlockEntity)).allModes());

		// ---------- Farmer's Delight ----------
		reg(new CookingPotSupport());
		reg(new SimpleContainerSupport("basket", be -> FarmersDelightSupport.isBasket(be)).allModes());

		// ---------- Ad Astra ----------
		// 这些机器都没有燃料槽：FUEL 预设无槽位可取
		// 压缩机：0电 1输入 2输出
		reg(new SimpleContainerSupport("compressor", be -> CompressorSupport.isCompressor(be))
				.extract(ExtractionMode.INPUT, 1).extract(ExtractionMode.OUTPUT, 2)
				.deposit(ExtractionMode.INPUT, 1));
		// 电力高炉：0电 1-4输入 5-8输出
		reg(new SimpleContainerSupport("etrionic_blast_furnace", be -> EtrionicBlastFurnaceSupport.isEtrionicBlastFurnace(be))
				.extract(ExtractionMode.INPUT, 1, 2, 3, 4).extract(ExtractionMode.OUTPUT, 5, 6, 7, 8)
				.deposit(ExtractionMode.INPUT, 1, 2, 3, 4));
		// 燃料精炼机：0电 1输入(原油) 2输出(空桶) 3流体输入 4输出(满桶)
		reg(new SimpleContainerSupport("fuel_refinery", be -> FuelRefinerySupport.isFuelRefinery(be))
				.extract(ExtractionMode.INPUT, 1, 3).extract(ExtractionMode.OUTPUT, 2, 4)
				.deposit(ExtractionMode.INPUT, 1, 3));
		// 氧气装载机：0电池 1输入(水桶) 2输出(空桶) 3流体输入 4输出
		reg(new SimpleContainerSupport("oxygen_loader", be -> OxygenLoaderSupport.isOxygenLoader(be))
				.extract(ExtractionMode.INPUT, 1, 3).extract(ExtractionMode.OUTPUT, 2, 4)
				.deposit(ExtractionMode.INPUT, 1, 3));
		// 低温冷冻机：0电 1输入 2流体输入 3输出
		reg(new SimpleContainerSupport("cryo_freezer", be -> CryoFreezerSupport.isCryoFreezer(be))
				.extract(ExtractionMode.INPUT, 1, 2).extract(ExtractionMode.OUTPUT, 3)
				.deposit(ExtractionMode.INPUT, 1, 2));

		// ---------- Crabber's Delight ----------
		// 捕蟹笼：0诱饵 1-9捕获物
		reg(new SimpleContainerSupport("crab_trap", be -> CrabTrapSupport.isCrabTrap(be))
				.extract(ExtractionMode.INPUT, 0).extract(ExtractionMode.OUTPUT, 1, 2, 3, 4, 5, 6, 7, 8, 9)
				.deposit(ExtractionMode.INPUT, 0));

		// ---------- The Aether ----------
		// 冷冻器/神能炉：0输入 1燃料 2输出
		reg(new SimpleContainerSupport("freezer", be -> FreezerSupport.isFreezer(be))
				.extract(ExtractionMode.INPUT, 0).extract(ExtractionMode.FUEL, 1).extract(ExtractionMode.OUTPUT, 2)
				.deposit(ExtractionMode.INPUT, 0).deposit(ExtractionMode.FUEL, 1));
		reg(new SimpleContainerSupport("altar", be -> AltarSupport.isAltar(be))
				.extract(ExtractionMode.INPUT, 0).extract(ExtractionMode.FUEL, 1).extract(ExtractionMode.OUTPUT, 2)
				.deposit(ExtractionMode.INPUT, 0).deposit(ExtractionMode.FUEL, 1));

		// ---------- Vinery ----------
		// 陈酿桶：0葡萄汁 1-3食材 4酒瓶 5输出
		reg(new SimpleContainerSupport("fermentation_barrel", be -> FermentationBarrelSupport.isFermentationBarrel(be))
				.extract(ExtractionMode.INPUT, 0, 1, 2, 3).extract(ExtractionMode.FUEL, 4).extract(ExtractionMode.OUTPUT, 5)
				.deposit(ExtractionMode.INPUT, 0, 1, 2, 3).deposit(ExtractionMode.FUEL, 4));
		// 苹果压榨器：0压榨输入 1中间产物 2酒瓶输入 3输出
		// 取出燃料预设为输出槽3，放入燃料预设为酒瓶槽2（保持原有不对称语义）
		reg(new SimpleContainerSupport("apple_press", be -> ApplePressSupport.isApplePress(be))
				.extract(ExtractionMode.INPUT, 0, 1, 2).extract(ExtractionMode.FUEL, 3).extract(ExtractionMode.OUTPUT, 3)
				.deposit(ExtractionMode.INPUT, 0, 1, 2).deposit(ExtractionMode.FUEL, 2));

		// ---------- Fossils and Archeology: Revival ----------
		// 分析仪：0-8输入 9-12输出
		reg(new SimpleContainerSupport("analyzer", be -> AnalyzerSupport.isAnalyzer(be))
				.extract(ExtractionMode.INPUT, 0, 1, 2, 3, 4, 5, 6, 7, 8)
				.extract(ExtractionMode.OUTPUT, 9, 10, 11, 12)
				.deposit(ExtractionMode.INPUT, 0, 1, 2, 3, 4, 5, 6, 7, 8));
		// 筛子：0输入 1-5输出
		reg(new SimpleContainerSupport("sifter", be -> SifterSupport.isSifter(be))
				.extract(ExtractionMode.INPUT, 0).extract(ExtractionMode.OUTPUT, 1, 2, 3, 4, 5)
				.deposit(ExtractionMode.INPUT, 0));
		// 培养槽：0输入 1燃料 2输出
		reg(new SimpleContainerSupport("culture_vat", be -> CultureVatSupport.isCultureVat(be))
				.extract(ExtractionMode.INPUT, 0).extract(ExtractionMode.FUEL, 1).extract(ExtractionMode.OUTPUT, 2)
				.deposit(ExtractionMode.INPUT, 0).deposit(ExtractionMode.FUEL, 1));
		// 考古工作台：0输入 1燃料 2输出
		reg(new SimpleContainerSupport("worktable", be -> WorktableSupport.isWorktable(be))
				.extract(ExtractionMode.INPUT, 0).extract(ExtractionMode.FUEL, 1).extract(ExtractionMode.OUTPUT, 2)
				.deposit(ExtractionMode.INPUT, 0).deposit(ExtractionMode.FUEL, 1));

		// ---------- Cooking for Blockheads ----------
		reg(new OvenSupport());

		// ---------- Farm & Charm ----------
		// 厨房锅/烤盘：0-5食材 6盘(消耗品) 7输出；炉灶：1-3食材 4燃料 0输出
		reg(new SimpleContainerSupport("fc_cooking_pot", be -> FarmAndCharmSupport.isCookingPot(be))
				.extract(ExtractionMode.INPUT, 0, 1, 2, 3, 4, 5).extract(ExtractionMode.FUEL, 6).extract(ExtractionMode.OUTPUT, 7)
				.deposit(ExtractionMode.INPUT, 0, 1, 2, 3, 4, 5).deposit(ExtractionMode.FUEL, 6));
		reg(new SimpleContainerSupport("roaster", be -> FarmAndCharmSupport.isRoaster(be))
				.extract(ExtractionMode.INPUT, 0, 1, 2, 3, 4, 5).extract(ExtractionMode.FUEL, 6).extract(ExtractionMode.OUTPUT, 7)
				.deposit(ExtractionMode.INPUT, 0, 1, 2, 3, 4, 5).deposit(ExtractionMode.FUEL, 6));
		reg(new SimpleContainerSupport("stove", be -> FarmAndCharmSupport.isStove(be))
				.extract(ExtractionMode.INPUT, 1, 2, 3).extract(ExtractionMode.FUEL, 4).extract(ExtractionMode.OUTPUT, 0)
				.deposit(ExtractionMode.INPUT, 1, 2, 3).deposit(ExtractionMode.FUEL, 4));

		// ---------- 实验性：通用容器支持（总开关默认关闭）----------
		// 顺序即优先级：手写规则优先于自动探测；两者都只对硬编码未覆盖的容器生效
		reg(new RuleContainerSupport());
		reg(new AutoDetectContainerSupport());
	}

	/**
	 * 启动自检：防止新增容器时“漏改一处静默失效”。
	 * 检查配置键是否重复、是否在 Configured 配置项里存在。
	 * 未安装 Configured 时跳过配置项检查。
	 */
	private static void validate() {
		Set<String> seen = new HashSet<>();
		for (ContainerSupport support : SUPPORTS) {
			String key = support.configKey();
			if (key == null) {
				continue;
			}
			if (!seen.add(key)) {
				BetterRemoval.LOGGER.error("[container] 重复的配置键 {}（后注册的会覆盖先注册的）", key);
			}
			if (!configHasKey(key)) {
				BetterRemoval.LOGGER.warn("[container] 配置键 {} 没有对应的配置项，Configured 里不会显示", key);
			}
		}
	}

	private static boolean configHasKey(String key) {
		try {
			return BetterRemovalConfig.DEFAULT_VALUES.containsKey(key);
		}
		catch (LinkageError e) {
			// 未安装 Configured，无法检查
			return true;
		}
	}

	/** 找出第一个匹配该方块的容器类型；没有则返回 null。 */
	public static ContainerSupport find(BlockEntity blockEntity) {
		if (blockEntity == null) {
			return null;
		}
		for (ContainerSupport support : SUPPORTS) {
			try {
				if (support.matches(blockEntity)) {
					return support;
				}
			}
			catch (Throwable t) {
				// 单个容器的匹配逻辑异常不应影响其它容器
			}
		}
		return null;
	}
}
