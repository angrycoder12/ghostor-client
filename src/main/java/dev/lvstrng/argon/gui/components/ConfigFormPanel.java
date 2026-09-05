package dev.lvstrng.argon.gui.components;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.config.ConfigManager;
import dev.lvstrng.argon.config.ConfigManager.ConfigSummary;
import dev.lvstrng.argon.config.ConfigManager.CreationBasis;
import dev.lvstrng.argon.config.ConfigManager.ImportedConfig;
import dev.lvstrng.argon.config.NativeFileDialogs;
import dev.lvstrng.argon.gui.ClickGui;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import java.awt.Color;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Separate New/Edit Config window with native import and explicit overwrite confirmation. */
public final class ConfigFormPanel {
    public static final int PREFERRED_WIDTH = 440;
    public static final int PREFERRED_HEIGHT = 410;
    private final ClickGui parent;
    private int x;
    private int y;
    private int width = PREFERRED_WIDTH;
    private int height = PREFERRED_HEIGHT;
    private boolean edit;
    private String editingId;
    private String name = "";
    private boolean autoSave;
    private boolean inputFocused;
    private CreationBasis basis = CreationBasis.SCRATCH;
    private ImportedConfig imported;
    private String status = "";
    private boolean confirmOverwrite;

    public ConfigFormPanel(ClickGui parent) {
        this.parent = parent;
    }

    public void beginNew(ImportedConfig imported) {
        edit = false;
        editingId = null;
        this.imported = imported;
        ConfigSummary active = manager().active();
        basis = imported != null ? CreationBasis.IMPORTED
                : active != null && active.permanent() ? CreationBasis.CURRENT : CreationBasis.SCRATCH;
        name = imported == null ? "" : imported.suggestedName();
        autoSave = imported != null && imported.autoSave();
        inputFocused = true;
        confirmOverwrite = false;
        status = "";
    }

    public boolean beginEdit(String id) {
        ConfigSummary selected = manager().summaries().stream().filter(summary -> summary.id().equals(id)).findFirst().orElse(null);
        if (selected == null || selected.permanent()) return false;
        edit = true;
        editingId = id;
        name = selected.name();
        autoSave = selected.autoSave();
        imported = null;
        inputFocused = false;
        confirmOverwrite = false;
        status = "";
        return true;
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
        TextRenderer.drawString(edit ? "Edit Config" : "New Config", context, x + 20, y + 25, GhostorTheme.TEXT.getRGB());
        TextRenderer.drawSmallString(edit ? "Rename, change saving, or replace its contents"
                : "Create from your current setup, defaults, or a file", context,
                x + 20, y + 48, GhostorTheme.TEXT_MUTED.getRGB());

        int inputY = y + 82;
        GhostorTheme.panel(context, x + 20, inputY, x + width - 20, inputY + 40,
                inputFocused ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 7);
        GhostorTheme.outline(context, x + 20, inputY, x + width - 20, inputY + 40,
                inputFocused ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 7);
        TextRenderer.drawSmallString(name.isEmpty() ? "Config name..." : fit(name, width - 62), context,
                x + 31, inputY + 16, (name.isEmpty() ? GhostorTheme.DISABLED : GhostorTheme.TEXT).getRGB());

        int autoY = inputY + 58;
        TextRenderer.drawString("Auto Save", context, x + 22, autoY + 9, GhostorTheme.TEXT.getRGB());
        renderToggle(context, x + width - 58, autoY + 4, autoSave);

        if (edit) renderEdit(context, mouseX, mouseY, autoY + 54);
        else renderNew(context, mouseX, mouseY, autoY + 54);

        int footerY = y + height - 54;
        secondary(context, mouseX, mouseY, x + 20, footerY, 62, "Back");
        button(context, mouseX, mouseY, x + width - 110, footerY, 90, edit ? "Apply" : "Create");
        if (!status.isBlank()) TextRenderer.drawSmallString(fit(status, width - 230), context,
                x + 112, footerY + 14, status.startsWith("Saved") || status.startsWith("Created")
                        ? GhostorTheme.SUCCESS.getRGB() : GhostorTheme.DANGER_HOVER.getRGB());
        if (confirmOverwrite) renderConfirm(context, mouseX, mouseY);
    }

