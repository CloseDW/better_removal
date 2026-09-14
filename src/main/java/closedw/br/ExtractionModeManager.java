package closedw.br;

import closedw.br.networking.ExtractionModeSyncPayload;
import com.mojang.logging.LogUtils;
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
 * 兼容旧格式（仅槽位名，视为取出行为）。
 */
public final class ExtractionModeManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "better-removal-modes.properties";

    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    private static final Map<UUID, ModeState> STATES = new ConcurrentHashMap<>();

    /** 客户端缓存的当前模式（由服务端通过 S2C 包同步） */
    private static volatile ModeState CLIENT_STATE = ModeState.DEFAULT;

    /** 客户端缓存的"主动探测是否可用"（由服务端通过 S2C 包同步），模式滚轮据此决定是否多显示一个预设 */
    private static volatile boolean CLIENT_PROBE_AVAILABLE = false;

    static {
        load();
    }

    private ExtractionModeManager() {
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
            ModeState state = parseState((String) value);
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
    private static ModeState parseState(String value) {
        try {
            int split = value.indexOf(':');
            if (split >= 0) {
                return new ModeState(ExtractionAction.valueOf(value.substring(0, split)), ExtractionMode.valueOf(value.substring(split + 1)));
            }
            return new ModeState(ExtractionAction.EXTRACT, ExtractionMode.valueOf(value));
        }
        catch (Exception e) {
            return null;
        }
    }

    private static void save() {
        Properties props = new Properties();
        STATES.forEach((uuid, state) -> props.setProperty(uuid.toString(), state.action().name() + ":" + state.mode().name()));
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

    public static ModeState getState(Player player) {
        return STATES.getOrDefault(player.getUUID(), ModeState.DEFAULT);
    }

    /** 客户端缓存的当前模式（供 Jade 客户端功能读取）。 */
    public static ModeState getClientState() {
        return CLIENT_STATE;
    }

    public static void setClientState(ModeState state) {
        CLIENT_STATE = state;
    }

    /** 客户端缓存的"主动探测是否可用"（供模式滚轮读取）。 */
    public static boolean isClientProbeAvailable() {
        return CLIENT_PROBE_AVAILABLE;
    }

    public static void setClientState(ModeState state, boolean probeAvailable) {
        CLIENT_STATE = state;
        CLIENT_PROBE_AVAILABLE = probeAvailable;
    }

    /**
     * 主动探测是否可用：实验性总开关 + 主动探测开关（experimental_transfer_probe）都打开。
     * 未安装 Configured 时读不到开关，按不可用处理。
     */
    public static boolean isProbeAvailable() {
        return OutputSlotExtractor.isExperimentalEnabled()
                && OutputSlotExtractor.isContainerEnabled("experimental_transfer_probe");
    }

    /** 重新向客户端同步模式与"主动探测是否可用"（配置改完后由 /br reload 调用）。 */
    public static void refreshClient(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new ExtractionModeSyncPayload(getState(player).action(), getState(player).mode(), isProbeAvailable()));
    }

    /** 防御非法组合：放入预设只允许 input/fuel；补货固定全部；主动探测不使用槽位模式 */
    private static ModeState sanitize(ModeState state) {
        if (state.action() == ExtractionAction.PROBE) {
            return new ModeState(ExtractionAction.PROBE, ExtractionMode.ALL);
        }
        if (state.action() == ExtractionAction.DEPOSIT && state.mode() != ExtractionMode.INPUT && state.mode() != ExtractionMode.FUEL) {
            return new ModeState(ExtractionAction.DEPOSIT, ExtractionMode.INPUT);
        }
        if (state.action() == ExtractionAction.RESTOCK && state.mode() != ExtractionMode.ALL) {
            return new ModeState(ExtractionAction.RESTOCK, ExtractionMode.ALL);
        }
        return state;
    }

    public static ModeState setState(ServerPlayer player, ModeState state) {
        state = sanitize(state);
        STATES.put(player.getUUID(), state);
        save();
        PacketDistributor.sendToPlayer(player,
                new ExtractionModeSyncPayload(state.action(), state.mode(), isProbeAvailable()));
        return state;
    }

    /**
     * 生成模式提示文本，如：当前模式【取出 · 输出槽】；补货/主动探测没有槽位预设，只显示行为名
     */
    public static Component getStateMessage(ModeState state) {
        MutableComponent prefix = Component.translatable("betterremoval.message.state_prefix")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
        MutableComponent open = Component.literal("【").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA));
        MutableComponent action = Component.translatable(state.action().getTranslationKey())
                .setStyle(Style.EMPTY.withColor(state.action().getAccentColor()));
        if (!state.action().hasSlotMode()) {
            return prefix.append(open).append(action)
                    .append(Component.literal("】").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)));
        }
        MutableComponent middle = Component.literal(" · ").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA));
        MutableComponent slot = Component.translatable(state.mode().getTranslationKey())
                .setStyle(Style.EMPTY.withColor(state.mode().getAccentColor()));
        MutableComponent close = Component.literal("】").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA));
        return prefix.append(open).append(action).append(middle).append(slot).append(close);
    }
}
