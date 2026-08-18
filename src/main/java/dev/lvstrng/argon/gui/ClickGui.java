package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.utils.ColorUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.lvstrng.argon.Argon.mc;

public final class ClickGui extends Screen {
	public List<Window> windows = new ArrayList<>();
	public Color currentColor;
	private Category selectedCategory = Category.COMBAT;
	private String search = "";
	private boolean searchFocused;
	private int panelX, panelY, panelWidth, panelHeight;
	private static final int SIDEBAR_WIDTH = 142;

	public ClickGui() {
		super(Text.empty());

		int offsetX = 50;
		for (Category category : Category.values()) {
			windows.add(new Window(offsetX, 50, 460, 30, category, this));
			offsetX += 480;
		}
	}

	public boolean isModuleVisible(dev.lvstrng.argon.module.Module module) {
		return search.isBlank()
				|| module.getName().toString().toLowerCase().contains(search.toLowerCase())
				|| (module.getDescription() != null && module.getDescription().toString().toLowerCase().contains(search.toLowerCase()));
	}

	public Category getSelectedCategory() {
		return selectedCategory;
	}

	public boolean isDraggingAlready() {
		for(Window window : windows)
			if(window.dragging)
				return true;

		return false;
	}

	@Override
	protected void setInitialFocus() {
		if (client == null) {
			return;
		}
		super.setInitialFocus();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (mc.currentScreen == this) {
			if (Argon.INSTANCE.previousScreen != null)
				Argon.INSTANCE.previousScreen.render(context, 0, 0, delta);

			if (currentColor == null)
				currentColor = new Color(0, 0, 0, 0);
			else currentColor = new Color(0, 0, 0, currentColor.getAlpha());

			if (currentColor.getAlpha() != (ClickGUI.background.getValue() ? 200 : 0))
				currentColor = ColorUtils.smoothAlphaTransition(0.05F, ClickGUI.background.getValue() ? 200 : 0, currentColor);

			if (mc.currentScreen instanceof ClickGui)
				context.fill(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight(), currentColor.getRGB());

			RenderUtils.unscaledProjection(context);
			mouseX *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
			mouseY *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
			super.render(context, mouseX, mouseY, delta);
			layout();
			renderShell(context, mouseX, mouseY);

			for (Window window : windows) {
				if (window.getCategory() != selectedCategory) continue;
				window.setBounds(panelX + SIDEBAR_WIDTH + 22, panelY + 68, panelWidth - SIDEBAR_WIDTH - 44, panelHeight - 86);
				window.render(context, mouseX, mouseY, delta);
				window.updatePosition(mouseX, mouseY, delta);
			}

			RenderUtils.scaledProjection(context);
		}
	}

	private void layout() {
		int screenWidth = mc.getWindow().getWidth();
		int screenHeight = mc.getWindow().getHeight();
		panelWidth = Math.min(Math.max(560, screenWidth - 80), 980);
		panelHeight = Math.min(Math.max(360, screenHeight - 80), 640);
		panelX = Math.max(20, (screenWidth - panelWidth) / 2);
		panelY = Math.max(20, (screenHeight - panelHeight) / 2);
	}

