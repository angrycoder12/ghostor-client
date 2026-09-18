package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.*;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public final class Slider extends RenderableSetting {
	private static final double TRACK_INSET = 20.0D;
	public boolean dragging;

	private final NumberSetting setting;

	public Color currentColor1;
	public Color currentColor2;
	private Color currentAlpha;

    public Slider(ModuleButton parent, Setting<?> setting, int offset) {
		super(parent, setting, offset);
		this.setting = (NumberSetting) setting;
	}

	@Override
	public void onUpdate() {
        Color clr = Utils.getMainColor(0, parent.settings.indexOf(this)).darker();
        Color clr2 = Utils.getMainColor(0, parent.settings.indexOf(this) + 1).darker();

		if (currentColor1 == null)
			currentColor1 = new Color(clr.getRed(), clr.getGreen(), clr.getBlue(), 0);
		else currentColor1 = new Color(clr.getRed(), clr.getGreen(), clr.getBlue(), currentColor1.getAlpha());

		if (currentColor2 == null)
			currentColor2 = new Color(clr2.getRed(), clr2.getGreen(), clr2.getBlue(), 0);
		else currentColor2 = new Color(clr2.getRed(), clr2.getGreen(), clr2.getBlue(), currentColor2.getAlpha());

		int toAlpha = 255;

		if (currentColor1.getAlpha() != toAlpha)
			currentColor1 = ColorUtils.smoothAlphaTransition(0.05F, toAlpha, currentColor1);

		if (currentColor2.getAlpha() != toAlpha)
			currentColor2 = ColorUtils.smoothAlphaTransition(0.05F, toAlpha, currentColor2);

		super.onUpdate();
	}

	@Override
	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int trackY = parentY() + offset + parentOffset() + parentHeight() - 11;
		double start = trackStart();
		double end = trackEnd();
		double handleX = Mth.lerp(normalizedValue(), start, end);
		context.fill((int) Math.round(start), trackY, (int) Math.round(end), trackY + 3, GhostorTheme.DISABLED.getRGB());
		context.fillGradient((int) Math.round(start), trackY, (int) Math.round(handleX), trackY + 3,
				GhostorTheme.ACCENT.getRGB(), GhostorTheme.ACCENT_HOVER.getRGB());
		Color handleColor = currentColor1 == null ? GhostorTheme.ACCENT_HOVER : currentColor1.brighter();
		RenderUtils.renderCircle(context, new Color(0, 0, 0, 170), handleX, trackY + 1.5D, 6, 16);
		RenderUtils.renderCircle(context, handleColor, handleX, trackY + 1.5D, 5, 16);

		TextRenderer.drawString(setting.getName(), context, parentX() + 20, (parentY() + parentOffset() + offset) + 8, GhostorTheme.TEXT.getRGB());
		String value = String.valueOf(setting.getValue());
		TextRenderer.drawString(value, context, parentX() + parentWidth() - 20 - TextRenderer.getWidth(value), (parentY() + parentOffset() + offset) + 8, GhostorTheme.TEXT_MUTED.getRGB());

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

	@Override
	public void onGuiClose() {
		this.currentColor1 = null;
		this.currentColor2 = null;
		super.onGuiClose();
	}

	private void slide(double mouseX) {
		double start = trackStart();
		double b = Mth.clamp((mouseX - start) / Math.max(1.0D, trackEnd() - start), 0.0D, 1.0D);
		setting.setValue(MathUtils.roundToDecimal(b * (setting.getMax() - setting.getMin()) + setting.getMin(), setting.getIncrement()));
	}

	private double normalizedValue() {
		double range = setting.getMax() - setting.getMin();
		return range <= 0.0D ? 0.0D : Mth.clamp((setting.getValue() - setting.getMin()) / range, 0.0D, 1.0D);
	}

	private double trackStart() {
		return parentX() + TRACK_INSET;
	}

	private double trackEnd() {
		return Math.max(trackStart(), parentX() + parentWidth() - TRACK_INSET);
	}

	@Override
	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		if(mouseOver && parent.extended) {
			if(keyCode == GLFW.GLFW_KEY_BACKSPACE)
				setting.setValue(setting.getOriginalValue());
		}
		super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (isHovered(mouseX, mouseY) && button == 0) {
			dragging = true;
			slide(mouseX);
		}
		super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging && button == 0)
			dragging = false;

		super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging)
			slide(mouseX);

		super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}
}
