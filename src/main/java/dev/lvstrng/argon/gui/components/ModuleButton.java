package dev.lvstrng.argon.gui.components;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.Window;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.gui.components.settings.*;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.setting.*;
import dev.lvstrng.argon.utils.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static dev.lvstrng.argon.Argon.mc;

public final class ModuleButton {
	public List<RenderableSetting> settings = new ArrayList<>();
	public Window parent;
	public Module module;
	public int offset;
	public boolean extended;
	public int settingOffset;
	public Color currentColor;
	public Color defaultColor = Color.WHITE;
	public Color currentAlpha;
	public AnimationUtils animation = new AnimationUtils(0);
	private final AnimationUtils toggleAnimation;

	public ModuleButton(Window parent, Module module, int offset) {
		this.parent = parent;
		this.module = module;
		this.offset = offset;
		this.extended = false;
		this.toggleAnimation = new AnimationUtils(module.isEnabled() ? 1 : 0);

		settingOffset = parent.getHeight();
		for (Setting<?> setting : module.getSettings()) {
			if (setting instanceof BooleanSetting booleanSetting)
				settings.add(new CheckBox(this, booleanSetting, settingOffset));
			else if (setting instanceof NumberSetting numberSetting)
				settings.add(new Slider(this, numberSetting, settingOffset));
			else if (setting instanceof ModeSetting<?> modeSetting)
				settings.add(new ModeBox(this, modeSetting, settingOffset));
			else if (setting instanceof KeybindSetting keybindSetting)
				settings.add(new KeybindBox(this, keybindSetting, settingOffset));
			else if (setting instanceof StringSetting stringSetting)
				settings.add(new StringBox(this, stringSetting, settingOffset));
			else if (setting instanceof MinMaxSetting minMaxSetting)
				settings.add(new MinMaxSlider(this, minMaxSetting, settingOffset));
			else if (setting instanceof ActionSetting actionSetting)
				settings.add(new ActionButton(this, actionSetting, settingOffset));
			else if (setting instanceof ColorSetting colorSetting)
				settings.add(new ColorPicker(this, colorSetting, settingOffset));

			settingOffset += parent.getHeight();
		}
	}

	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		if (parent.getY() + offset > Minecraft.getInstance().getWindow().getHeight())
			return;

		refreshSettingLayout();
		for (RenderableSetting renderableSetting : settings)
			if (renderableSetting.setting.isVisible()) renderableSetting.onUpdate();

		if (currentColor == null)
			currentColor = new Color(0, 0, 0, 0);
		else currentColor = new Color(0, 0, 0, currentColor.getAlpha());

		int toAlpha = 255;

		currentColor = ColorUtils.smoothAlphaTransition(0.05F, toAlpha, currentColor);

		Color toColor = module.isEnabled() ? GhostorTheme.TEXT : GhostorTheme.TEXT_MUTED;

		if (defaultColor != toColor)
			defaultColor = ColorUtils.smoothColorTransition(0.1F, toColor, defaultColor);

		Color card = module.isEnabled() ? GhostorTheme.ACTIVE_SURFACE : GhostorTheme.SURFACE_ELEVATED;
		if (isHovered(mouseX, mouseY)) card = module.isEnabled()
				? GhostorTheme.ACTIVE_SURFACE.brighter() : GhostorTheme.SURFACE_HOVER;
		GhostorTheme.panel(context, parent.getX() + 8, parent.getY() + offset, parent.getX() + parent.getWidth() - 8,
				parent.getY() + parent.getHeight() + offset - 3, card, 7);
		if (module.isEnabled()) context.fill(parent.getX() + 8, parent.getY() + offset + 7, parent.getX() + 11,
				parent.getY() + parent.getHeight() + offset - 10, GhostorTheme.ACCENT.getRGB());
		GhostorTheme.outline(context, parent.getX() + 8, parent.getY() + offset, parent.getX() + parent.getWidth() - 8,
				parent.getY() + parent.getHeight() + offset - 3, module.isEnabled() ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 7);

		CharSequence nameChars = module.getName();
		GhostorIcons.moduleBadge(context, module.getCategory(), parent.getX() + 17, parent.getY() + offset + 9);
		TextRenderer.drawString(nameChars, context, parent.getX() + 52, parent.getY() + offset + 7, defaultColor.getRGB());
		if (module.getDescription() != null && parent.getHeight() >= 42) {
			TextRenderer.drawSmallString(module.getDescription(), context, parent.getX() + 52, parent.getY() + offset + 25, GhostorTheme.TEXT_MUTED.getRGB());
		}
		int toggleX = parent.getX() + parent.getWidth() - 52;
		int toggleY = parent.getY() + offset + 12;
		double toggleProgress = toggleAnimation.animate(0.3 * delta, module.isEnabled() ? 1 : 0);
		GhostorTheme.panel(context, toggleX, toggleY, toggleX + 34, toggleY + 18,
				module.isEnabled() ? GhostorTheme.ACCENT : GhostorTheme.DISABLED_TOGGLE, 9);
		RenderUtils.renderCircle(context, Color.WHITE, toggleX + 9 + toggleProgress * 16, toggleY + 9, 6, 16);

