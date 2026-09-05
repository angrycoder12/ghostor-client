package dev.lvstrng.argon.gui.components;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.config.ConfigManager;
import dev.lvstrng.argon.config.ConfigManager.ConfigSummary;
import dev.lvstrng.argon.config.ConfigManager.SwitchResult;
import dev.lvstrng.argon.config.ConfigManager.UnsavedAction;
import dev.lvstrng.argon.config.NativeFileDialogs;
import dev.lvstrng.argon.gui.ClickGui;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.utils.TextRenderer;
import java.awt.Color;
import java.nio.file.Path;
import java.util.List;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Named config list, file actions, and destructive/switch confirmations. */
public final class ConfigManagerPanel {
    public static final int PREFERRED_WIDTH = 570;
    public static final int PREFERRED_HEIGHT = 500;
    private static final int ROW_HEIGHT = 55;
    private final ClickGui parent;
    private int x;
    private int y;
    private int width = PREFERRED_WIDTH;
    private int height = PREFERRED_HEIGHT;
    private int scroll;
    private String status = "";
    private String pendingSwitch;
    private String pendingDelete;

    public ConfigManagerPanel(ClickGui parent) {
        this.parent = parent;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        GhostorTheme.glowOutline(context, x, y, x + width, y + height, GhostorTheme.RADIUS);
        GhostorTheme.panel(context, x, y, x + width, y + height, GhostorTheme.SURFACE_GLASS, GhostorTheme.RADIUS);
        GhostorTheme.outline(context, x, y, x + width, y + height, GhostorTheme.ACCENT_BORDER, GhostorTheme.RADIUS);
        context.fill(x + width / 2 - 14, y - 2, x + width / 2 + 14, y + 2, GhostorTheme.ACCENT.getRGB());
		TextRenderer.drawString("Configs", context, x + 20, y + 25, GhostorTheme.TEXT.getRGB());
        TextRenderer.drawSmallString("Switch, save, import, and export complete Ghostor setups", context,
                x + 20, y + 48, GhostorTheme.TEXT_MUTED.getRGB());

        List<ConfigSummary> configs = manager().summaries();
        int listTop = y + 78;
        int listBottom = y + height - 94;
        int visible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, configs.size() - visible)));
        context.enableScissor(x + 12, listTop, x + width - 12, listBottom);
        for (int row = 0; row < visible && row + scroll < configs.size(); row++) {
            ConfigSummary config = configs.get(row + scroll);
            renderRow(context, mouseX, mouseY, config, listTop + row * ROW_HEIGHT);
        }
        context.disableScissor();

        int footerY = y + height - 57;
        drawSecondaryButton(context, x + 18, footerY, x + 80, footerY + 36, "Back",
                hovered(mouseX, mouseY, x + 18, footerY, x + 80, footerY + 36));
        button(context, mouseX, mouseY, x + 90, footerY, 98, "New Conf.", false);
        button(context, mouseX, mouseY, x + 198, footerY, 86, "Upload", false);
        if (manager().isDirty()) button(context, mouseX, mouseY, x + 294, footerY, 100, "Save Active", false);
        String message = status.isBlank() ? activeLabel() : status;
        String fittedMessage = fit(message, width - 40);
        TextRenderer.drawSmallString(fittedMessage, context, x + 20, footerY - 18,
                status.isBlank() ? GhostorTheme.TEXT_MUTED.getRGB() : GhostorTheme.ACCENT_HOVER.getRGB());

        if (pendingSwitch != null) renderConfirm(context, mouseX, mouseY,
                "Unsaved changes", "Save changes before switching?", true);
        else if (pendingDelete != null) renderConfirm(context, mouseX, mouseY,
                "Delete config?", "This cannot be undone.", false);
    }

    private void renderRow(GuiGraphicsExtractor context, int mouseX, int mouseY, ConfigSummary config, int rowY) {
        boolean over = hovered(mouseX, mouseY, x + 18, rowY, x + width - 18, rowY + 48);
        Color fill = config.active() ? GhostorTheme.ACTIVE_SURFACE
                : over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED;
        GhostorTheme.panel(context, x + 18, rowY, x + width - 18, rowY + 48, fill, 7);
        GhostorTheme.outline(context, x + 18, rowY, x + width - 18, rowY + 48,
                config.active() ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 7);
        if (config.active()) context.fill(x + 18, rowY + 9, x + 21, rowY + 39, GhostorTheme.ACCENT.getRGB());
        TextRenderer.drawString(fit(config.name(), Math.max(100, width - 280)), context,
                x + 33, rowY + 10, GhostorTheme.TEXT.getRGB());
        String flags = config.permanent() ? "Permanent • Auto Save"
                : (config.autoSave() ? "Auto Save" : "Manual")
                + (config.active() ? " • Active" : "") + (config.dirty() ? " • Unsaved" : "");
        TextRenderer.drawSmallString(flags, context, x + 33, rowY + 30,
                config.dirty() ? GhostorTheme.DANGER_HOVER.getRGB() : GhostorTheme.TEXT_MUTED.getRGB());
        if (!config.permanent()) {
			secondary(context, mouseX, mouseY, x + width - 168, rowY + 9, 74, "Download");
            danger(context, mouseX, mouseY, x + width - 84, rowY + 9, 54, "Delete");
        }
    }

    private void renderConfirm(GuiGraphicsExtractor context, int mouseX, int mouseY,
            String title, String subtitle, boolean threeButtons) {
        int boxWidth = Math.min(380, width - 50);
        int boxHeight = 150;
        int boxX = x + (width - boxWidth) / 2;
        int boxY = y + (height - boxHeight) / 2;
        GhostorTheme.panel(context, x, y, x + width, y + height, GhostorTheme.BACKDROP, GhostorTheme.RADIUS);
        GhostorTheme.panel(context, boxX, boxY, boxX + boxWidth, boxY + boxHeight,
                GhostorTheme.SURFACE, 10);
        GhostorTheme.outline(context, boxX, boxY, boxX + boxWidth, boxY + boxHeight,
                GhostorTheme.ACCENT_BORDER, 10);
        TextRenderer.drawString(title, context, boxX + 18, boxY + 22, GhostorTheme.TEXT.getRGB());
        TextRenderer.drawSmallString(subtitle, context, boxX + 18, boxY + 47, GhostorTheme.TEXT_MUTED.getRGB());
        int buttonY = boxY + 94;
        if (threeButtons) {
            button(context, mouseX, mouseY, boxX + 16, buttonY, 100, "Save", false);
            danger(context, mouseX, mouseY, boxX + 126, buttonY, 100, "Discard");
            secondary(context, mouseX, mouseY, boxX + 236, buttonY, boxWidth - 252, "Cancel");
        } else {
            danger(context, mouseX, mouseY, boxX + 70, buttonY, 110, "Delete");
            secondary(context, mouseX, mouseY, boxX + 194, buttonY, 110, "Cancel");
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) return false;
        if (pendingSwitch != null) return handleSwitchConfirm(mouseX, mouseY, button);
        if (pendingDelete != null) return handleDeleteConfirm(mouseX, mouseY, button);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return true;

        List<ConfigSummary> configs = manager().summaries();
        int listTop = y + 78;
        int listBottom = y + height - 94;
        int visible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        for (int row = 0; row < visible && row + scroll < configs.size(); row++) {
            ConfigSummary config = configs.get(row + scroll);
            int rowY = listTop + row * ROW_HEIGHT;
            if (!hovered(mouseX, mouseY, x + 18, rowY, x + width - 18, rowY + 48)) continue;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !config.permanent()) {
                parent.openConfigEdit(config.id());
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !config.permanent()
					&& hovered(mouseX, mouseY, x + width - 168, rowY + 9, x + width - 94, rowY + 39)) {
                openExport(config);
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !config.permanent()
                    && hovered(mouseX, mouseY, x + width - 84, rowY + 9, x + width - 30, rowY + 39)) {
                pendingDelete = config.id();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) requestSwitch(config.id(), null);
            return true;
        }

        int footerY = y + height - 57;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hovered(mouseX, mouseY, x + 18, footerY, x + 80, footerY + 36)) {
            parent.closeConfigsPanel();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && hovered(mouseX, mouseY, x + 90, footerY, x + 188, footerY + 36)) {
            parent.openNewConfig(null);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && hovered(mouseX, mouseY, x + 198, footerY, x + 284, footerY + 36)) {
            chooseImport();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && manager().isDirty()
                && hovered(mouseX, mouseY, x + 294, footerY, x + 394, footerY + 36)) {
            setStatus(manager().saveActiveNow());
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!contains(mouseX, mouseY)) return false;
        if (pendingSwitch == null && pendingDelete == null) {
            scroll = Math.max(0, scroll + (amount < 0 ? 1 : amount > 0 ? -1 : 0));
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return pendingSwitch != null || pendingDelete != null;
        if (pendingSwitch != null || pendingDelete != null) {
            pendingSwitch = null;
            pendingDelete = null;
        } else parent.closeConfigsPanel();
        return true;
    }

    public void clearFocus() {
    }

    public boolean isInputFocused() {
        return false;
    }

    public boolean contains(double mouseX, double mouseY) {
        return hovered(mouseX, mouseY, x, y, x + width, y + height);
    }

    private boolean handleSwitchConfirm(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        int boxWidth = Math.min(380, width - 50);
        int boxX = x + (width - boxWidth) / 2;
        int buttonY = y + (height - 150) / 2 + 94;
        if (hovered(mouseX, mouseY, boxX + 16, buttonY, boxX + 116, buttonY + 36)) {
            requestSwitch(pendingSwitch, UnsavedAction.SAVE);
        } else if (hovered(mouseX, mouseY, boxX + 126, buttonY, boxX + 226, buttonY + 36)) {
            requestSwitch(pendingSwitch, UnsavedAction.DISCARD);
        } else if (hovered(mouseX, mouseY, boxX + 236, buttonY, boxX + boxWidth - 16, buttonY + 36)) {
            pendingSwitch = null;
        }
        return true;
    }

    private boolean handleDeleteConfirm(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        int boxWidth = Math.min(380, width - 50);
        int boxX = x + (width - boxWidth) / 2;
        int buttonY = y + (height - 150) / 2 + 94;
        if (hovered(mouseX, mouseY, boxX + 70, buttonY, boxX + 180, buttonY + 36)) {
            setStatus(manager().delete(pendingDelete));
            pendingDelete = null;
        } else if (hovered(mouseX, mouseY, boxX + 194, buttonY, boxX + 304, buttonY + 36)) {
            pendingDelete = null;
        }
        return true;
    }

    private void requestSwitch(String id, UnsavedAction action) {
        SwitchResult result = manager().switchTo(id, action);
        if (result.needsConfirmation()) pendingSwitch = id;
        else {
            pendingSwitch = null;
            status = result.switched() ? "Config loaded" : result.message();
        }
    }

    private void chooseImport() {
        status = "Waiting for file selection...";
        NativeFileDialogs.openConfig(manager().directory(), path -> {
            if (path == null) {
                status = "Import cancelled";
                return;
            }
            var result = manager().readImport(path);
            if (result.ok()) parent.openNewConfig(result.value());
            else status = result.message();
        });
    }

    private void openExport(ConfigSummary config) {
        status = "Waiting for export location...";
        NativeFileDialogs.saveConfig(manager().suggestedExportPath(config.id()), path -> {
            if (path == null) status = "Export cancelled";
            else setStatus(manager().export(config.id(), path));
        });
    }

    private String activeLabel() {
        ConfigSummary active = manager().active();
        return active == null ? "" : "Active: " + active.name();
    }

    private void setStatus(ConfigManager.Result<?> result) {
        status = result.ok() ? "Saved" : result.message();
    }

    private ConfigManager manager() {
        return Argon.INSTANCE.getConfigManager();
    }

    private static void button(GuiGraphicsExtractor context, int mouseX, int mouseY,
            int x, int y, int width, String label, boolean ignored) {
        boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 36);
        GhostorTheme.panel(context, x, y, x + width, y + 36,
                over ? GhostorTheme.ACCENT_HOVER : GhostorTheme.ACCENT, 7);
        TextRenderer.drawCenteredString(label, context, x + width / 2, y + 12, GhostorTheme.TEXT.getRGB());
    }

    private static void drawSecondaryButton(GuiGraphicsExtractor context,
            int x1, int y1, int x2, int y2, String label, boolean hovered) {
        GhostorTheme.panel(context, x1, y1, x2, y2,
                hovered ? GhostorTheme.SURFACE_HOVER : GhostorTheme.ACCENT_SOFT, 7);
        GhostorTheme.outline(context, x1, y1, x2, y2,
                hovered ? GhostorTheme.ACCENT : GhostorTheme.ACCENT_BORDER, 7);
        TextRenderer.drawCenteredString(label, context, (x1 + x2) / 2, y1 + 13, GhostorTheme.TEXT.getRGB());
    }

    private static void secondary(GuiGraphicsExtractor context, int mouseX, int mouseY,
            int x, int y, int width, String label) {
        boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 30);
        GhostorTheme.panel(context, x, y, x + width, y + 30,
                over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.ACCENT_SOFT, 6);
        GhostorTheme.outline(context, x, y, x + width, y + 30, GhostorTheme.ACCENT_BORDER, 6);
        TextRenderer.drawCenteredString(label, context, x + width / 2, y + 9, GhostorTheme.TEXT.getRGB());
    }

    private static void danger(GuiGraphicsExtractor context, int mouseX, int mouseY,
            int x, int y, int width, String label) {
        boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 30);
        GhostorTheme.panel(context, x, y, x + width, y + 30,
                over ? GhostorTheme.DANGER_HOVER : GhostorTheme.DANGER, 6);
        TextRenderer.drawCenteredString(label, context, x + width / 2, y + 9, GhostorTheme.TEXT.getRGB());
    }

    private static boolean hovered(double mouseX, double mouseY, int x1, int y1, int x2, int y2) {
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    private static String fit(String value, int maxWidth) {
        if (TextRenderer.getSmallWidth(value) <= maxWidth) return value;
        String result = value;
        while (!result.isEmpty() && TextRenderer.getSmallWidth(result + "…") > maxWidth) {
            result = result.substring(0, result.offsetByCodePoints(result.length(), -1));
        }
        return result + "…";
    }
}
