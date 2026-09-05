package dev.lvstrng.argon.gui.components;

import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.gui.theme.ThemeDefinition;
import dev.lvstrng.argon.gui.theme.ThemeManager;
import dev.lvstrng.argon.gui.theme.ThemeManager.ParticleColorMode;
import dev.lvstrng.argon.gui.theme.ThemeManager.ParticleSettings;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/** Movable/resizable built-in and custom theme editor. */
public final class ThemeManagerPanel {
	public static final int PREFERRED_WIDTH = 780;
	public static final int PREFERRED_HEIGHT = 660;
	private static final int LIST_WIDTH = 214;
	private static final int ROW_HEIGHT = 44;
	private static final String[] COLOR_NAMES = {
			"Primary", "Secondary", "Background", "Panel", "Border",
			"Enabled Toggle", "Disabled Toggle", "Main Text", "Secondary Text", "Danger"
	};

	private final ThemeManager manager;
	private final Runnable closeAction;
	private int x;
	private int y;
	private int width = PREFERRED_WIDTH;
	private int height = PREFERRED_HEIGHT;
	private int scroll;
	private ThemeDefinition draft;
	private int selectedColor;
	private boolean particleTab;
	private boolean nameFocused;
	private int activeSlider = -1;

	public ThemeManagerPanel(ThemeManager manager, Runnable closeAction) {
		this.manager = manager;
		this.closeAction = closeAction;
		this.draft = manager.activeTheme();
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
		TextRenderer.drawString("Themes", context, x + 20, y + 24, GhostorTheme.TEXT.getRGB());
		TextRenderer.drawSmallString("Global Ghostor appearance and GUI particles", context,
				x + 20, y + 47, GhostorTheme.TEXT_MUTED.getRGB());

		renderThemeList(context, mouseX, mouseY);
		renderEditor(context, mouseX, mouseY);
		renderFooter(context, mouseX, mouseY);
	}