		renderHover(context, mouseX, mouseY, delta);
		renderSettings(context, mouseX, mouseY, delta);

		for(RenderableSetting renderableSetting : settings)
			if(extended && renderableSetting.setting.isVisible()) renderableSetting.renderDescription(context, mouseX, mouseY, delta);

	}

	private void renderHover(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		if (!parent.dragging) {
			int toHoverAlpha = isHovered(mouseX, mouseY) ? 8 : 0;

			if (currentAlpha == null)
				currentAlpha = new Color(255, 255, 255, toHoverAlpha);
			else currentAlpha = new Color(255, 255, 255, currentAlpha.getAlpha());

			if (currentAlpha.getAlpha() != toHoverAlpha)
				currentAlpha = ColorUtils.smoothAlphaTransition(0.05F, toHoverAlpha, currentAlpha);

			GhostorTheme.panel(context, parent.getX() + 8, parent.getY() + offset,
					parent.getX() + parent.getWidth() - 8, parent.getY() + parent.getHeight() + offset - 3,
					currentAlpha, 7);
		}
	}

	private void renderSettings(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		int scissorX1 = parent.getX();
		int scissorY1 = parent.getY() + offset;
		int scissorX2 = scissorX1 + parent.getWidth();
		int scissorY2 = scissorY1 + (int) animation.getValue();

		context.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);

		for (RenderableSetting renderableSetting : settings)
			if(renderableSetting.setting.isVisible() && animation.getValue() > parent.getHeight())
				renderableSetting.render(context, mouseX, mouseY, delta);

		for (RenderableSetting renderableSetting : settings) {
			if(renderableSetting.setting.isVisible() && animation.getValue() > parent.getHeight()) {
				if (renderableSetting instanceof Slider slider) {
					RenderUtils.renderCircle(context, new Color(0, 0, 0, 170), (slider.parentX() + (Math.max(slider.lerpedOffsetX, 2.5))), slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
					RenderUtils.renderCircle(context, slider.currentColor1.brighter(), (slider.parentX() + (Math.max(slider.lerpedOffsetX, 2.5))) , slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);

				} else if (renderableSetting instanceof MinMaxSlider slider) {
					RenderUtils.renderCircle(context, new Color(0, 0, 0, 170), (slider.parentX() + (Math.max(slider.lerpedOffsetMinX, 2.5))), slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
					RenderUtils.renderCircle(context, slider.currentColor1.brighter(), (slider.parentX() + (Math.max(slider.lerpedOffsetMinX, 2.5))), slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);

					RenderUtils.renderCircle(context, new Color(0, 0, 0, 170), (slider.parentX() + (Math.max(slider.lerpedOffsetMaxX, 2.5))), slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
					RenderUtils.renderCircle(context, slider.currentColor1.brighter(), (slider.parentX() + (Math.max(slider.lerpedOffsetMaxX, 2.5))), slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);
				}
			}
		}

		context.disableScissor();
	}

	public void onExtend() {
		for(ModuleButton moduleButton : parent.moduleButtons) {
			moduleButton.extended = false;
		}
	}

	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		for (RenderableSetting setting : settings)
			if (setting.setting.isVisible()) setting.keyPressed(keyCode, scanCode, modifiers);
	}

	public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (extended)
			for (RenderableSetting renderableSetting : settings)
				if (renderableSetting.setting.isVisible()) renderableSetting.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (isHovered(mouseX, mouseY)) {
			if (button == 0)
				module.toggle();

			if (button == 1) {
				if (module.getSettings().isEmpty()) return;
				if (!extended)
					onExtend();

				extended = !extended;
			}
		}
		if (extended) {
			for (RenderableSetting renderableSetting : settings) {
				if (renderableSetting.setting.isVisible()) renderableSetting.mouseClicked(mouseX, mouseY, button);
			}
		}
	}

	public void onGuiClose() {
		this.currentAlpha = null;
		this.currentColor = null;

		for (RenderableSetting renderableSetting : settings)
			renderableSetting.onGuiClose();
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
		for (RenderableSetting renderableSetting : settings)
			renderableSetting.mouseReleased(mouseX, mouseY, button);
	}

	public boolean isHovered(double mouseX, double mouseY) {
		return mouseX > parent.getX()
				&& mouseX < parent.getX() + parent.getWidth()
				&& mouseY > parent.getY() + offset
				&& mouseY < parent.getY() + offset + parent.getHeight();
	}

	public int visibleSettingsCount() {
		return (int) settings.stream().filter(setting -> setting.setting.isVisible()).count();
	}

	private void refreshSettingLayout() {
		int nextOffset = parent.getHeight();
		for (RenderableSetting renderableSetting : settings) {
			if (!renderableSetting.setting.isVisible()) {
				// A conditional text/key input must not remain focused after its row
				// disappears, otherwise normal module keybinds stay blocked invisibly.
				renderableSetting.onGuiClose();
				continue;
			}
			renderableSetting.offset = nextOffset;
			nextOffset += parent.getHeight();
		}
	}
}
