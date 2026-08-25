package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.ActionSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.TextRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

public final class ActionButton extends RenderableSetting {
    private final ActionSetting action;

    public ActionButton(ModuleButton parent, Setting<?> setting, int offset) {
        super(parent, setting, offset);
        this.action = (ActionSetting) setting;
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int x1 = parentX() + 20;
        int y1 = parentY() + parentOffset() + offset + 6;
        int x2 = parentX() + parentWidth() - 20;
        int y2 = parentY() + parentOffset() + offset + parentHeight() - 6;
        GhostorTheme.panel(context, x1, y1, x2, y2,
                isHovered(mouseX, mouseY) ? GhostorTheme.ACCENT_HOVER : GhostorTheme.ACCENT, 5);
        TextRenderer.drawCenteredString(action.getName(), context, (x1 + x2) / 2, y1 + 6, GhostorTheme.TEXT.getRGB());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isHovered(mouseX, mouseY)) {
            action.run();
        }
    }
}