    private void renderNew(GuiGraphicsExtractor context, int mouseX, int mouseY, int top) {
        ConfigSummary active = manager().active();
        boolean allowCurrent = active != null && active.permanent();
        TextRenderer.drawSmallString("Starting point", context, x + 20, top, GhostorTheme.TEXT_MUTED.getRGB());
        int choiceCount = allowCurrent ? 3 : 2;
        int gap = 8;
        int choiceWidth = (width - 40 - gap * (choiceCount - 1)) / choiceCount;
        int choiceX = x + 20;
        if (allowCurrent) {
            choice(context, mouseX, mouseY, choiceX, top + 24, choiceWidth,
                    "Use Current", basis == CreationBasis.CURRENT);
            choiceX += choiceWidth + gap;
        }
        choice(context, mouseX, mouseY, choiceX, top + 24, choiceWidth,
                "Start From Scratch", basis == CreationBasis.SCRATCH);
        choiceX += choiceWidth + gap;
        choice(context, mouseX, mouseY, choiceX, top + 24, choiceWidth,
                "Upload", basis == CreationBasis.IMPORTED);
        String basisText = switch (basis) {
            case CURRENT -> "Copies every setting from the current none setup.";
            case SCRATCH -> "Uses each module's real built-in defaults.";
            case IMPORTED -> imported == null ? "Choose a .ghostorconfig file." : "Ready: " + imported.suggestedName();
        };
        TextRenderer.drawSmallString(fit(basisText, width - 40), context, x + 20, top + 78,
                GhostorTheme.TEXT_MUTED.getRGB());
    }

    private void renderEdit(GuiGraphicsExtractor context, int mouseX, int mouseY, int top) {
        TextRenderer.drawSmallString("Saved contents", context, x + 20, top, GhostorTheme.TEXT_MUTED.getRGB());
        button(context, mouseX, mouseY, x + 20, top + 24, Math.min(210, width - 40), "Save Current to This Config");
        TextRenderer.drawSmallString("This replaces the selected config only after confirmation.", context,
                x + 20, top + 70, GhostorTheme.TEXT_MUTED.getRGB());
    }

    private void renderConfirm(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int boxWidth = Math.min(350, width - 44);
        int boxX = x + (width - boxWidth) / 2;
        int boxY = y + (height - 142) / 2;
        GhostorTheme.panel(context, x, y, x + width, y + height, GhostorTheme.BACKDROP, GhostorTheme.RADIUS);
        GhostorTheme.panel(context, boxX, boxY, boxX + boxWidth, boxY + 142, GhostorTheme.SURFACE, 10);
        GhostorTheme.outline(context, boxX, boxY, boxX + boxWidth, boxY + 142, GhostorTheme.ACCENT_BORDER, 10);
        TextRenderer.drawString("Replace saved config?", context, boxX + 18, boxY + 22, GhostorTheme.TEXT.getRGB());
        TextRenderer.drawSmallString("Current live settings will replace its saved contents.", context,
                boxX + 18, boxY + 48, GhostorTheme.TEXT_MUTED.getRGB());
        danger(context, mouseX, mouseY, boxX + 54, boxY + 90, 105, "Replace");
        secondary(context, mouseX, mouseY, boxX + 176, boxY + 90, 105, "Cancel");
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            inputFocused = false;
            return false;
        }
        if (confirmOverwrite) return handleConfirm(mouseX, mouseY, button);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        int inputY = y + 82;
        if (hovered(mouseX, mouseY, x + 20, inputY, x + width - 20, inputY + 40)) {
            inputFocused = true;
            return true;
        }
        inputFocused = false;
        int autoY = inputY + 58;
        if (hovered(mouseX, mouseY, x + width - 68, autoY, x + width - 18, autoY + 34)) {
            autoSave = !autoSave;
            return true;
        }
        int top = autoY + 54;
        if (edit) {
            if (hovered(mouseX, mouseY, x + 20, top + 24, x + Math.min(230, width - 20), top + 60)) {
                confirmOverwrite = true;
                return true;
            }
        } else {
            ConfigSummary active = manager().active();
            boolean allowCurrent = active != null && active.permanent();
            int choiceCount = allowCurrent ? 3 : 2;
            int gap = 8;
            int choiceWidth = (width - 40 - gap * (choiceCount - 1)) / choiceCount;
            int choiceX = x + 20;
            if (allowCurrent && hovered(mouseX, mouseY, choiceX, top + 24,
                    choiceX + choiceWidth, top + 60)) {
                basis = CreationBasis.CURRENT;
            } else {
                if (allowCurrent) choiceX += choiceWidth + gap;
                if (hovered(mouseX, mouseY, choiceX, top + 24, choiceX + choiceWidth, top + 60)) {
                basis = CreationBasis.SCRATCH;
                } else {
                    choiceX += choiceWidth + gap;
                    if (hovered(mouseX, mouseY, choiceX, top + 24,
                            choiceX + choiceWidth, top + 60)) chooseImport();
                }
            }
        }

