package closedw.br;

import closedw.br.behavior.Behavior;
import closedw.br.behavior.InteractionMode;
import closedw.br.behavior.InteractionModeManager;
import closedw.br.config.BetterRemovalConfig;
import closedw.br.container.access.MenuSlotAccess;
import closedw.br.container.detect.SlotDetection;
import closedw.br.container.detect.SlotProbeReport;
import closedw.br.container.Stacks;
import closedw.br.container.support.ContainerAccess;
import closedw.br.container.support.ContainerRegistry;
import closedw.br.container.support.ContainerSupport;
import closedw.br.ftbultimine.FTBUltimineSupport;
import closedw.br.mapping.MappingSessionManager;
import closedw.br.networking.ExtractKeyStateManager;
import closedw.br.slot.SlotSet;


import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 左Alt（可改键）+ 右键容器直接交互，不用打开容器的 GUI：
 * 空手=按当前槽位模式取出物品；手持物品=按当前槽位模式放入物品（放入预设）；
 * 补货预设=空手右击，从背包向容器输入槽和燃料槽补充已有同类物品（空槽不补）。
 * 模式通过 /br 指令或模式滚轮（按住模式键+滚轮）切换。
 * 容器类型统一由 {@link ContainerRegistry} 描述，容器可通过Configured的配置菜单开关。
 */
public final class OutputSlotExtractor {

	private static final org.slf4j.Logger LOGGER = BetterRemoval.LOGGER;

