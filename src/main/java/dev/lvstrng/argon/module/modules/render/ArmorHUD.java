package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Renders equipped armor using Minecraft's current extracted-GUI render path. */
public final class ArmorHUD extends Module implements HudListener {
    public enum DisplayMode {
        Durability,
        Percent,
        Bar
    }

    public enum Layout {
        Horizontal,
        Vertical
    }

    private static final EquipmentSlot[] ARMOR_ORDER = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };
    private static final int CELL_SIZE = 30;

    private final NumberSetting x = new NumberSetting(EncryptedString.of("X"), 0, 3840, 10, 1);
    private final NumberSetting y = new NumberSetting(EncryptedString.of("Y"), 0, 2160, 300, 1);
    private final ModeSetting<DisplayMode> displayMode = new ModeSetting<>(
            EncryptedString.of("Display Mode"), DisplayMode.Percent, DisplayMode.class);
    private final ModeSetting<Layout> layout = new ModeSetting<>(
            EncryptedString.of("Layout"), Layout.Horizontal, Layout.class);

    public ArmorHUD() {
        super(EncryptedString.of("Armor HUD"),
                EncryptedString.of("Displays equipped armor and durability"),
                -1,
                Category.RENDER);
        addSettings(x, y, displayMode, layout);
    }

    @Override
    public void onEnable() {
        eventManager.add(HudListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(HudListener.class, this);
        super.onDisable();
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (mc.player == null || mc.level == null) return;

        List<ItemStack> armor = new ArrayList<>(4);
        for (EquipmentSlot slot : ARMOR_ORDER) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (!stack.isEmpty()) armor.add(stack);
        }
        if (armor.isEmpty()) return;

        GuiGraphicsExtractor context = event.context;
        boolean horizontal = layout.isMode(Layout.Horizontal);
        int contentWidth = horizontal ? armor.size() * CELL_SIZE : CELL_SIZE;
        int contentHeight = horizontal ? CELL_SIZE : armor.size() * CELL_SIZE;
        int baseX = clamp(x.getValueInt(), 0, Math.max(0, context.guiWidth() - contentWidth));
        int baseY = clamp(y.getValueInt(), 0, Math.max(0, context.guiHeight() - contentHeight));

        for (int index = 0; index < armor.size(); index++) {
            int cellX = baseX + (horizontal ? index * CELL_SIZE : 0);
            int cellY = baseY + (horizontal ? 0 : index * CELL_SIZE);
            renderArmorPiece(context, armor.get(index), cellX, cellY);
        }
    }

    private void renderArmorPiece(GuiGraphicsExtractor context, ItemStack stack, int cellX, int cellY) {
        int itemX = cellX + (CELL_SIZE - 16) / 2;
        context.item(stack, itemX, cellY);

        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return;

        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        float percentage = Math.max(0.0F, Math.min(1.0F, remaining / (float) stack.getMaxDamage()));
        int color = durabilityColor(percentage);

        if (displayMode.isMode(DisplayMode.Bar)) {
            int barY = cellY + 19;
            int width = Math.round(16.0F * percentage);
            context.fill(itemX, barY, itemX + 16, barY + 3, 0xB0000000);
            if (width > 0) context.fill(itemX, barY, itemX + width, barY + 3, color);
            return;
        }

        String text = displayMode.isMode(DisplayMode.Durability)
                ? Integer.toString(remaining)
                : (int) (percentage * 100.0F) + "%";
        int textX = cellX + (CELL_SIZE - mc.font.width(text)) / 2;
        context.text(mc.font, text, textX, cellY + 18, color, true);
    }

    private static int durabilityColor(float percentage) {
        return Color.HSBtoRGB(percentage / 3.0F, 1.0F, 1.0F);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