	private void renderShell(DrawContext context, int mouseX, int mouseY) {
		GhostorTheme.panel(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight, GhostorTheme.SURFACE, GhostorTheme.RADIUS);
		GhostorTheme.outline(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight, GhostorTheme.BORDER, GhostorTheme.RADIUS);
		context.fill(panelX + SIDEBAR_WIDTH, panelY + 14, panelX + SIDEBAR_WIDTH + 1, panelY + panelHeight - 14, GhostorTheme.BORDER.getRGB());

		TextRenderer.drawString("GHOSTOR", context, panelX + 18, panelY + 18, GhostorTheme.TEXT.getRGB());
		TextRenderer.drawString("CLIENT", context, panelX + 18, panelY + 34, GhostorTheme.ACCENT.getRGB());
		TextRenderer.drawString(selectedCategory.name.toString().toUpperCase(), context, panelX + SIDEBAR_WIDTH + 22, panelY + 20, GhostorTheme.TEXT.getRGB());
		TextRenderer.drawString("Modules", context, panelX + SIDEBAR_WIDTH + 22, panelY + 37, GhostorTheme.TEXT_MUTED.getRGB());

		int searchWidth = Math.min(220, panelWidth / 3);
		int searchX = panelX + panelWidth - searchWidth - 18;
		GhostorTheme.panel(context, searchX, panelY + 17, searchX + searchWidth, panelY + 48,
				searchFocused ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 6);
		GhostorTheme.outline(context, searchX, panelY + 17, searchX + searchWidth, panelY + 48,
				searchFocused ? GhostorTheme.ACCENT : GhostorTheme.BORDER, 6);
		TextRenderer.drawString(search.isBlank() ? "Search modules" : search, context, searchX + 11, panelY + 27,
				search.isBlank() ? GhostorTheme.DISABLED.getRGB() : GhostorTheme.TEXT.getRGB());

		int y = panelY + 76;
		for (Category category : Category.values()) {
			boolean selected = category == selectedCategory;
			boolean hovered = mouseX >= panelX + 10 && mouseX <= panelX + SIDEBAR_WIDTH - 10 && mouseY >= y && mouseY <= y + GhostorTheme.ROW;
			if (selected || hovered) GhostorTheme.panel(context, panelX + 10, y, panelX + SIDEBAR_WIDTH - 10, y + GhostorTheme.ROW,
					selected ? new Color(GhostorTheme.ACCENT.getRed(), GhostorTheme.ACCENT.getGreen(), GhostorTheme.ACCENT.getBlue(), 48) : GhostorTheme.SURFACE_HOVER, 6);
			if (selected) context.fill(panelX + 10, y + 7, panelX + 13, y + GhostorTheme.ROW - 7, GhostorTheme.ACCENT.getRGB());
			TextRenderer.drawString(category.name, context, panelX + 24, y + 10, (selected ? GhostorTheme.TEXT : GhostorTheme.TEXT_MUTED).getRGB());
			y += GhostorTheme.ROW + 4;
		}
	}

	@Override
	public boolean keyPressed(KeyInput keyInput) {
		int keyCode = keyInput.key();
		int scanCode = keyInput.scancode();
		int modifiers = keyInput.modifiers();

		if (searchFocused) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) searchFocused = false;
			else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) search = search.substring(0, search.length() - 1);
			return true;
		}

		for (Window window : windows)
			if (window.getCategory() == selectedCategory) window.keyPressed(keyCode, scanCode, modifiers);

		return super.keyPressed(keyInput);
	}

	@Override
	public boolean charTyped(CharInput charInput) {
		if (searchFocused && charInput.isValidChar()) {
			search += charInput.asString();
			return true;
		}
		return super.charTyped(charInput);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mouseX = click.x();
		double mouseY = click.y();
		int button = click.button();

		mouseX *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
		mouseY *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();

		int searchWidth = Math.min(220, panelWidth / 3);
		int searchX = panelX + panelWidth - searchWidth - 18;
		if (mouseX >= searchX && mouseX <= searchX + searchWidth && mouseY >= panelY + 17 && mouseY <= panelY + 48) {
			searchFocused = true;
			return true;
		}
		int y = panelY + 76;
		for (Category category : Category.values()) {
			if (mouseX >= panelX + 10 && mouseX <= panelX + SIDEBAR_WIDTH - 10 && mouseY >= y && mouseY <= y + GhostorTheme.ROW) {
				selectedCategory = category;
				searchFocused = false;
				return true;
			}
			y += GhostorTheme.ROW + 4;
		}

		for (Window window : windows)
			if (window.getCategory() == selectedCategory) window.mouseClicked(mouseX, mouseY, button);

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double deltaX, double deltaY) {
		double mouseX = click.x();
		double mouseY = click.y();
		int button = click.button();

		mouseX *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
		mouseY *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();

		for (Window window : windows)
			if (window.getCategory() == selectedCategory) window.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

		return super.mouseDragged(click, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		MinecraftClient mc = MinecraftClient.getInstance();
		mouseY *= mc.getWindow().getScaleFactor();

		for (Window window : windows)
			if (window.getCategory() == selectedCategory) window.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		Argon.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
		onGuiClose();
	}

	public void onGuiClose() {
		mc.setScreenAndRender(Argon.INSTANCE.previousScreen);
		currentColor = null;

		for (Window window : windows)
			window.onGuiClose();
	}

	@Override
	public boolean mouseReleased(Click click) {
		double mouseX = click.x();
		double mouseY = click.y();
		int button = click.button();

		mouseX *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
		mouseY *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();

		for (Window window : windows)
			if (window.getCategory() == selectedCategory) window.mouseReleased(mouseX, mouseY, button);

		return super.mouseReleased(click);
	}
}
