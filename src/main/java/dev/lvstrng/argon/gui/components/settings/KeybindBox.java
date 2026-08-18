package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.ColorUtils;
import dev.lvstrng.argon.utils.KeyUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;


public final class KeybindBox extends RenderableSetting {
	public KeybindSetting keybind;
	private Color currentAlpha;

	public KeybindBox(ModuleButton parent, Setting<?> setting, int offset) {
		super(parent, setting, offset);
		this.keybind = (KeybindSetting) setting;
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (isHovered(mouseX, mouseY)) {
			if(!keybind.isListening()) {
				if(button == 0) {
					keybind.toggleListening();
					keybind.setListening(true);
				}
			} else {
				if(keybind.isModuleKey()) parent.module.setKey(button);

				keybind.setKey(button);
				keybind.setListening(false);
			}
		}
		super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		if(keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			if(mouseOver) {
				if (keybind.isModuleKey())
					parent.module.setKey(keybind.getOriginalKey());

				keybind.setKey(keybind.getOriginalKey());
				keybind.setListening(false);
			}
		} else {
			if (keybind.isListening() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
				if (keybind.isModuleKey()) parent.module.setKey(keyCode);

				keybind.setKey(keyCode);
				keybind.setListening(false);
			}

			if (keybind.getKey() == GLFW.GLFW_KEY_ESCAPE) {
				if (keybind.isModuleKey()) parent.module.setKey(parent.module.getKey());

				keybind.setKey(keybind.getKey());
				keybind.setListening(false);
			}
		}
		super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int off = parentX() + 20;
		TextRenderer.drawString(setting.getName(), context, off, (parentY() + parentOffset() + offset) + 9, GhostorTheme.TEXT.getRGB());
		String value = keybind.isListening() ? "Press a key" : KeyUtils.getKey(keybind.getKey()).toString();
		int valueX = parentX() + parentWidth() - 20 - TextRenderer.getWidth(value);
		GhostorTheme.panel(context, valueX - 7, parentY() + parentOffset() + offset + 6, parentX() + parentWidth() - 13, parentY() + parentOffset() + offset + parentHeight() - 6, keybind.isListening() ? GhostorTheme.ACCENT : GhostorTheme.SURFACE_HOVER, 4);
		TextRenderer.drawString(value, context, valueX, (parentY() + parentOffset() + offset) + 9, (keybind.isListening() ? GhostorTheme.TEXT : GhostorTheme.TEXT_MUTED).getRGB());

		if (!parent.parent.dragging) {
			int toHoverAlpha = isHovered(mouseX, mouseY) ? 15 : 0;

			if (currentAlpha == null)
				currentAlpha = new Color(255, 255, 255, toHoverAlpha);
			else currentAlpha = new Color(255, 255, 255, currentAlpha.getAlpha());

			if (currentAlpha.getAlpha() != toHoverAlpha)
				currentAlpha = ColorUtils.smoothAlphaTransition(0.05F, toHoverAlpha, currentAlpha);

			context.fill(parentX(), parentY() + parentOffset() + offset, parentX() + parentWidth(), parentY() + parentOffset() + offset + parentHeight(), currentAlpha.getRGB());
		}
	}
}