        int footerY = y + height - 54;
        if (hovered(mouseX, mouseY, x + 20, footerY, x + 82, footerY + 36)) {
            parent.closeConfigForm();
        } else if (hovered(mouseX, mouseY, x + width - 110, footerY, x + width - 20, footerY + 36)) {
            apply();
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (confirmOverwrite) confirmOverwrite = false;
            else parent.closeConfigForm();
            return true;
        }
        if (!inputFocused) return false;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !name.isEmpty()) {
            name = name.substring(0, name.offsetByCodePoints(name.length(), -1));
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            apply();
        }
        return true;
    }

    public boolean charTyped(String characters) {
        if (!inputFocused || name.length() >= 48) return false;
        name += characters;
        return true;
    }

    public void clearFocus() {
        inputFocused = false;
    }

    public boolean isInputFocused() {
        return inputFocused;
    }

    public boolean contains(double mouseX, double mouseY) {
        return hovered(mouseX, mouseY, x, y, x + width, y + height);
    }

    private boolean handleConfirm(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        int boxWidth = Math.min(350, width - 44);
        int boxX = x + (width - boxWidth) / 2;
        int boxY = y + (height - 142) / 2;
        if (hovered(mouseX, mouseY, boxX + 54, boxY + 90, boxX + 159, boxY + 126)) {
            var result = manager().saveCurrentTo(editingId);
            status = result.ok() ? "Saved current setup" : result.message();
            confirmOverwrite = false;
        } else if (hovered(mouseX, mouseY, boxX + 176, boxY + 90, boxX + 281, boxY + 126)) {
            confirmOverwrite = false;
        }
        return true;
    }

    private void apply() {
        if (edit) {
            var result = manager().edit(editingId, name, autoSave);
            status = result.ok() ? "Saved config details" : result.message();
            if (result.ok()) parent.closeConfigForm();
        } else {
            var created = manager().create(name, autoSave, basis, imported);
            status = created.ok() ? "Created " + created.value().name() : created.message();
            if (created.ok()) parent.closeConfigForm();
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
            if (result.ok()) {
                imported = result.value();
                basis = CreationBasis.IMPORTED;
                if (name.isBlank()) name = imported.suggestedName();
                status = "File validated";
            } else status = result.message();
        });
    }

    private ConfigManager manager() {
        return Argon.INSTANCE.getConfigManager();
    }

    private static void renderToggle(GuiGraphicsExtractor context, int x, int y, boolean enabled) {
        GhostorTheme.panel(context, x, y, x + 38, y + 22,
                enabled ? GhostorTheme.ACCENT : GhostorTheme.DISABLED_TOGGLE, 11);
        RenderUtils.renderCircle(context, Color.WHITE, x + (enabled ? 27 : 11), y + 11, 7, 16);
    }

    private static void choice(GuiGraphicsExtractor context, int mouseX, int mouseY,
            int x, int y, int width, String label, boolean selected) {
        boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 36);
        GhostorTheme.panel(context, x, y, x + width, y + 36,
                selected ? GhostorTheme.ACCENT : over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 7);
        GhostorTheme.outline(context, x, y, x + width, y + 36,
                selected ? GhostorTheme.ACCENT_HOVER : GhostorTheme.BORDER, 7);
        drawFittedButtonLabel(context, x, y, width, label);
    }

    private static void button(GuiGraphicsExtractor context, int mouseX, int mouseY,
            int x, int y, int width, String label) {
        boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 36);
        GhostorTheme.panel(context, x, y, x + width, y + 36,
                over ? GhostorTheme.ACCENT_HOVER : GhostorTheme.ACCENT, 7);
        drawFittedButtonLabel(context, x, y, width, label);
    }

    private static void secondary(GuiGraphicsExtractor context, int mouseX, int mouseY,
            int x, int y, int width, String label) {
        boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 36);
        GhostorTheme.panel(context, x, y, x + width, y + 36,
                over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.ACCENT_SOFT, 7);
        GhostorTheme.outline(context, x, y, x + width, y + 36,
                over ? GhostorTheme.ACCENT : GhostorTheme.ACCENT_BORDER, 7);
        TextRenderer.drawCenteredString(label, context, x + width / 2, y + 13, GhostorTheme.TEXT.getRGB());
    }

    private static void danger(GuiGraphicsExtractor context, int mouseX, int mouseY,
            int x, int y, int width, String label) {
        boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 36);
        GhostorTheme.panel(context, x, y, x + width, y + 36,
                over ? GhostorTheme.DANGER_HOVER : GhostorTheme.DANGER, 7);
        TextRenderer.drawCenteredString(label, context, x + width / 2, y + 12, GhostorTheme.TEXT.getRGB());
    }

    private static void drawFittedButtonLabel(GuiGraphicsExtractor context,
            int x, int y, int width, String label) {
        if (TextRenderer.getWidth(label) <= width - 12) {
            TextRenderer.drawCenteredString(label, context, x + width / 2, y + 12, GhostorTheme.TEXT.getRGB());
            return;
        }
        String fitted = fit(label, width - 10);
        TextRenderer.drawSmallString(fitted, context,
                x + (width - TextRenderer.getSmallWidth(fitted)) / 2, y + 14, GhostorTheme.TEXT.getRGB());
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
