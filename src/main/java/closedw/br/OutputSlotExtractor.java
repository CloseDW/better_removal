package closedw.br;

import closedw.br.config.BetterRemovalConfig;
import closedw.br.container.ContainerAccess;
import closedw.br.container.ContainerRegistry;
import closedw.br.container.ContainerSupport;
import closedw.br.container.Stacks;
import closedw.br.experimental.SlotProbeReport;
import closedw.br.ftbultimine.FTBUltimineSupport;
import closedw.br.networking.ExtractKeyStateManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
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

	private OutputSlotExtractor() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(OutputSlotExtractor::onUseBlock);
	}

	/**
	 * 查询某个容器是否启用。未安装Configured时默认全部启用
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
	 * 查询整数配置。未安装Configured时返回fallback。
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
	 * 实验性功能总开关（Configured 中的 experimental_auto_detect，默认关闭）。
	 * 手写槽位规则与自动探测都受它约束。
	 * 未安装 Configured 时读不到这个开关，按“关闭”处理（与其它容器开关的默认启用语义不同）。
	 */
	public static boolean isExperimentalEnabled() {
		try {
			return BetterRemovalConfig.get().isEnabled("experimental_auto_detect");
		}
		catch (LinkageError e) {
			return false;
		}
	}

	/**
	 * 查询字符串列表配置（实验性自动探测白名单）。未安装Configured时返回空列表。
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
	 * 条件：配置开启 + 按住Ultimine键 + Ultimine存在缓存的连锁形状。
	 * 点击的容器保证在列表中；数量受ftb_ultimine_max_containers限制。
	 * 返回null表示不适用连锁。
	 */
	public static List<BlockPos> getChainPositions(PlayerEntity player, BlockPos clicked) {
		if (!isContainerEnabled("ftb_ultimine") || !FTBUltimineSupport.isKeyHeld(player)) {
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
	private static ActionResult handleChainExtraction(PlayerEntity player, World world, List<BlockPos> chain, ExtractionMode mode) {
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
			ContainerAccess access = support.access(blockEntity);
			if (slots == null || access == null) {
				continue;
			}
			if (takeSlots(player, access, slots)) {
				access.markDirty();
				any = true;
			}
		}
		if (!any) {
			return ActionResult.PASS;
		}
		// 取物音效只播放一次，避免连锁时刷屏
		finish(player, world, () -> {
		});
		return ActionResult.SUCCESS;
	}

	/**
	 * Jade预览：计算将要取出的物品。
	 * 空手+按住修饰键+按住Ultimine键时聚合整个连锁形状内所有支持容器；否则单容器。
	 */
	public static List<ItemStack> collectPreview(PlayerEntity player, BlockEntity blockEntity, ExtractionMode mode) {
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
					List<ItemStack> part = ExtractionPreviewItems.collect(be, mode);
					if (part != null && !part.isEmpty()) {
						items.addAll(part);
					}
					// 限制聚合大小，避免向客户端下发过大NBT
					if (items.size() >= 128) {
						break;
					}
				}
				return items;
			}
		}
		return ExtractionPreviewItems.collect(blockEntity, mode);
	}

	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
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

		ModeState state = ExtractionModeManager.getState(player);

		// ---------- 主动探测（实验性）：槽位探测的结果打印到聊天框，不动容器 ----------
		if (state.action() == ExtractionAction.PROBE) {
			if (!ExtractionModeManager.isProbeAvailable()) {
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
			if (state.action() == ExtractionAction.RESTOCK) {
				return handleRestock(player, world, hitResult.getBlockPos(), blockEntity);
			}
			// 放入预设下空手右击不动作
			if (state.action() != ExtractionAction.EXTRACT) {
				return ActionResult.PASS;
			}
			return handleExtract(player, world, hitResult.getBlockPos(), blockEntity, state.mode());
		}

		// ---------- 放入（手持物品）----------
		if (state.action() != ExtractionAction.DEPOSIT) {
			return ActionResult.PASS;
		}
		if (!isContainerEnabled("deposit")) {
			return ActionResult.PASS;
		}
		// 放入预设只有 input/fuel
		if (state.mode() != ExtractionMode.INPUT && state.mode() != ExtractionMode.FUEL) {
			return ActionResult.PASS;
		}
		ItemStack held = player.getMainHandStack();
		if (held.isEmpty()) {
			return ActionResult.PASS;
		}

		// FTB Ultimine连锁：一排容器一起放入
		List<BlockPos> chain = getChainPositions(player, hitResult.getBlockPos());
		if (chain != null) {
			ActionResult chainResult = handleChainDeposit(player, world, chain, state.mode(), held);
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
		int[] slots = support.depositSlots(blockEntity, state.mode());
		ContainerAccess access = support.access(blockEntity);
		if (slots == null || access == null) {
			return ActionResult.PASS;
		}
		if (!depositToContainer(access, slots, held)) {
			return ActionResult.PASS;
		}
		finish(player, world, () -> {
			access.markDirty();
			player.getInventory().markDirty();
		});
		return ActionResult.SUCCESS;
	}

	/**
	 * 取出分支：连锁 → 单容器（统一走注册表）。
	 */
	private static ActionResult handleExtract(PlayerEntity player, World world, BlockPos pos, BlockEntity blockEntity, ExtractionMode mode) {
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
		ContainerAccess access = support.access(blockEntity);
		if (slots == null || access == null) {
			return ActionResult.PASS;
		}
		if (!takeSlots(player, access, slots)) {
			return ActionResult.PASS;
		}
		finish(player, world, access::markDirty);
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
	private static ActionResult handleChainDeposit(PlayerEntity player, World world, List<BlockPos> chain, ExtractionMode slotMode, ItemStack held) {
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
			ContainerAccess access = support.access(blockEntity);
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
		// 余数补足给权重最大且未填满的槽位
		int leftover = remaining - (int) allocated;
		while (leftover > 0) {
			int best = -1;
			for (int i = 0; i < slots.size(); i++) {
				if (share[i] < slots.get(i).weight() && (best == -1 || slots.get(i).weight() > slots.get(best).weight())) {
					best = i;
				}
			}
			if (best == -1) {
				break;
			}
			share[best]++;
			leftover--;
		}

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
		finish(player, world, () -> {
		});
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
		player.getInventory().offer(refund, false);
	}

	/**
	 * Jade放入预览：判断手持物品是否至少能放进一个目标槽位。
	 * 会逐槽检查容量与过滤，全部放不下时不显示预览。
	 */
	public static boolean canDepositTo(BlockEntity blockEntity, ExtractionMode mode, ItemStack held) {
		if (blockEntity == null || held == null || held.isEmpty()) {
			return false;
		}
		ContainerSupport support = ContainerRegistry.find(blockEntity);
		if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
			return false;
		}
		int[] slots = support.depositSlots(blockEntity, mode);
		ContainerAccess access = support.access(blockEntity);
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

	// ------------------------------------------------------------------
	// 补货
	// ------------------------------------------------------------------

	/**
	 * 补货分支：从背包向容器输入槽和燃料槽补充已有同类物品（空槽不补）。
	 */
	private static ActionResult handleRestock(PlayerEntity player, World world, BlockPos pos, BlockEntity blockEntity) {
		if (!isContainerEnabled("restock")) {
			return ActionResult.PASS;
		}
		// ---------- FTB Ultimine连锁补货 ----------
		// 修饰键+空手再按住Ultimine键时，对连锁形状内所有容器逐个补货（背包耗尽即止）
		List<BlockPos> chain = getChainPositions(player, pos);
		if (chain != null) {
			return handleChainRestock(player, world, chain);
		}

		ContainerSupport support = ContainerRegistry.find(blockEntity);
		if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
			return ActionResult.PASS;
		}
		int[] slots = support.restockSlots(blockEntity);
		ContainerAccess access = support.access(blockEntity);
		if (slots == null || access == null) {
			return ActionResult.PASS;
		}
		if (!restockAccess(player, access, slots)) {
			return ActionResult.PASS;
		}
		access.markDirty();
		player.getInventory().markDirty();
		finish(player, world, () -> {
		});
		return ActionResult.SUCCESS;
	}

	/**
	 * 补货：从背包向容器指定槽位补充已有同类物品到槽位上限，空槽不补。
	 * @return 是否补充了至少一个物品
	 */
	private static boolean restockAccess(PlayerEntity player, ContainerAccess access, int[] slots) {
		boolean movedAny = false;
		for (int slot : slots) {
			ItemStack existing = access.get(slot);
			if (existing.isEmpty()) {
				// 空槽不补
				continue;
			}
			int need = existing.getMaxCount() - existing.getCount();
			if (need <= 0) {
				// 已满
				continue;
			}
			int stock = countInInventory(player, existing);
			if (stock <= 0) {
				// 背包没有同类存货
				continue;
			}
			// 单容器补货：需要量与背包存货取小者
			int want = Math.min(need, stock);
			// 先模拟放入确认实际可放数量，避免先扣背包后放不进而丢物品
			ItemStack probe = existing.copy();
			probe.setCount(want);
			int canInsert = access.insert(slot, probe, true);
			if (canInsert <= 0) {
				continue;
			}
			int taken = takeFromPlayerInventory(player, existing, canInsert);
			if (taken <= 0) {
				continue;
			}
			ItemStack portion = existing.copy();
			portion.setCount(taken);
			int put = access.insert(slot, portion, false);
			if (put < taken) {
				giveBackToPlayer(player, taken - put, existing);
			}
			movedAny = true;
		}
		return movedAny;
	}

	/**
	 * 连锁补货：把背包存货按物品类型分组，组内按权重（槽位剩余容量）分摊到连锁形状内所有补货槽。
	 * 剩余越多的槽位补得越多，尽量补满；空槽不补；背包存货耗尽即止。
	 */
	private static ActionResult handleChainRestock(PlayerEntity player, World world, List<BlockPos> chain) {
		// 第一遍：收集所有可补货的槽位（非空且未满、背包有同类存货）
		List<RestockSlot> slots = new ArrayList<>();
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
			int[] restockSlots = support.restockSlots(blockEntity);
			ContainerAccess access = support.access(blockEntity);
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
			// 余数补足给需求最大且未填满的槽位
			int leftover = stock - (int) allocated;
			while (leftover > 0) {
				int best = -1;
				for (int k = 0; k < group.size(); k++) {
					if (share[k] < group.get(k).need() && (best == -1 || group.get(k).need() > group.get(best).need())) {
						best = k;
					}
				}
				if (best == -1) {
					break;
				}
				share[best]++;
				leftover--;
			}
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
		finish(player, world, () -> {
		});
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
		// 收集整个连锁形状（或单个容器）内所有可补货槽位
		List<RestockCandidate> candidates = new ArrayList<>();
		List<BlockPos> chain = getChainPositions(player, blockEntity.getPos());
		if (chain != null && blockEntity.getWorld() != null) {
			for (BlockPos pos : chain) {
				BlockEntity be = blockEntity.getWorld().getBlockEntity(pos);
				if (be != null) {
					collectRestockCandidates(be, candidates);
				}
			}
		}
		else {
			collectRestockCandidates(blockEntity, candidates);
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
			ItemStack display = first.template().copy();
			display.setCount(toAdd);
			items.add(display);
		}
		return items;
	}

	/** 补货预览的可补货槽位 */
	private record RestockCandidate(ItemStack template, int need) {
	}

	/** 收集单个容器的可补货槽位（槽位非空且未满），加入candidates */
	private static void collectRestockCandidates(BlockEntity blockEntity, List<RestockCandidate> candidates) {
		ContainerSupport support = ContainerRegistry.find(blockEntity);
		if (support == null || !support.enabled() || !support.allowInteraction(blockEntity)) {
			return;
		}
		int[] slots = support.restockSlots(blockEntity);
		ContainerAccess access = support.access(blockEntity);
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

	// ------------------------------------------------------------------
	// 取物与工具
	// ------------------------------------------------------------------

	/**
	 * 从指定槽位取出物品到玩家背包。
	 * @return 是否取出了物品
	 */
	private static boolean takeSlots(PlayerEntity player, ContainerAccess access, int[] slots) {
		boolean takenAny = false;
		for (int slot : slots) {
			ItemStack result = access.get(slot);
			if (result.isEmpty()) {
				continue;
			}
			int taken = tryTakeSlot(player, result);
			if (taken <= 0) {
				continue;
			}
			access.remove(slot, taken);
			takenAny = true;
		}
		return takenAny;
	}

	/**
	 * 使用 insertStack
	 * 不使用offer，offer在背包放不下时会dropItem直接扔到地上
	 * 额外限制：单次最多取出到物品堆叠上限，防止容器中存在超过堆叠上限的物品
	 *  Vinery 苹果压榨器的输出槽 BUG被原样塞进玩家背包。
	 */
	private static int tryTakeSlot(PlayerEntity player, ItemStack result) {
		int maxCount = result.getMaxCount();
		int takeCount = Math.min(result.getCount(), maxCount);
		ItemStack toInsert = result.copy();
		toInsert.setCount(takeCount);
		player.getInventory().insertStack(toInsert);
		return takeCount - toInsert.getCount();
	}

	private static void finish(PlayerEntity player, World world, Runnable markDirty) {
		markDirty.run();
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, 1.0f);
	}
}
