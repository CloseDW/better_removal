package closedw.br.jade;

import closedw.br.BetterRemoval;
import closedw.br.BetterRemovalClient;
import closedw.br.ExtractionMode;
import closedw.br.ExtractionModeManager;
import closedw.br.ExtractionPreviewItems;
import closedw.br.OutputSlotExtractor;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Jade 联动：当玩家空手按住修饰键（潜行；装了Carry On用左Alt）看向容器时，
 * 在Jade提示框中添加一行高亮预览，圈出将要取出的物品。
 *
 * 物品优先读取服务端通过 {@link BetterRemovalServerData} 下发的真实数据，
 * 其次回退到客户端本地读取。
 */
public class ExtractionPreviewProvider implements IBlockComponentProvider {

    private static final int MAX_PREVIEW_ICONS = 24;

    @Override
    public ResourceLocation getUid() {
        return BetterRemoval.id("extraction_preview");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        // Jade 预览开关（Configured）
        if (!OutputSlotExtractor.isContainerEnabled("jade_preview")) {
            return;
        }
        Player player = accessor.getPlayer();
        if (player == null) {
            return;
        }
        // 空手
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return;
        }
        // 按住修饰键
        if (!isModifierHeld(player)) {
            return;
        }

        BlockEntity blockEntity = accessor.getBlockEntity();
        if (blockEntity == null) {
            return;
        }

        List<ItemStack> items = readItems(accessor, blockEntity);
        if (items == null || items.isEmpty()) {
            return;
        }

        // 高亮预览行：标签 + 物品图标，外层用彩色边框包裹
        IElementHelper helper = IElementHelper.get();
        ITooltip inner = helper.tooltip();
        inner.add(Component.translatable("betterremoval.jade.preview").withStyle(ChatFormatting.GREEN));
        int count = 0;
        for (ItemStack stack : items) {
            if (count >= MAX_PREVIEW_ICONS) {
                break;
            }
            inner.append(helper.item(stack));
            count++;
        }

        BoxStyle.GradientBorder style = BoxStyle.GradientBorder.DEFAULT_VIEW_GROUP.clone();
        style.bgColor = 0x2200AA00;
        style.borderColor = new int[] { 0xFF55FF55 };
        style.borderWidth = 1;
        tooltip.add(helper.box(inner, style));
    }

    /**
     * 优先读取服务端下发的真实物品；缺失时回退到客户端本地读取。
     */
    private List<ItemStack> readItems(BlockAccessor accessor, BlockEntity blockEntity) {
        CompoundTag serverData = accessor.getServerData();
        if (serverData.contains(BetterRemovalServerData.DATA_KEY)) {
            ListTag list = serverData.getList(BetterRemovalServerData.DATA_KEY, Tag.TAG_COMPOUND);
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(accessor.getLevel().registryAccess(), list.getCompound(i));
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
            return items;
        }
        ExtractionMode mode = ExtractionModeManager.getClientMode();
        return ExtractionPreviewItems.collect(blockEntity, mode);
    }

    private static boolean isModifierHeld(Player player) {
        if (ModList.get().isLoaded("carryon")) {
            return BetterRemovalClient.isExtractKeyPressed();
        }
        return player.isShiftKeyDown();
    }
}