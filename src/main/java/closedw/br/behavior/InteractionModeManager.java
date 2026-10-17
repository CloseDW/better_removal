package closedw.br.behavior;

import closedw.br.BetterRemoval;
import closedw.br.OutputSlotExtractor;
import closedw.br.mapping.MappingSessionManager;
import closedw.br.networking.ExtractionModeSyncPayload;
import closedw.br.slot.SlotSet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按玩家记录交互模式（行为=取出/放入/补货/主动探测 + 槽位），并持久化到配置文件（服务器重启后仍保留）。
 * 配置文件位于 config/better-removal-modes.properties，键为玩家UUID，值为"ACTION:MODE"。
 */
public final class InteractionModeManager {

    private static final Logger LOGGER = BetterRemoval.LOGGER;
    private static final String FILE_NAME = "better-removal-modes.properties";

    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    private static final Map<UUID, InteractionMode> STATES = new ConcurrentHashMap<>();

    /** 客户端缓存的当前模式（由服务端通过 S2C 包同步） */
    private static volatile InteractionMode CLIENT_STATE = InteractionMode.DEFAULT;

    /** 客户端缓存的"主动探测是否可用"，模式滚轮据此决定是否多显示一个预设 */
    private static volatile boolean CLIENT_PROBE_AVAILABLE = false;

    /** 客户端缓存的"手动指认是否可用"，模式滚轮据此决定是否多显示一个预设 */
    private static volatile boolean CLIENT_MAPPING_AVAILABLE = false;

    static {
        load();
    }

    private InteractionModeManager() {
    }

    private static void load() {
        Properties props = new Properties();
        if (Files.exists(PATH)) {
            try (InputStream in = Files.newInputStream(PATH)) {
                props.load(in);
            }
            catch (IOException e) {
                LOGGER.warn("Failed to load extraction modes from {}", PATH, e);
            }
        }
        props.forEach((key, value) -> {
            InteractionMode state = parseState((String) value);
            if (state == null) {
                LOGGER.warn("Skipping invalid extraction mode entry: {}={}", key, value);
                return;
            }
            try {
                STATES.put(UUID.fromString((String) key), state);
            }
            catch (IllegalArgumentException e) {
                LOGGER.warn("Skipping extraction mode entry with invalid player UUID: {}={}", key, value);
            }
        });
    }

    /** 解析"ACTION:MODE"，兼容旧格式（仅槽位名 = 取出行为） */
    private static InteractionMode parseState(String value) {
        try {
            int split = value.indexOf(':');
            if (split >= 0) {
                return new InteractionMode(Behavior.valueOf(value.substring(0, split)), SlotSet.valueOf(value.substring(split + 1)));
            }
            return new InteractionMode(Behavior.EXTRACT, SlotSet.valueOf(value));
        }
        catch (Exception e) {
            return null;
        }
    }

    private static void save() {
        Properties props = new Properties();
        STATES.forEach((uuid, state) -> props.setProperty(uuid.toString(), state.behavior().name() + ":" + state.slots().name()));
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream out = Files.newOutputStream(PATH)) {
                props.store(out, "Better Removal per-player modes");
            }
        }
        catch (IOException e) {
            LOGGER.error("Failed to save extraction modes to {}", PATH, e);
        }
    }

    public static InteractionMode getState(Player player) {
        return STATES.getOrDefault(player.getUUID(), InteractionMode.DEFAULT);
    }

    /** 客户端缓存的当前模式（供 Jade客户端功能读取）。 */
    public static InteractionMode getClientState() {
        return CLIENT_STATE;
    }

    /** 客户端缓存的"主动探测是否可用"（供模式滚轮读取）。 */
    public static boolean isClientProbeAvailable() {
        return CLIENT_PROBE_AVAILABLE;
    }

    /** 客户端缓存的"手动指认是否可用"（供模式滚轮读取）。 */
    public static boolean isClientMappingAvailable() {
        return CLIENT_MAPPING_AVAILABLE;
    }

    public static void setClientState(InteractionMode state, boolean probeAvailable, boolean mappingAvailable) {
        CLIENT_STATE = state;
        CLIENT_PROBE_AVAILABLE = probeAvailable;
        CLIENT_MAPPING_AVAILABLE = mappingAvailable;
    }

    /**
     * 主动探测是否可用：只看 experimental_transfer_probe 这一个开关。
     * 未安装 Configured 时读不到开关，按不可用处理。
     */
    public static boolean isProbeAvailable() {
        return OutputSlotExtractor.isContainerEnabled("experimental_transfer_probe");
    }

    /**
     * 手动指认是否可用：只受 menu_mapping 开关约束（实验性，默认关闭）。
     */
    public static boolean isMappingAvailable() {
        return OutputSlotExtractor.isContainerEnabled("menu_mapping");
    }

    /** 重新向客户端同步模式与"主动探测是否可用"（配置改完后由 /br reload 调用）。 */
    public static void refreshClient(ServerPlayer player) {
        sendSync(player, getState(player));
    }

    /** 向单个玩家下发当前模式与"主动探测/手动指认是否可用"。 */
    public static void sendSync(ServerPlayer player, InteractionMode state) {
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new ExtractionModeSyncPayload(state.behavior(), state.slots(), isProbeAvailable(), isMappingAvailable()));
    }

    /** 防御非法组合：放入预设只允许 input/fuel；补货固定全部；主动探测/手动指认不使用槽位模式 */
    private static InteractionMode sanitize(InteractionMode state) {
        if (state.behavior() == Behavior.PROBE || state.behavior() == Behavior.MAPPING) {
            return new InteractionMode(state.behavior(), SlotSet.ALL);
        }
        if (state.behavior() == Behavior.DEPOSIT && state.slots() != SlotSet.INPUT && state.slots() != SlotSet.FUEL) {
            return new InteractionMode(Behavior.DEPOSIT, SlotSet.INPUT);
        }
        if (state.behavior() == Behavior.RESTOCK && state.slots() != SlotSet.ALL) {
            return new InteractionMode(Behavior.RESTOCK, SlotSet.ALL);
        }
        return state;
    }

    public static InteractionMode setState(ServerPlayer player, InteractionMode state) {
        state = sanitize(state);
        STATES.put(player.getUUID(), state);
        save();
        if (state.behavior() != Behavior.MAPPING) {
            // 切走指认模式就结束当前会话，免得客户端还挂着叠层标签
            MappingSessionManager.end(player);
        }
        sendSync(player, state);
        return state;
    }

    /**
     * 生成模式提示文本，如：当前模式【取出 · 输出槽】；补货/主动探测没有槽位预设，只显示行为名。
     */
    public static Component getStateMessage(InteractionMode state) {
        MutableComponent prefix = Component.translatable("betterremoval.message.state_prefix")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        MutableComponent open = Component.literal("【").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA));
        MutableComponent action = Component.translatable(state.behavior().getTranslationKey())
                .setStyle(Style.EMPTY.withColor(state.behavior().getAccentColor()));
        if (!state.behavior().hasSlotMode()) {
            return prefix.append(open).append(action)
                    .append(Component.literal("】").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)));
        }
        MutableComponent middle = Component.literal(" · ").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA));
        MutableComponent slot = Component.translatable(state.slots().getTranslationKey())
                .setStyle(Style.EMPTY.withColor(state.slots().getAccentColor()));
        MutableComponent close = Component.literal("】").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA));
        return prefix.append(open).append(action).append(middle).append(slot).append(close);
    }
}