	private OutputSlotExtractor() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(OutputSlotExtractor::onUseBlock);
	}

	/**
	 * 查询某个容器是否启用。
	 * <p>
	 * Configured 是<b>必需</b>依赖（`fabric.mod.json` 的 `depends`，与 NeoForge 端的
	 * `type="required"` 对齐），所以正常情况下这里不会走到 catch。
	 * 保留 {@link LinkageError} 兜底只是为了"配置类还没被类加载器解析完"这类极端时序：
	 * 容器开关读不到时按<b>启用</b>处理（宁可照旧也不要静默失效）。
	 */
	public static boolean isContainerEnabled(String key) {
		try {
			return BetterRemovalConfig.get().isEnabled(key);
		}
		catch (LinkageError e) {
			return true;
		}
	}

	/**
	 * 查询整数配置。读不到时返回 fallback。
	 */
	public static int getConfigInt(String key, int fallback) {
		try {
			return BetterRemovalConfig.get().getInt(key);
		}
		catch (LinkageError e) {
			return fallback;
		}
	}

	/**
	 * 查询一个"默认关闭"的实验性开关（{@code menu_mapping} / {@code experimental_transfer_probe}）。
	 * <p>
	 * 与 {@link #isContainerEnabled} 刻意相反：读不到配置时返回 <b>false</b>。
	 * 容器开关缺失配置时按"开启"处理（宁可照旧也不要静默失效），但实验性功能必须相反——
	 * 缺配置就当没开，否则它会绕过"默认关闭"的约定自己冒出来。
	 */
	public static boolean isExperimentalSwitchOn(String key) {
		try {
			return BetterRemovalConfig.get().isEnabled(key);
		}
		catch (LinkageError e) {
			return false;
		}
	}

	/**
	 * 查询字符串列表配置（声明式槽位规则 slot_rules）。读不到时返回空列表
	 * （Configured 是必需依赖，见 {@link #isContainerEnabled} 的说明）。
	 */
	public static List<String> getConfigList(String key) {
		try {
			return BetterRemovalConfig.get().getList(key);
		}
		catch (LinkageError e) {
			return List.of();
		}
	}

	/**
	 * 修饰键是否按住：左Alt（可改键）。
	 */
	public static boolean isModifierHeld(PlayerEntity player) {
		return ExtractKeyStateManager.isAltKeyDown(player);
	}

	// 容器槽位查询统一走 ContainerRegistry.find(...)，不再提供冗余的包装方法

	// FTB Ultimine 连锁
	/**
	 * FTB Ultimine连锁（取出/放入共用）的容器位置列表。
	 * 条件：配置开启 + 官方 API 能拿到连锁选区（拿不到即"没按住 Ultimine 键或形状无效"）。
	 * 点击的容器保证在列表中；数量受ftb_ultimine_max_containers限制。
	 * 返回null表示不适用连锁。
	 */
	public static List<BlockPos> getChainPositions(PlayerEntity player, BlockPos clicked) {
		if (!isContainerEnabled("ftb_ultimine")) {
			return null;
		}
		java.util.Collection<BlockPos> shape = FTBUltimineSupport.getShapePositions(player);
		if (shape == null || shape.isEmpty()) {
			return null;
		}
		int max = getConfigInt("ftb_ultimine_max_containers", 64);
		if (max <= 0) {
			return null;
		}
		// 点击的容器优先入列（并且占用一个名额），避免形状遍历顺序把它挤出 max 之外
		Set<BlockPos> unique = new LinkedHashSet<>();
		unique.add(clicked);
		for (BlockPos pos : shape) {
			if (unique.size() >= max) {
				break;
			}
			unique.add(pos);
		}
		return new ArrayList<>(unique);
	}

	/**
	 * 连锁取出：遍历Ultimine连锁形状内的所有容器，逐个按当前模式取出。
	 * 不适用的容器（未支持/被关闭）自动跳过。
	 */
	private static ActionResult handleChainExtraction(PlayerEntity player, World world, List<BlockPos> chain, SlotSet mode) {
		boolean any = false;
		for (BlockPos pos : chain) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity == null) {
				continue;
			}
			ContainerSupport support = ContainerRegistry.find(blockEntity);
			if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
				continue;
			}
			int[] slots = support.extractSlots(blockEntity, mode);
			ContainerAccess access = support.access(blockEntity, player);
			if (slots == null || access == null) {
				continue;
			}
			// 规则可以要求额外校验「该槽位能否被取出」
			boolean sided = SlotDetection.ruleRequiresSidedCheck(blockEntity);
			if (takeSlots(player, access, slots, blockEntity instanceof Inventory inventory ? inventory : null, sided)) {
				access.markDirty();
				any = true;
			}
		}
		if (!any) {
			return ActionResult.PASS;
		}
		// 取物音效只播放一次，避免连锁时刷屏（各容器的 markDirty 已在循环里逐个处理）
		playPickupSound(player, world);
		return ActionResult.SUCCESS;
	}

	/**
	 * Jade预览：计算将要取出的物品。
	 * 空手+按住修饰键+按住Ultimine键时聚合整个连锁形状内所有支持容器；否则单容器。
	 */
	public static List<ItemStack> collectPreview(PlayerEntity player, BlockEntity blockEntity, SlotSet mode) {
		boolean emptyHands = player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty();
		World world = blockEntity.getWorld();
		// 空手 + 按住修饰键且连锁生效时，聚合整个连锁形状内所有支持容器的物品
		if (emptyHands && isModifierHeld(player) && world != null) {
			List<BlockPos> chain = getChainPositions(player, blockEntity.getPos());
			if (chain != null) {
				List<ItemStack> items = new ArrayList<>();
				for (BlockPos pos : chain) {
					BlockEntity be = world.getBlockEntity(pos);
					if (be == null) {
						continue;
					}
					List<ItemStack> part = ExtractionPreviewItems.collect(player, be, mode);
					if (part != null) {
						// 跨容器也叠起来：两台机器各 32 个同类物品，预览里是一叠 64 而不是两个 32
						for (ItemStack stack : part) {
							ExtractionPreviewItems.mergeInto(items, stack);
						}
					}
					// 限制聚合大小，避免向客户端下发过大数据
					if (items.size() >= 128) {
						break;
					}
				}
				return items;
			}
		}
		return ExtractionPreviewItems.collect(player, blockEntity, mode);
	}

	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
		try {
			return handleUseBlock(player, world, hand, hitResult);
		}
		finally {
			// 菜单槽位（手动指认映射）会临时构造菜单：操作结束统一关闭，别漏掉 onClosed 钩子
			MenuSlotAccess.closeAll(player);
		}
	}

	private static ActionResult handleUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
		// 修饰键：左Alt（可改键）
		if (!isModifierHeld(player)) {
			return ActionResult.PASS;
		}
		// 只处理主手
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		// 只在服务端执行逻辑
		if (world.isClient) {
			return ActionResult.PASS;
		}

		InteractionMode state = InteractionModeManager.getState(player);

		// 只在探测/指认模式记（普通右击不记，避免刷屏）。
		// 复现时看这一行在不在：在 → 服务端确实以为 Alt 按着（问题在同步链路）；
		// 不在却仍然探测了 → 触发路径根本不是这道闸（问题在别处）。
		if (state.behavior() == Behavior.PROBE || state.behavior() == Behavior.MAPPING) {
		}

		// ---------- 手动指认：不取消交互，让原版照常打开 GUI，玩家在界面里标槽位 ----------
		if (state.behavior() == Behavior.MAPPING) {
			if (!InteractionModeManager.isMappingAvailable()) {
				// 开关关掉后玩家可能还停在"手动指认"模式
				return ActionResult.PASS;
			}
			if (player instanceof ServerPlayerEntity serverPlayer) {
				// 记下目标容器（并提前验证它的菜单能不能在没有 GUI 的情况下重建）
				MappingSessionManager.begin(serverPlayer, hitResult.getBlockPos());
			}
			// 返回 PASS：不拦原版交互，界面照常打开
			return ActionResult.PASS;
		}

		// ---------- 主动探测（实验性）：槽位探测的结果打印到聊天框，不动容器 ----------
		if (state.behavior() == Behavior.PROBE) {
			if (!InteractionModeManager.isProbeAvailable()) {
				// 开关关掉后玩家可能还停在"主动探测"模式
				return ActionResult.PASS;
			}
			if (!(player instanceof ServerPlayerEntity serverPlayer)) {
				return ActionResult.PASS;
			}
			BlockEntity blockEntity = world.getBlockEntity(hitResult.getBlockPos());
			if (blockEntity == null) {
				return ActionResult.PASS;
			}
			SlotProbeReport.send(serverPlayer, blockEntity);
			return ActionResult.SUCCESS;
		}

		boolean emptyHands = player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty();

		// ---------- 取出（空手）/ 补货（空手）----------
		if (emptyHands) {
			BlockEntity blockEntity = world.getBlockEntity(hitResult.getBlockPos());
			if (blockEntity == null) {
				return ActionResult.PASS;
			}
			// 补货：从背包向容器输入/燃料槽补充已有同类物品（空槽不补）
			if (state.behavior() == Behavior.RESTOCK) {
				return handleRestock(player, world, hitResult.getBlockPos());
			}
			// 放入预设下空手右击不动作
			if (state.behavior() != Behavior.EXTRACT) {
				return ActionResult.PASS;
			}
			return handleExtract(player, world, hitResult.getBlockPos(), blockEntity, state.slots());
		}

		// ---------- 放入（手持物品）----------
		if (state.behavior() != Behavior.DEPOSIT) {
			return ActionResult.PASS;
		}
		if (!isContainerEnabled("deposit")) {
			return ActionResult.PASS;
		}
		// 放入预设只有 input/fuel
		if (state.slots() != SlotSet.INPUT && state.slots() != SlotSet.FUEL) {
			return ActionResult.PASS;
		}
		ItemStack held = player.getMainHandStack();
		if (held.isEmpty()) {
			return ActionResult.PASS;
		}

		// FTB Ultimine连锁：一排容器一起放入
		List<BlockPos> chain = getChainPositions(player, hitResult.getBlockPos());
		if (chain != null) {
			ActionResult chainResult = handleChainDeposit(player, world, chain, state.slots(), held);
			if (chainResult == ActionResult.SUCCESS) {
				return ActionResult.SUCCESS;
			}
			// 连锁没有放入任何物品时回退到单个容器处理
		}

		BlockEntity blockEntity = world.getBlockEntity(hitResult.getBlockPos());
		if (blockEntity == null) {
			return ActionResult.PASS;
		}
		ContainerSupport support = ContainerRegistry.find(blockEntity);
		if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
			return ActionResult.PASS;
		}
		int[] slots = support.depositSlots(blockEntity, state.slots());
		ContainerAccess access = support.access(blockEntity, player);
		if (slots == null || access == null) {
			return ActionResult.PASS;
		}
		if (!depositToContainer(access, slots, held)) {
			return ActionResult.PASS;
		}
		access.markDirty();
		player.getInventory().markDirty();
		playPickupSound(player, world);
		return ActionResult.SUCCESS;
	}

	/**
	 * 取出分支：连锁 → 单容器（统一走注册表）。
	 */
	private static ActionResult handleExtract(PlayerEntity player, World world, BlockPos pos, BlockEntity blockEntity, SlotSet mode) {
		// ---------- FTB Ultimine连锁取出 ----------
		// 修饰键+空手再按住Ultimine键时，一次性取出整个连锁形状内所有支持容器的对应槽位
		List<BlockPos> chain = getChainPositions(player, pos);
		if (chain != null) {
			return handleChainExtraction(player, world, chain, mode);
		}

		ContainerSupport support = ContainerRegistry.find(blockEntity);
		if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
			return ActionResult.PASS;
		}
		int[] slots = support.extractSlots(blockEntity, mode);
		ContainerAccess access = support.access(blockEntity, player);
		if (slots == null || access == null) {
			return ActionResult.PASS;
		}
		// 规则可以要求额外校验「该槽位能否被取出」
		boolean sided = SlotDetection.ruleRequiresSidedCheck(blockEntity);
		if (!takeSlots(player, access, slots, blockEntity instanceof Inventory inventory ? inventory : null, sided)) {
			return ActionResult.PASS;
		}
		access.markDirty();
		playPickupSound(player, world);
		return ActionResult.SUCCESS;
	}

	/**
	 * 判断两个堆叠是否为同种物品（含NBT）。
	 */
	private static boolean isSameItem(ItemStack a, ItemStack b) {
		return Stacks.isSameItem(a, b);
	}

	/**
	 * 把主手物品放入容器的指定槽位（同类堆叠优先合并，空槽其次）。
	 * 每个槽位都会先经过 isValid 校验。
	 * @return 是否放入了至少一个物品
	 */
	/**
	 * 把整除后的余数<b>轮流</b>分给"还有空间"的槽位。
	 * <p>
	 * 旧写法是反复挑"权重最大"的槽 {@code share[best]++}，而权重相同时比较用的是 {@code >}，
	 * 于是每次都命中同一个（最先遇到的）槽——表现出来就是"映射里第一格总比其它格补得多"，
	 * 槽越多、存货越少偏得越明显（5 个槽时最多能差 4 个）。
	 * <p>
	 * 这里改成从前往后轮流补，一轮补不完再转一圈，直到余数分完或所有槽都满了。
	 *
	 * @param share    各槽已分到的份额（就地修改）
	 * @param capacity 各槽还能接受的上限（补货 = need，连锁放入 = 剩余容量）
	 * @param leftover 待分配的余数
	 */
	private static void spreadLeftover(int[] share, int[] capacity, int leftover) {
		if (leftover <= 0 || share == null || capacity == null) {
			return;
		}
		int n = Math.min(share.length, capacity.length);
		boolean progressed = true;
		while (leftover > 0 && progressed) {
			progressed = false;
			for (int i = 0; i < n && leftover > 0; i++) {
				if (share[i] < capacity[i]) {
					share[i]++;
					leftover--;
					progressed = true;
				}
			}
		}
	}

	private static boolean depositToContainer(ContainerAccess access, int[] slots, ItemStack held) {
		boolean movedAny = false;
		for (int slot : slots) {
			if (held.isEmpty()) {
				break;
			}
			if (depositToContainerSlot(access, slot, held, Integer.MAX_VALUE) > 0) {
				movedAny = true;
			}
		}
		return movedAny;
	}

	/**
	 * 放入单个槽位，返回实际放入数量。
	 * @param maxTake 本次最多放入的数量
	 */
	private static int depositToContainerSlot(ContainerAccess access, int slot, ItemStack held, int maxTake) {
		if (held.isEmpty() || maxTake <= 0) {
			return 0;
		}
		ItemStack portion = held.copy();
		portion.setCount(Math.min(held.getCount(), maxTake));
		int put = access.insert(slot, portion, false);
		if (put > 0) {
			held.decrement(put);
		}
		return put;
	}

	/**
	 * 连锁放入：把主手物品按权重分摊到Ultimine连锁形状内的所有可接收槽位。
	 * 权重=槽位剩余容量（空槽按整组计、同类槽按剩余空间计）
	 * 放不下的余量留在手上
	 */
	private static ActionResult handleChainDeposit(PlayerEntity player, World world, List<BlockPos> chain, SlotSet slotMode, ItemStack held) {
		// 第一遍：收集所有能接收手持物品的槽位及其权重
		List<DepositSlot> slots = new ArrayList<>();
		Set<ContainerAccess> dirty = new LinkedHashSet<>();
		for (BlockPos pos : chain) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity == null) {
				continue;
			}
			ContainerSupport support = ContainerRegistry.find(blockEntity);
			if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
				continue;
			}
			int[] depositSlots = support.depositSlots(blockEntity, slotMode);
			ContainerAccess access = support.access(blockEntity, player);
			if (depositSlots == null || access == null) {
				continue;
			}
			for (int slot : depositSlots) {
				int weight = slotFreeCapacity(access, slot, held);
				if (weight > 0) {
					slots.add(new DepositSlot(access, slot, weight));
					dirty.add(access);
				}
			}
		}
		if (slots.isEmpty()) {
			return ActionResult.PASS;
		}

		// 按权重比例分配手持物品（权重=槽位剩余容量）
		long totalWeight = 0;
		for (DepositSlot s : slots) {
			totalWeight += s.weight();
		}
		int remaining = held.getCount();
		int[] share = new int[slots.size()];
		long allocated = 0;
		for (int i = 0; i < slots.size(); i++) {
			share[i] = (int) ((long) remaining * slots.get(i).weight() / totalWeight);
			allocated += share[i];
		}
		// 余数在"还有空间"的槽位之间轮流补，而不是全塞给同一个槽
		int[] capacity = new int[slots.size()];
		for (int i = 0; i < slots.size(); i++) {
			capacity[i] = slots.get(i).weight();
		}
		spreadLeftover(share, capacity, remaining - (int) allocated);

		// 第二遍：按份额放入，实际放入少于份额的余量留在手上
		boolean any = false;
		for (int i = 0; i < slots.size(); i++) {
			if (held.isEmpty()) {
				break;
			}
			if (share[i] <= 0) {
				continue;
			}
			DepositSlot s = slots.get(i);
			if (depositToContainerSlot(s.access(), s.slot(), held, share[i]) > 0) {
				any = true;
			}
		}
		if (!any) {
			return ActionResult.PASS;
		}
		for (ContainerAccess access : dirty) {
			access.markDirty();
		}
		player.getInventory().markDirty();
		// 放入音效只播放一次，避免连锁时刷屏
		playPickupSound(player, world);
		return ActionResult.SUCCESS;
	}

	/** 连锁放入的可接收槽位（权重=槽位剩余容量） */
	private record DepositSlot(ContainerAccess access, int slot, int weight) {
	}

	/** 槽位剩余可放容量（权重）。空槽=手持堆叠上限；同类=剩余空间；其它=0 */
	private static int slotFreeCapacity(ContainerAccess access, int slot, ItemStack held) {
		ItemStack probe = held.copy();
		probe.setCount(held.getMaxCount());
		return access.insert(slot, probe, true);
	}

	/** 把放不进的物品退还到玩家背包主背包（这些物品刚从中取出，一定有空间） */
	private static void giveBackToPlayer(PlayerEntity player, int count, ItemStack template) {
		if (count <= 0 || template == null || template.isEmpty()) {
			return;
		}
		ItemStack refund = template.copy();
		refund.setCount(count);
		// 不用 PlayerInventory#offer：它在背包满时会把物品丢到地上
		insertIntoPlayerInventory(player, refund);
	}

	/**
	 * Jade放入预览：判断手持物品是否至少能放进一个目标槽位。
	 * 会逐槽检查容量与过滤，全部放不下时不显示预览。
	 * <p>
	 * 带玩家：手动指认的容器要靠他构造临时菜单。
	 */
	public static boolean canDepositTo(PlayerEntity player, BlockEntity blockEntity, SlotSet mode, ItemStack held) {
		try {
			return canDepositToInternal(player, blockEntity, mode, held);
		}
		finally {
			// 预览也会临时构造菜单：读完统一关闭
			MenuSlotAccess.closeAll(player);
		}
	}

	private static boolean canDepositToInternal(PlayerEntity player, BlockEntity blockEntity, SlotSet mode, ItemStack held) {
		if (blockEntity == null || held == null || held.isEmpty()) {
			return false;
		}
		ContainerSupport support = ContainerRegistry.find(blockEntity);
		if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
			return false;
		}
		int[] slots = support.depositSlots(blockEntity, mode);
		ContainerAccess access = support.access(blockEntity, player);
		if (slots == null || access == null) {
			return false;
		}
		for (int slot : slots) {
			if (access.insert(slot, held, true) > 0) {
				return true;
			}
		}
		return false;
	}

	// 补货
	/**
	 * 补货分支：从背包向容器输入槽和燃料槽补充已有同类物品（空槽不补）。
	 * <p>
	 * 单容器与连锁走<b>同一套</b>算法（{@link #restockAcross} 按物品分组后按各槽剩余容量加权分摊）。
	 * 早期版本单容器是"逐个槽位贪心填"：第一个槽位能吃掉全部存货，后面的槽位就一点都补不到——
	 * 4 个输入槽各缺 48、背包只有 32 时，结果是 1 号槽吃满 32，其余三个槽空着；
	 * 同一物品占了两格以上的容器（手动指认的映射、原版漏斗/发射器/投掷器）都会这样。
	 */
	private static ActionResult handleRestock(PlayerEntity player, World world, BlockPos pos) {
		if (!isContainerEnabled("restock")) {
			return ActionResult.PASS;
		}
		// FTB Ultimine连锁补货：修饰键+空手再按住Ultimine键时，对连锁形状内所有容器一起补（背包耗尽即止）
		List<BlockPos> chain = getChainPositions(player, pos);
		if (chain != null) {
			return restockAcross(player, world, chain);
		}
		// 单容器补货：走与连锁完全相同的那套"按剩余容量分摊"算法。
		return restockAcross(player, world, List.of(pos));
	}

	/**
	 * 补货：把这些容器里可补的槽位按物品类型分组，组内按权重（槽位剩余容量）分摊背包存货。
	 * 剩余越多的槽位补得越多，尽量补满；空槽不补；背包存货耗尽即止。
	 * <p>
	 * 单个容器也走这里（传一个只有它自己的位置表），这样"单容器"和"连锁"的分货行为完全一致。
	 */
	private static ActionResult restockAcross(PlayerEntity player, World world, List<BlockPos> positions) {
		// 第一遍：收集所有可补货的槽位（非空且未满、背包有同类存货）
		List<RestockSlot> slots = new ArrayList<>();
		Set<ContainerAccess> dirty = new LinkedHashSet<>();
		for (BlockPos pos : positions) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity == null) {
				continue;
			}
			ContainerSupport support = ContainerRegistry.find(blockEntity);
			if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
				continue;
			}
			int[] restockSlots = support.restockSlots(blockEntity);
			ContainerAccess access = support.access(blockEntity, player);
			if (restockSlots == null || access == null) {
				continue;
			}
			collectRestockSlots(player, access, restockSlots, slots, dirty);
		}
		if (slots.isEmpty()) {
			return ActionResult.PASS;
		}

		// 按物品类型分组，组内按权重（剩余容量）分配背包存货
		boolean any = false;
		for (int i = 0; i < slots.size(); i++) {
			RestockSlot first = slots.get(i);
			if (first == null) {
				continue;
			}
			slots.set(i, null);
			List<RestockSlot> group = new ArrayList<>();
			group.add(first);
			for (int j = i + 1; j < slots.size(); j++) {
				RestockSlot other = slots.get(j);
				if (other != null && isSameItem(first.template(), other.template())) {
					group.add(other);
					slots.set(j, null);
				}
			}
			int stock = countInInventory(player, first.template());
			if (stock <= 0) {
				continue;
			}
			long totalNeed = 0;
			for (RestockSlot g : group) {
				totalNeed += g.need();
			}
			int[] share = new int[group.size()];
			long allocated = 0;
			for (int k = 0; k < group.size(); k++) {
				// 每个槽位最多补到剩余容量（need），避免背包存货多于总需求时超量取出被容器上限截断而吞物品
				share[k] = (int) Math.min(group.get(k).need(), (long) stock * group.get(k).need() / totalNeed);
				allocated += share[k];
			}
			// 余数在"还差得下"的槽位之间轮流补，而不是全塞给同一个槽
			int[] capacity = new int[group.size()];
			for (int k = 0; k < group.size(); k++) {
				capacity[k] = group.get(k).need();
			}
			spreadLeftover(share, capacity, stock - (int) allocated);
			// 应用份额：先模拟确认可放量，再扣背包并放入，避免丢物品
			for (int k = 0; k < group.size(); k++) {
				if (share[k] <= 0) {
					continue;
				}
				RestockSlot g = group.get(k);
				ItemStack probe = g.template().copy();
				probe.setCount(share[k]);
				int canInsert = g.access().insert(g.slot(), probe, true);
				if (canInsert <= 0) {
					continue;
				}
				int taken = takeFromPlayerInventory(player, g.template(), canInsert);
				if (taken <= 0) {
					continue;
				}
				ItemStack portion = g.template().copy();
				portion.setCount(taken);
				int put = g.access().insert(g.slot(), portion, false);
				if (put < taken) {
					giveBackToPlayer(player, taken - put, g.template());
				}
				any = true;
			}
		}
		if (!any) {
			return ActionResult.PASS;
		}
		for (ContainerAccess access : dirty) {
			access.markDirty();
		}
		player.getInventory().markDirty();
		playPickupSound(player, world);
		return ActionResult.SUCCESS;
	}

	/** 连锁补货的可补货槽位（need=剩余容量） */
	private record RestockSlot(ContainerAccess access, int slot, ItemStack template, int need) {
	}

	/** 收集单个容器的可补货槽位，加入slots并登记dirty */
	private static void collectRestockSlots(PlayerEntity player, ContainerAccess access, int[] restockSlots, List<RestockSlot> slots, Set<ContainerAccess> dirty) {
		for (int slot : restockSlots) {
			ItemStack existing = access.get(slot);
			int need = restockNeed(existing);
			if (need > 0 && countInInventory(player, existing) > 0) {
				slots.add(new RestockSlot(access, slot, existing.copy(), need));
				dirty.add(access);
			}
		}
	}

	/** 补货槽位的剩余容量；空槽/已满返回0 */
	private static int restockNeed(ItemStack existing) {
		if (existing == null || existing.isEmpty()) {
			return 0;
		}
		return Math.max(0, existing.getMaxCount() - existing.getCount());
	}

	/**
	 * 背包主背包中与template同类的物品总数。
	 */
	private static int countInInventory(PlayerEntity player, ItemStack template) {
		int count = 0;
		DefaultedList<ItemStack> main = player.getInventory().main;
		for (int i = 0; i < main.size(); i++) {
			ItemStack stack = main.get(i);
			if (!stack.isEmpty() && isSameItem(stack, template)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/**
	 * 从玩家主背包取出need个与template同类的物品。
	 */
	private static int takeFromPlayerInventory(PlayerEntity player, ItemStack template, int need) {
		int taken = 0;
		DefaultedList<ItemStack> main = player.getInventory().main;
		for (int i = 0; i < main.size() && taken < need; i++) {
			ItemStack stack = main.get(i);
			if (stack.isEmpty() || !isSameItem(stack, template)) {
				continue;
			}
			int take = Math.min(need - taken, stack.getCount());
			stack.decrement(take);
			taken += take;
		}
		return taken;
	}

	// Jade 补货预览
	/**
	 * Jade补货预览：列出将从玩家背包补入容器的物品及数量（同类去重）。
	 * 数量 = min(同类槽位剩余容量总和, 背包同类存货)，与补货实际移动数一致。
	 * 按住Ultimine键（连锁生效）时汇总整个连锁形状，否则只看当前容器。
	 */
	public static List<ItemStack> collectRestockPreview(BlockEntity blockEntity, PlayerEntity player) {
		if (player == null) {
			return List.of();
		}
		try {
			return collectRestockPreviewInternal(blockEntity, player);
		}
		finally {
			// 预览会临时构造菜单（手动指认的容器）：读完统一关闭
			MenuSlotAccess.closeAll(player);
		}
	}

	private static List<ItemStack> collectRestockPreviewInternal(BlockEntity blockEntity, PlayerEntity player) {
		// 收集整个连锁形状（或单个容器）内所有可补货槽位
		List<RestockCandidate> candidates = new ArrayList<>();
		List<BlockPos> chain = getChainPositions(player, blockEntity.getPos());
		if (chain != null && blockEntity.getWorld() != null) {
			for (BlockPos pos : chain) {
				BlockEntity be = blockEntity.getWorld().getBlockEntity(pos);
				if (be != null) {
					collectRestockCandidates(player, be, candidates);
				}
			}
		}
		else {
			collectRestockCandidates(player, blockEntity, candidates);
		}

		// 按物品类型分组，累加槽位需求量，补货量 = min(总需求量, 背包同类存货)
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < candidates.size(); i++) {
			RestockCandidate first = candidates.get(i);
			if (first == null) {
				continue;
			}
			candidates.set(i, null);
			long totalNeed = first.need();
			for (int j = i + 1; j < candidates.size(); j++) {
				RestockCandidate other = candidates.get(j);
				if (other != null && isSameItem(first.template(), other.template())) {
					totalNeed += other.need();
					candidates.set(j, null);
				}
			}
			int stock = countInInventory(player, first.template());
			if (stock <= 0) {
				continue;
			}
			int toAdd = (int) Math.min(totalNeed, stock);
			if (toAdd <= 0) {
				continue;
			}
			// 同种物品叠起来，而且单叠不超过 64（物品自身的堆叠上限更小时按它算）
			ItemStack display = first.template().copy();
			display.setCount(toAdd);
			ExtractionPreviewItems.mergeInto(items, display);
		}
		return items;
	}

	/** 补货预览的可补货槽位 */
	private record RestockCandidate(ItemStack template, int need) {
	}

	/** 收集单个容器的可补货槽位（槽位非空且未满），加入candidates */
	private static void collectRestockCandidates(PlayerEntity player, BlockEntity blockEntity, List<RestockCandidate> candidates) {
		ContainerSupport support = ContainerRegistry.find(blockEntity);
		if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
			return;
		}
		int[] slots = support.restockSlots(blockEntity);
		ContainerAccess access = support.access(blockEntity, player);
		if (slots == null || access == null) {
			return;
		}
		for (int slot : slots) {
			ItemStack existing = access.get(slot);
			int need = restockNeed(existing);
			if (need > 0) {
				candidates.add(new RestockCandidate(existing.copy(), need));
			}
		}
	}

	// 取物
	/**
	 * 从指定槽位取出物品到玩家背包。
	 * <p>
	 * 取出走 {@link ContainerAccess#take(int, int, PlayerEntity)}：
	 * 菜单槽位（手动指认）会在这里走原版的"以玩家身份取出"，从而遵守模组写在槽位上的
	 * {@code canTakeItems} 规则并触发 {@code onTakeItem} 钩子；其它容器退化为「读取 + 移除」。
	 * <p>
	 * 取之前先<b>模拟</b>一次背包容量：取出来的物品不会再放回容器（输出槽通常不允许放回），
	 * 所以放不下就一点都别取。
	 *
	 * @param inventory 该容器对应的原版容器视图；用于 sided 校验，可为 null
	 * @param sided 是否额外经过「该槽位能否被取出」的校验（规则里的 sided 字段）
	 * @return 是否取出了物品
	 */
	private static boolean takeSlots(PlayerEntity player, ContainerAccess access, int[] slots, Inventory inventory, boolean sided) {
		boolean takenAny = false;
		for (int slot : slots) {
			ItemStack result = access.get(slot);
			if (result.isEmpty()) {
				continue;
			}
			if (sided && !SlotDetection.canExtract(inventory, slot, result)) {
				// 容器明确声明这个槽位不允许被取出（升级槽/展示槽之类）
				continue;
			}
			// 单次最多取到物品堆叠上限，防止容器中存在超过堆叠上限的物品（Vinery 苹果压榨器的输出槽）
			// 被原样塞进玩家背包
			int want = Math.min(result.getCount(), result.getMaxCount());
			if (want <= 0) {
				continue;
			}
			int room = insertIntoPlayerInventory(player, result, true);
			if (room <= 0) {
				continue;
			}
			ItemStack portion = access.take(slot, Math.min(want, room), player);
			if (portion.isEmpty()) {
				continue;
			}
			if (insertIntoPlayerInventory(player, portion) <= 0) {
				// 取出的数量已经按 room 限制过，正常到不了这里；
				// 真到了（例如模组的 onTakeItem 钩子动了背包）也绝不静默吞物品：丢在玩家脚下
				player.dropItem(portion, false);
				continue;
			}
			takenAny = true;
		}
		return takenAny;
	}

	/**
	 * 把 stack 放入玩家主背包（优先与同类堆叠合并，其次放入空槽），返回实际放入数量。
	 * 不使用 PlayerInventory#insertStack / #offer：
	 * - #offer 在背包放不下时会把物品丢到地上；
	 * - #insertStack 在创造模式下背包放不下时会把传入堆叠清零并返回“成功”，
	 *   直接按差值计算会把放不下的物品当成已取走而吞掉。
	 * 这里只按真实容量放置，绝不多取。
	 */
	private static int insertIntoPlayerInventory(PlayerEntity player, ItemStack stack) {
		return insertIntoPlayerInventory(player, stack, false);
	}

	/**
	 * 同上，但 {@code simulate=true} 时只计算能放入多少、不改动背包也不标脏。
	 * 取物前用它确认背包放得下，避免"先取出、放不进、又放不回去"而丢物品。
	 */
	private static int insertIntoPlayerInventory(PlayerEntity player, ItemStack stack, boolean simulate) {
		if (stack.isEmpty()) {
			return 0;
		}
		DefaultedList<ItemStack> main = player.getInventory().main;
		int inventoryMax = player.getInventory().getMaxCountPerStack();
		int remaining = stack.getCount();
		// 1) 先与背包含同类物品的堆叠合并
		for (int i = 0; i < main.size() && remaining > 0; i++) {
			ItemStack existing = main.get(i);
			if (existing.isEmpty() || !isSameItem(existing, stack)) {
				continue;
			}
			int room = Math.min(existing.getMaxCount(), inventoryMax) - existing.getCount();
			if (room <= 0) {
				continue;
			}
			int move = Math.min(room, remaining);
			if (!simulate) {
				existing.increment(move);
			}
			remaining -= move;
		}
		// 2) 再放入空槽
		for (int i = 0; i < main.size() && remaining > 0; i++) {
			if (!main.get(i).isEmpty()) {
				continue;
			}
			int move = Math.min(Math.min(stack.getMaxCount(), inventoryMax), remaining);
			if (!simulate) {
				ItemStack copy = stack.copy();
				copy.setCount(move);
				main.set(i, copy);
			}
			remaining -= move;
		}
		int inserted = stack.getCount() - remaining;
		if (!simulate && inserted > 0) {
			player.getInventory().markDirty();
		}
		return inserted;
	}

	private static void playPickupSound(PlayerEntity player, World world) {
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, 1.0f);
	}
}
