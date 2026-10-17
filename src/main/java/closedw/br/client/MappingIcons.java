package closedw.br.client;

import closedw.br.slot.SlotRole;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 指认用到的贴图资源。
 * <ul>
 *   <li>光标图（交给 {@link MappingCursors} 做系统光标）：{@code cursor_input/output/fuel/ignore.png}</li>
 *   <li>状态条背景：{@link #MAPPING_BAR} —— 由 trinkets-tooltip 项目
 *       {@code assets/trinkets-tooltip/textures/gui/inventory_revamp.png} 的左侧面板（x 0..29，y 0..147）
 *       逆时针旋转 90° 烘焙而来（同一作者 closedw 的作品；素材沿用其 LGPL-3 授权）</li>
 * </ul>
 * 都在模组资源里，缺哪张就回退，不报错。
 */
public final class MappingIcons {

    /** 状态条背景（横向 148×30）。 */
    public static final ResourceLocation MAPPING_BAR =
            ResourceLocation.fromNamespaceAndPath("betterremoval", "textures/gui/mapping_bar.png");

    private static final Map<SlotRole, ResourceLocation> TEXTURES = new EnumMap<>(SlotRole.class);
    /** 原图尺寸缓存。 */
    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();
    /** 缺图/坏图的资源，记下来免得每帧重试。 */
    private static final Set<ResourceLocation> MISSING = new HashSet<>();

    static {
        put(SlotRole.INPUT, "cursor_input.png");
        put(SlotRole.OUTPUT, "cursor_output.png");
        put(SlotRole.FUEL, "cursor_fuel.png");
        put(SlotRole.NONE, "cursor_ignore.png");
    }

    private MappingIcons() {
    }

    private static void put(SlotRole role, String file) {
        TEXTURES.put(role, ResourceLocation.fromNamespaceAndPath("betterremoval", "textures/gui/" + file));
    }

    /** 该角色的图标贴图；没配图标返回 null。 */
    public static ResourceLocation texture(SlotRole role) {
        return role == null ? null : TEXTURES.get(role);
    }

    /** 打开某个图标文件（角色版）；调用方负责 close。缺图/坏图返回 null。 */
    public static NativeImage open(SlotRole role) {
        return open(texture(role));
    }

    /** 打开某个贴图；调用方负责 close。缺图/坏图返回 null。 */
    public static NativeImage open(ResourceLocation location) {
        if (location == null || MISSING.contains(location)) {
            return null;
        }
        String path = "/assets/" + location.getNamespace() + "/" + location.getPath();
        try (InputStream in = MappingIcons.class.getResourceAsStream(path)) {
            if (in == null) {
                MISSING.add(location);
                return null;
            }
            return NativeImage.read(in);
        }
        catch (Throwable t) {
            MISSING.add(location);
            return null;
        }
    }

    /** 贴图原始尺寸 [宽, 高]；缺图返回 null（结果缓存）。 */
    public static int[] size(ResourceLocation location) {
        if (location == null) {
            return null;
        }
        int[] cached = SIZES.get(location);
        if (cached != null) {
            return cached;
        }
        try (NativeImage image = open(location)) {
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return null;
            }
            int[] size = { image.getWidth(), image.getHeight() };
            SIZES.put(location, size);
            return size;
        }
        catch (Throwable t) {
            return null;
        }
    }
}
