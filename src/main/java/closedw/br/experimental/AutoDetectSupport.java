package closedw.br.experimental;

import closedw.br.ExtractionMode;
import closedw.br.OutputSlotExtractor;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 实验性：未知容器的槽位判定（默认关闭）。
 */
public final class AutoDetectSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, String> LAST_LOGGED = new ConcurrentHashMap<>();

    private AutoDetectSupport() {
    }

    public static boolean hasRule(BlockEntity blockEntity) {
        if (blockEntity == null || !(blockEntity instanceof Container)) {
            return false;
        }
        if (!OutputSlotExtractor.isExperimentalEnabled()) {
            return false;
        }
        return SlotRules.find(blockEntity) != null;
    }

    public static int[] getRuleSlotsForMode(BlockEntity blockEntity, ExtractionMode mode) {
        if (mode == null || !(blockEntity instanceof Container container)) {
            return null;
        }
        if (!OutputSlotExtractor.isExperimentalEnabled()) {
            return null;
        }
        SlotRule rule = SlotRules.find(blockEntity);
        if (rule == null) {
            return null;
        }
        SlotRole[] roles;
        try {
            roles = rule.apply(container.getContainerSize());
        }
        catch (Throwable t) {
            return null;
        }
        logDetection(blockEntity, "rule:" + rule.match(), roles);
        return slotsForMode(roles, mode);
    }

    public static boolean isEnabledFor(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        if (!OutputSlotExtractor.isExperimentalEnabled()) {
            return false;
        }
        if (!(blockEntity instanceof Container)) {
            return false;
        }
        return matchesWhitelist(blockEntity);
    }

    public static int[] getSlotsForMode(BlockEntity blockEntity, ExtractionMode mode) {
        if (mode == null || !(blockEntity instanceof Container container)) {
            return null;
        }
        Detection detection = detect(blockEntity, container);
        if (detection == null) {
            return null;
        }
        logDetection(blockEntity, detection.source(), detection.roles());
        return slotsForMode(detection.roles(), mode);
    }

    public static int[] getDepositSlots(BlockEntity blockEntity, ExtractionMode mode) {
        if (mode != ExtractionMode.INPUT && mode != ExtractionMode.FUEL) {
            return null;
        }
        return getSlotsForMode(blockEntity, mode);
    }

    private static int[] slotsForMode(SlotRole[] roles, ExtractionMode mode) {
        Set<Integer> slots = new LinkedHashSet<>();
        for (int slot = 0; slot < roles.length; slot++) {
            switch (mode) {
                case OUTPUT -> {
                    if (roles[slot] == SlotRole.OUTPUT) {
                        slots.add(slot);
                    }
                }
                case INPUT -> {
                    if (roles[slot] == SlotRole.INPUT) {
                        slots.add(slot);
                    }
                }
                case FUEL -> {
                    if (roles[slot] == SlotRole.FUEL) {
                        slots.add(slot);
                    }
                }
                case ALL -> {
                    if (roles[slot] != SlotRole.NONE) {
                        slots.add(slot);
                    }
                }
            }
        }
        if (slots.isEmpty()) {
            return null;
        }
        int[] result = new int[slots.size()];
        int index = 0;
        for (int slot : slots) {
            result[index++] = slot;
        }
        return result;
    }

    private static Detection detect(BlockEntity blockEntity, Container container) {
        // 优先级1：GUI 菜单语义（只读判定）
        SlotRole[] menuRoles = MenuSlotSupport.detect(blockEntity);
        if (menuRoles != null && menuRoles.length == container.getContainerSize()) {
            return new Detection(menuRoles, "menu");
        }

        // 优先级2：容器接口启发式
        SlotRole[] roles = inventoryRoles(container);
        for (SlotRole role : roles) {
            if (role != SlotRole.NONE) {
                return new Detection(roles, "inventory");
            }
        }
        return null;
    }

    static SlotRole[] inventoryRoles(Container container) {
        int size;
        try {
            size = container.getContainerSize();
        }
        catch (Throwable t) {
            return new SlotRole[0];
        }
        SlotRole[] roles = new SlotRole[size];
        WorldlyContainer sided = container instanceof WorldlyContainer w ? w : null;
        for (int slot = 0; slot < size; slot++) {
            int index = slot;
            Predicate<ItemStack> accepts = stack -> isValid(container, index, stack);
            if (Probes.anyOf(accepts, Probes.GENERIC)) {
                roles[slot] = Probes.fuelOrInput(accepts);
                continue;
            }
            ItemStack existing = getStack(container, slot);
            if (!existing.isEmpty() && accepts.test(existing)) {
                roles[slot] = SlotRole.NONE;
                continue;
            }
            boolean extractable = sided != null ? canExtractAnySide(sided, slot) : true;
            roles[slot] = extractable ? SlotRole.OUTPUT : SlotRole.NONE;
        }
        return roles;
    }

    private static boolean isValid(Container container, int slot, ItemStack stack) {
        try {
            return container.canPlaceItem(slot, stack);
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private static ItemStack getStack(Container container, int slot) {
        try {
            ItemStack stack = container.getItem(slot);
            return stack == null ? ItemStack.EMPTY : stack;
        }
        catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean canExtractAnySide(WorldlyContainer container, int slot) {
        ItemStack probe = new ItemStack(Items.STONE);
        for (Direction side : Direction.values()) {
            try {
                if (container.canTakeItemThroughFace(slot, probe, side)) {
                    return true;
                }
            }
            catch (Throwable ignored) {
                // 同上
            }
        }
        return false;
    }

    private static boolean matchesWhitelist(BlockEntity blockEntity) {
        List<String> whitelist = OutputSlotExtractor.getConfigList("experimental_auto_detect_whitelist");
        if (whitelist.isEmpty()) {
            return false;
        }
        for (String entry : whitelist) {
            if (SlotRules.matchesEntry(blockEntity, entry)) {
                return true;
            }
        }
        return false;
    }

    private static void logDetection(BlockEntity blockEntity, String source, SlotRole[] roles) {
        ResourceLocation id = SlotRules.blockId(blockEntity);
        String key = (id != null ? id.toString() : blockEntity.getClass().getName());
        StringBuilder signature = new StringBuilder(source).append(" |");
        for (int slot = 0; slot < roles.length; slot++) {
            signature.append(' ').append(slot).append('=').append(roles[slot].name());
        }
        String value = signature.toString();
        if (value.equals(LAST_LOGGED.put(key, value))) {
            return;
        }
        LOGGER.info("[auto-detect] {} -> {}", key, value);
    }

    private record Detection(SlotRole[] roles, String source) {
    }
}
