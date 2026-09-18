package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.ColorUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import java.awt.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class RenderableSetting {
	public Minecraft mc = Minecraft.getInstance();
	public ModuleButton parent;
	public Setting<?> setting;
	public int offset;
	public Color currentColor;
	public boolean mouseOver;
	int x;
	int y;
	int width;
	int height;

	public RenderableSetting(ModuleButton parent, Setting<?> setting, int offset) {
		this.parent = parent;
		this.setting = setting;
		this.offset = offset;

		this.x = parentX();
		this.y = parentY() + parentOffset() + offset;
		this.width = parentX() + parentWidth();
		this.height = parentY() + parentOffset() + offset + parentHeight();
	}

	public int parentX() {
		return parent.parent.getX();
	}

	public int parentY() {
		return parent.parent.getY();
	}

	public int parentWidth() {
		return parent.parent.getWidth();
	}

	public int parentHeight() {
		return parent.parent.getHeight();
	}

	public int parentOffset() {
		return parent.offset;
	}

	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		updateMouseOver(mouseX, mouseY);
		this.x = parentX();
		this.y = parentY() + parentOffset() + offset;
		this.width = parentX() + parentWidth();
		this.height = parentY() + parentOffset() + offset + parentHeight();

		context.fill(x + 12, y, width - 12, height, GhostorTheme.SURFACE.getRGB());
	}

	private void updateMouseOver(double mouseX, double mouseY) {
		this.mouseOver = isHovered(mouseX, mouseY);
	}

	public void renderDescription(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		if (isHovered(mouseX, mouseY) && setting.getDescription() != null && !parent.parent.dragging) {
			CharSequence chars = setting.getDescription();

			int tw = TextRenderer.getWidth(chars);

			int parentCenter = mc.getWindow().getWidth() / 2;
			int textCenter = parentCenter - tw / 2;

			int tooltipY = Math.min(mc.getWindow().getHeight() - 34, y + height + 8);
			GhostorTheme.panel(context, textCenter - 8, tooltipY, textCenter + tw + 8, tooltipY + 24, GhostorTheme.SURFACE_ELEVATED, 5);
			GhostorTheme.outline(context, textCenter - 8, tooltipY, textCenter + tw + 8, tooltipY + 24, GhostorTheme.BORDER, 5);
			TextRenderer.drawString(chars, context, textCenter, tooltipY + 7, GhostorTheme.TEXT.getRGB());
		}
	}

	public void onGuiClose() {
		this.currentColor = null;
	}

	public void keyPressed(int keyCode, int scanCode, int modifiers) {
	}

	public boolean isHovered(double mouseX, double mouseY) {
		return parent.parent.containsViewport(mouseX, mouseY)
				&& mouseX > parentX()
				&& mouseX < parentX() + parentWidth()
				&& mouseY > offset + parentOffset() + parentY()
				&& mouseY < offset + parentOffset() + parentY() + parentHeight();
	}

	public void onUpdate() {
		if (currentColor == null)
			currentColor = new Color(0, 0, 0, 0);
		else currentColor = new Color(0, 0, 0, currentColor.getAlpha());

		int toAlpha = 255;

		if (currentColor.getAlpha() != toAlpha)
			currentColor = ColorUtils.smoothAlphaTransition(0.05F, toAlpha, currentColor);
	}

	public void mouseClicked(double mouseX, double mouseY, int button) {
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
	}

	public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
	}
}
