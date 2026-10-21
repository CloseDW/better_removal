package closedw.br.container.sync;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 容器内容改动后把新内容推给客户端 —— 全模组唯一的同步出口。
 * <p>
 * 走原版 {@code level.sendBlockUpdated(pos, state, state, UPDATE_ALL)}：服务端只把坐标记进
 * {@code ChunkHolder.changedBlocksPerSection}，真正发包发生在该区块下一个 tick 的
 * {@code ChunkHolder.broadcastChanges} 里，同一 section 的多个坐标会合并成一个
 * {@code ClientboundSectionBlocksUpdatePacket}。所以连锁操作（一次最多
 * {@code ftb_ultimine_max_containers} 台容器）不会按容器数线性放大流量。
 * <p>
 * <b>只对"愿意把方块实体数据发给客户端"的方块实体发起同步</b>：原版在该坐标的方块实体
 * {@link BlockEntity#getUpdatePacket()} 非空时才会附带 {@code ClientboundBlockEntityDataPacket}
 * （{@code ChunkHolder#broadcastBlockEntityIfNeeded}），而该方法默认返回 {@code null}。
 * 原版箱子/熔炉/漏斗这类容器都没有重写它，客户端那份方块实体本来也不持有容器内容
 * （GUI 内容由菜单下发），给它们发包纯属空转，因此这里直接跳过。
 * <p>
 * 判定按类缓存，不构造任何对象；方块实体没挂到服务端世界（{@code getLevel()} 为 null
 * 或已在客户端）时同样跳过。这就是 Ad Astra 机器那种"客户端也缓存了物品列表"的容器
 * 所需要的同步 —— 它的 {@code MachineBlockEntity#sync()} 内部也只是
 * {@code level().sendBlockUpdated(...)}，所以不再需要为每个模组各写一层反射调用。
 */
public final class ContainerSync {

    /** 方块实体类 → 是否重写了 {@code getUpdatePacket()}（即是否会下发方块实体数据）。 */
    private static final Map<Class<?>, Boolean> PUSHES_DATA = new ConcurrentHashMap<>();

    private ContainerSync() {
    }

    /**
     * 容器内容改动后调用：把新内容同步给附近所有加载了该区块的玩家。
     * <p>
     * 调用方只需保证传入的方块实体确实被改过（槽位内容有变化），重复调用没有副作用
     * ——原版按坐标去重。
     */
    public static void sync(BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved()) {
            return;
        }
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!pushesBlockEntityData(blockEntity.getClass())) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        BlockState state = blockEntity.getBlockState();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    private static boolean pushesBlockEntityData(Class<?> type) {
        return PUSHES_DATA.computeIfAbsent(type, ContainerSync::overridesUpdatePacket);
    }

    private static boolean overridesUpdatePacket(Class<?> type) {
        try {
            return type.getMethod("getUpdatePacket").getDeclaringClass() != BlockEntity.class;
        }
        catch (Throwable t) {
            // 取不到就当作"不下发"，宁可不发也不发包扰民
            return false;
        }
    }
}