	private void renderThemeList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int left = x + 18;
		int top = y + 78;
		int bottom = y + height - 72;
		GhostorTheme.panel(context, left, top, left + LIST_WIDTH, bottom, GhostorTheme.SURFACE, 9);
		GhostorTheme.outline(context, left, top, left + LIST_WIDTH, bottom, GhostorTheme.BORDER, 9);
		List<ThemeDefinition> themes = manager.themes();
		int visible = Math.max(1, (bottom - top - 12) / ROW_HEIGHT);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, themes.size() - visible)));
		context.enableScissor(left + 1, top + 1, left + LIST_WIDTH - 1, bottom - 1);
		for (int row = 0; row < visible && row + scroll < themes.size(); row++) {
			ThemeDefinition theme = themes.get(row + scroll);
			int rowY = top + 6 + row * ROW_HEIGHT;
			boolean selected = draft != null && theme.id.equals(draft.id);
			boolean over = hovered(mouseX, mouseY, left + 6, rowY, left + LIST_WIDTH - 6, rowY + 38);
			GhostorTheme.panel(context, left + 6, rowY, left + LIST_WIDTH - 6, rowY + 38,
					selected ? GhostorTheme.ACTIVE_SURFACE : over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 7);
			GhostorTheme.outline(context, left + 6, rowY, left + LIST_WIDTH - 6, rowY + 38,
					selected ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 7);
			RenderUtils.renderCircle(context, new Color(theme.primary, true), left + 20, rowY + 19, 6, 12);
			TextRenderer.drawSmallString(fit(theme.name, 128), context, left + 34, rowY + 9, GhostorTheme.TEXT.getRGB());
			String flag = theme.id.equals(manager.activeId()) ? "Active" : theme.builtIn ? "Built-in" : "Custom";
			TextRenderer.drawSmallString(flag, context, left + 34, rowY + 24,
					theme.id.equals(manager.activeId()) ? GhostorTheme.ACCENT_HOVER.getRGB() : GhostorTheme.TEXT_MUTED.getRGB());
		}
		context.disableScissor();
	}

	private void renderEditor(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		if (draft == null) draft = manager.activeTheme();
		int left = x + LIST_WIDTH + 34;
		int right = x + width - 18;
		int previewTop = y + 78;
		Color previewPanel = new Color(draft.panel, true);
		GhostorTheme.panel(context, left, previewTop, right, previewTop + 76, previewPanel, draft.cornerRadius);
		GhostorTheme.outline(context, left, previewTop, right, previewTop + 76, new Color(draft.border, true), draft.cornerRadius);
		context.fill(left + 1, previewTop + 1, left + 5, previewTop + 75, draft.primary);
		TextRenderer.drawString(fit(draft.name, right - left - 120), context, left + 18, previewTop + 17, draft.text);
		TextRenderer.drawSmallString("Theme preview", context, left + 18, previewTop + 40, draft.secondaryText);
		GhostorTheme.panel(context, right - 58, previewTop + 25, right - 18, previewTop + 47,
				new Color(draft.enabledToggle, true), 11);
		RenderUtils.renderCircle(context, Color.WHITE, right - 30, previewTop + 36, 7, 14);

		int tabY = previewTop + 88;
		drawChoice(context, mouseX, mouseY, left, tabY, 112, "Appearance", !particleTab);
		drawChoice(context, mouseX, mouseY, left + 120, tabY, 112, "Particles", particleTab);
		if (particleTab) renderParticles(context, mouseX, mouseY, left, right, tabY + 46);
		else renderAppearance(context, mouseX, mouseY, left, right, tabY + 46);
	}

	private void renderAppearance(GuiGraphicsExtractor context, int mouseX, int mouseY,
			int left, int right, int top) {
		if (draft.builtIn) {
			TextRenderer.drawString("Built-in theme", context, left, top + 8, GhostorTheme.TEXT.getRGB());
			TextRenderer.drawSmallString("Built-in palettes are read-only. Use New Theme to make an editable copy.",
					context, left, top + 34, GhostorTheme.TEXT_MUTED.getRGB());
			return;
		}

		GhostorTheme.panel(context, left, top, right, top + 34,
				nameFocused ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 7);
		GhostorTheme.outline(context, left, top, right, top + 34,
				nameFocused ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 7);
		TextRenderer.drawSmallString(draft.name, context, left + 11, top + 13, GhostorTheme.TEXT.getRGB());

		int gridTop = top + 45;
		int gap = 8;
		int columnWidth = (right - left - gap) / 2;
		for (int index = 0; index < COLOR_NAMES.length; index++) {
			int column = index % 2;
			int row = index / 2;
			int rowX = left + column * (columnWidth + gap);
			int rowY = gridTop + row * 32;
			boolean over = hovered(mouseX, mouseY, rowX, rowY, rowX + columnWidth, rowY + 27);
			GhostorTheme.panel(context, rowX, rowY, rowX + columnWidth, rowY + 27,
					over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 6);
			GhostorTheme.outline(context, rowX, rowY, rowX + columnWidth, rowY + 27,
					selectedColor == index ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 6);
			TextRenderer.drawSmallString(COLOR_NAMES[index], context, rowX + 9, rowY + 10, GhostorTheme.TEXT_MUTED.getRGB());
			Color value = new Color(getThemeColor(index), true);
			GhostorTheme.panel(context, rowX + columnWidth - 29, rowY + 5, rowX + columnWidth - 8, rowY + 22, value, 4);
		}

		int channelsTop = gridTop + 5 * 32 + 8;
		Color selected = new Color(getThemeColor(selectedColor), true);
		renderSlider(context, left, right, channelsTop, "Red", selected.getRed(), 255);
		renderSlider(context, left, right, channelsTop + 27, "Green", selected.getGreen(), 255);
		renderSlider(context, left, right, channelsTop + 54, "Blue", selected.getBlue(), 255);
		renderSlider(context, left, right, channelsTop + 81, "Alpha", selected.getAlpha(), 255);
		renderCompactSlider(context, left, (left + right) / 2 - 4, channelsTop + 115,
				"Radius", draft.cornerRadius, 24);
		renderCompactSlider(context, (left + right) / 2 + 4, right, channelsTop + 115,
				"Glow", draft.glowIntensity, 100);
	}

	private void renderParticles(GuiGraphicsExtractor context, int mouseX, int mouseY,
			int left, int right, int top) {
		ParticleSettings particles = manager.particles();
		TextRenderer.drawString("Background Particles", context, left, top + 8, GhostorTheme.TEXT.getRGB());
		renderToggle(context, left, top + 34, particles.enabled);
		TextRenderer.drawSmallString(particles.enabled ? "Enabled" : "Disabled", context,
				left + 50, top + 43, GhostorTheme.TEXT_MUTED.getRGB());
		renderSlider(context, left, right, top + 78, "Amount", particles.amount, 100);
		renderSlider(context, left, right, top + 112, "Speed", (int) Math.round(particles.speed), 60);
		renderSlider(context, left, right, top + 146, "Size", (int) Math.round(particles.size * 10.0), 40);
		renderSlider(context, left, right, top + 180, "Opacity", particles.opacity, 100);
		TextRenderer.drawSmallString("Color Mode", context, left, top + 226, GhostorTheme.TEXT_MUTED.getRGB());
		drawChoice(context, mouseX, mouseY, left, top + 244, 138, "Theme Accent",
				particles.colorMode == ParticleColorMode.Theme_Accent);
		drawChoice(context, mouseX, mouseY, left + 146, top + 244, 100, "Custom",
				particles.colorMode == ParticleColorMode.Custom);
		if (particles.colorMode == ParticleColorMode.Custom) {
			Color custom = new Color(particles.customColor, true);
			GhostorTheme.panel(context, right - 48, top + 244, right, top + 278, custom, 6);
			renderSlider(context, left, right, top + 294, "Red", custom.getRed(), 255);
			renderSlider(context, left, right, top + 321, "Green", custom.getGreen(), 255);
			renderSlider(context, left, right, top + 348, "Blue", custom.getBlue(), 255);
		}
	}

	private void renderFooter(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int footerY = y + height - 55;
		drawSecondary(context, mouseX, mouseY, x + 18, footerY, 64, "Back");
		drawPrimary(context, mouseX, mouseY, x + 92, footerY, 104, "New Theme");
		int right = x + width - 18;
		if (draft != null && !draft.builtIn) {
			drawDanger(context, mouseX, mouseY, right - 292, footerY, 72, "Delete");
			drawSecondary(context, mouseX, mouseY, right - 210, footerY, 72, "Save");
		}
		drawPrimary(context, mouseX, mouseY, right - 128, footerY, 128, "Apply Theme");
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY)) return false;
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
		nameFocused = false;

		List<ThemeDefinition> themes = manager.themes();
		int listTop = y + 78;
		int listBottom = y + height - 72;
		int visible = Math.max(1, (listBottom - listTop - 12) / ROW_HEIGHT);
		for (int row = 0; row < visible && row + scroll < themes.size(); row++) {
			int rowY = listTop + 6 + row * ROW_HEIGHT;
			if (hovered(mouseX, mouseY, x + 24, rowY, x + 18 + LIST_WIDTH - 6, rowY + 38)) {
				draft = themes.get(row + scroll).copy();
				selectedColor = 0;
				return true;
			}
		}

		int left = x + LIST_WIDTH + 34;
		int right = x + width - 18;
		int tabY = y + 166;
		if (hovered(mouseX, mouseY, left, tabY, left + 112, tabY + 34)) {
			particleTab = false;
			return true;
		}
		if (hovered(mouseX, mouseY, left + 120, tabY, left + 232, tabY + 34)) {
			particleTab = true;
			return true;
		}

		int footerY = y + height - 55;
		if (hovered(mouseX, mouseY, x + 18, footerY, x + 82, footerY + 36)) {
			manager.saveParticleSettings();
			closeAction.run();
			return true;
		}
		if (hovered(mouseX, mouseY, x + 92, footerY, x + 196, footerY + 36)) {
			draft = manager.createCustom(draft);
			particleTab = false;
			selectedColor = 0;
			return true;
		}
		if (draft != null && !draft.builtIn && hovered(mouseX, mouseY,
				right - 292, footerY, right - 220, footerY + 36)) {
			manager.deleteCustom(draft.id);
			draft = manager.activeTheme();
			return true;
		}
		if (draft != null && !draft.builtIn && hovered(mouseX, mouseY,
				right - 210, footerY, right - 138, footerY + 36)) {
			ThemeDefinition saved = manager.saveCustom(draft);
			if (saved != null) draft = saved;
			return true;
		}
		if (hovered(mouseX, mouseY, right - 128, footerY, right, footerY + 36)) {
			if (draft != null && !draft.builtIn) {
				ThemeDefinition saved = manager.saveCustom(draft);
				if (saved != null) draft = saved;
			}
			if (draft != null) manager.apply(draft.id);
			return true;
		}

		if (particleTab) return handleParticleClick(mouseX, mouseY, left, right, y + 212);
		return handleAppearanceClick(mouseX, mouseY, left, right, y + 212);
	}

	private boolean handleAppearanceClick(double mouseX, double mouseY, int left, int right, int top) {
		if (draft == null || draft.builtIn) return true;
		if (hovered(mouseX, mouseY, left, top, right, top + 34)) {
			nameFocused = true;
			return true;
		}
		int gridTop = top + 45;
		int gap = 8;
		int columnWidth = (right - left - gap) / 2;
		for (int index = 0; index < COLOR_NAMES.length; index++) {
			int rowX = left + index % 2 * (columnWidth + gap);
			int rowY = gridTop + index / 2 * 32;
			if (hovered(mouseX, mouseY, rowX, rowY, rowX + columnWidth, rowY + 27)) {
				selectedColor = index;
				return true;
			}
		}
		int slidersTop = gridTop + 5 * 32 + 8;
		for (int channel = 0; channel < 4; channel++) {
			if (hovered(mouseX, mouseY, left + 92, slidersTop + channel * 27,
					right, slidersTop + channel * 27 + 18)) {
				activeSlider = channel;
				updateActiveSlider(mouseX, left, right);
				return true;
			}
		}
		int compactTop = slidersTop + 115;
		int middle = (left + right) / 2;
		if (hovered(mouseX, mouseY, left + 66, compactTop, middle - 4, compactTop + 18)) {
			activeSlider = 10;
			updateActiveSlider(mouseX, left, middle - 4);
		} else if (hovered(mouseX, mouseY, middle + 70, compactTop, right, compactTop + 18)) {
			activeSlider = 11;
			updateActiveSlider(mouseX, middle + 4, right);
		}
		return true;
	}

	private boolean handleParticleClick(double mouseX, double mouseY, int left, int right, int top) {
		ParticleSettings particles = manager.particles();
		if (hovered(mouseX, mouseY, left, top + 34, left + 40, top + 56)) {
			particles.enabled = !particles.enabled;
			manager.saveParticleSettings();
			return true;
		}
		int[] rows = {top + 78, top + 112, top + 146, top + 180};
		for (int index = 0; index < rows.length; index++) {
			if (hovered(mouseX, mouseY, left + 92, rows[index], right, rows[index] + 18)) {
				activeSlider = 20 + index;
				updateActiveSlider(mouseX, left, right);
				return true;
			}
		}
		if (hovered(mouseX, mouseY, left, top + 244, left + 138, top + 278)) {
			particles.colorMode = ParticleColorMode.Theme_Accent;
			manager.saveParticleSettings();
			return true;
		}
		if (hovered(mouseX, mouseY, left + 146, top + 244, left + 246, top + 278)) {
			particles.colorMode = ParticleColorMode.Custom;
			manager.saveParticleSettings();
			return true;
		}
		if (particles.colorMode == ParticleColorMode.Custom) {
			int[] customRows = {top + 294, top + 321, top + 348};
			for (int channel = 0; channel < customRows.length; channel++) {
				if (hovered(mouseX, mouseY, left + 92, customRows[channel], right, customRows[channel] + 18)) {
					activeSlider = 30 + channel;
					updateActiveSlider(mouseX, left, right);
					return true;
				}
			}
		}
		return true;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || activeSlider < 0) return false;
		int left = x + LIST_WIDTH + 34;
		int right = x + width - 18;
		if (activeSlider == 10) updateActiveSlider(mouseX, left, (left + right) / 2 - 4);
		else if (activeSlider == 11) updateActiveSlider(mouseX, (left + right) / 2 + 4, right);
		else updateActiveSlider(mouseX, left, right);
		return true;
	}

	public boolean mouseReleased(int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || activeSlider < 0) return false;
		boolean particlesChanged = activeSlider >= 20;
		activeSlider = -1;
		if (particlesChanged) manager.saveParticleSettings();
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!contains(mouseX, mouseY)) return false;
		if (mouseX <= x + 18 + LIST_WIDTH) {
			scroll = Math.max(0, scroll + (amount < 0 ? 1 : amount > 0 ? -1 : 0));
		}
		return true;
	}

	public boolean keyPressed(int keyCode) {
		if (nameFocused) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) nameFocused = false;
			else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && draft != null && !draft.name.isEmpty()) {
				draft.name = draft.name.substring(0, draft.name.offsetByCodePoints(draft.name.length(), -1));
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			manager.saveParticleSettings();
			closeAction.run();
			return true;
		}
		return false;
	}

	public boolean charTyped(String characters) {
		if (!nameFocused || draft == null || draft.builtIn || draft.name.length() >= 32) return false;
		draft.name += characters;
		return true;
	}

	public void clearFocus() {
		nameFocused = false;
		activeSlider = -1;
	}

	public boolean isInputFocused() {
		return nameFocused;
	}

	public boolean contains(double mouseX, double mouseY) {
		return hovered(mouseX, mouseY, x, y, x + width, y + height);
	}

	private void updateActiveSlider(double mouseX, int left, int right) {
		double start = left + (activeSlider == 10 || activeSlider == 11 ? 66 : 92);
		double value = Math.max(0.0D, Math.min(1.0D, (mouseX - start) / Math.max(1.0D, right - start)));
		if (activeSlider >= 0 && activeSlider <= 3 && draft != null) {
			Color old = new Color(getThemeColor(selectedColor), true);
			int channel = (int) Math.round(value * 255.0D);
			Color updated = switch (activeSlider) {
				case 0 -> new Color(channel, old.getGreen(), old.getBlue(), old.getAlpha());
				case 1 -> new Color(old.getRed(), channel, old.getBlue(), old.getAlpha());
				case 2 -> new Color(old.getRed(), old.getGreen(), channel, old.getAlpha());
				default -> new Color(old.getRed(), old.getGreen(), old.getBlue(), channel);
			};
			setThemeColor(selectedColor, updated.getRGB());
		} else if (activeSlider == 10 && draft != null) draft.cornerRadius = (int) Math.round(value * 24.0D);
		else if (activeSlider == 11 && draft != null) draft.glowIntensity = (int) Math.round(value * 100.0D);
		else if (activeSlider >= 20 && activeSlider <= 23) {
			ParticleSettings particles = manager.particles();
			switch (activeSlider) {
				case 20 -> particles.amount = (int) Math.round(value * 100.0D);
				case 21 -> particles.speed = Math.max(1.0D, value * 60.0D);
				case 22 -> particles.size = Math.max(0.5D, value * 4.0D);
				case 23 -> particles.opacity = Math.max(5, (int) Math.round(value * 100.0D));
			}
		} else if (activeSlider >= 30 && activeSlider <= 32) {
			ParticleSettings particles = manager.particles();
			Color old = new Color(particles.customColor, true);
			int channel = (int) Math.round(value * 255.0D);
			particles.customColor = switch (activeSlider) {
				case 30 -> new Color(channel, old.getGreen(), old.getBlue()).getRGB();
				case 31 -> new Color(old.getRed(), channel, old.getBlue()).getRGB();
				default -> new Color(old.getRed(), old.getGreen(), channel).getRGB();
			};
		}
	}

	private int getThemeColor(int index) {
		return switch (index) {
			case 0 -> draft.primary;
			case 1 -> draft.secondary;
			case 2 -> draft.background;
			case 3 -> draft.panel;
			case 4 -> draft.border;
			case 5 -> draft.enabledToggle;
			case 6 -> draft.disabledToggle;
			case 7 -> draft.text;
			case 8 -> draft.secondaryText;
			default -> draft.danger;
		};
	}

	private void setThemeColor(int index, int argb) {
		switch (index) {
			case 0 -> draft.primary = argb;
			case 1 -> draft.secondary = argb;
			case 2 -> draft.background = argb;
			case 3 -> draft.panel = argb;
			case 4 -> draft.border = argb;
			case 5 -> draft.enabledToggle = argb;
			case 6 -> draft.disabledToggle = argb;
			case 7 -> draft.text = argb;
			case 8 -> draft.secondaryText = argb;
			default -> draft.danger = argb;
		}
	}

	private static void renderSlider(GuiGraphicsExtractor context, int left, int right, int y,
			String label, int value, int maximum) {
		TextRenderer.drawSmallString(label + " " + value, context, left, y + 5, GhostorTheme.TEXT_MUTED.getRGB());
		int start = left + 92;
		int end = right;
		int filled = start + (int) Math.round((end - start) * Math.max(0.0D, Math.min(1.0D, value / (double) maximum)));
		context.fill(start, y + 8, end, y + 11, GhostorTheme.DISABLED_TOGGLE.getRGB());
		context.fill(start, y + 8, filled, y + 11, GhostorTheme.ACCENT.getRGB());
		RenderUtils.renderCircle(context, GhostorTheme.ACCENT_HOVER, filled, y + 9.5D, 4, 10);
	}

	private static void renderCompactSlider(GuiGraphicsExtractor context, int left, int right, int y,
			String label, int value, int maximum) {
		TextRenderer.drawSmallString(label + " " + value, context, left, y + 5, GhostorTheme.TEXT_MUTED.getRGB());
		int start = left + 66;
		int filled = start + (int) Math.round((right - start) * value / (double) maximum);
		context.fill(start, y + 8, right, y + 11, GhostorTheme.DISABLED_TOGGLE.getRGB());
		context.fill(start, y + 8, filled, y + 11, GhostorTheme.ACCENT.getRGB());
	}

	private static void renderToggle(GuiGraphicsExtractor context, int x, int y, boolean enabled) {
		GhostorTheme.panel(context, x, y, x + 40, y + 22,
				enabled ? GhostorTheme.ACCENT : GhostorTheme.DISABLED_TOGGLE, 11);
		RenderUtils.renderCircle(context, Color.WHITE, x + (enabled ? 29 : 11), y + 11, 7, 14);
	}

	private static void drawChoice(GuiGraphicsExtractor context, int mouseX, int mouseY,
			int x, int y, int width, String label, boolean selected) {
		boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 34);
		GhostorTheme.panel(context, x, y, x + width, y + 34,
				selected ? GhostorTheme.ACCENT : over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 7);
		GhostorTheme.outline(context, x, y, x + width, y + 34,
				selected ? GhostorTheme.ACCENT_HOVER : GhostorTheme.BORDER, 7);
		TextRenderer.drawCenteredString(label, context, x + width / 2, y + 12, GhostorTheme.TEXT.getRGB());
	}

	private static void drawPrimary(GuiGraphicsExtractor context, int mouseX, int mouseY,
			int x, int y, int width, String label) {
		boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 36);
		GhostorTheme.panel(context, x, y, x + width, y + 36,
				over ? GhostorTheme.ACCENT_HOVER : GhostorTheme.ACCENT, 7);
		TextRenderer.drawCenteredString(label, context, x + width / 2, y + 13, GhostorTheme.TEXT.getRGB());
	}

	private static void drawSecondary(GuiGraphicsExtractor context, int mouseX, int mouseY,
			int x, int y, int width, String label) {
		boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 36);
		GhostorTheme.panel(context, x, y, x + width, y + 36,
				over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.ACCENT_SOFT, 7);
		GhostorTheme.outline(context, x, y, x + width, y + 36,
				over ? GhostorTheme.ACCENT : GhostorTheme.ACCENT_BORDER, 7);
		TextRenderer.drawCenteredString(label, context, x + width / 2, y + 13, GhostorTheme.TEXT.getRGB());
	}

	private static void drawDanger(GuiGraphicsExtractor context, int mouseX, int mouseY,
			int x, int y, int width, String label) {
		boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 36);
		GhostorTheme.panel(context, x, y, x + width, y + 36,
				over ? GhostorTheme.DANGER_HOVER : GhostorTheme.DANGER, 7);
		TextRenderer.drawCenteredString(label, context, x + width / 2, y + 13, GhostorTheme.TEXT.getRGB());
	}

	private static boolean hovered(double mouseX, double mouseY, int x1, int y1, int x2, int y2) {
		return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
	}

	private static String fit(String value, int maximumWidth) {
		if (TextRenderer.getSmallWidth(value) <= maximumWidth) return value;
		String result = value;
		while (!result.isEmpty() && TextRenderer.getSmallWidth(result + "…") > maximumWidth) {
			result = result.substring(0, result.offsetByCodePoints(result.length(), -1));
		}
		return result + "…";
	}
}
