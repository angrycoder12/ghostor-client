package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.utils.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Window {
	public List<ModuleButton> moduleButtons = new ArrayList<>();
	public int x;
	public int y;
	private int width, height, contentHeight;
	public Color currentColor;
	private final Category category;
	public boolean dragging, extended;
	private int dragX, dragY;
	private int prevX, prevY;
	private int scrollOffset;
	private int laidOutHeight;
	public ClickGui parent;

	public Window(int x, int y, int width, int height, Category category, ClickGui parent) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.dragging = false;
		this.extended = true;
		this.height = 46;
		this.contentHeight = height;
		this.category = category;
		this.parent = parent;

		this.prevX = x;
		this.prevY = y;

		int offset = height;
		List<Module> sortedModules = new ArrayList<>(Argon.INSTANCE.getModuleManager().getModulesInCategory(category));

		for (Module module : sortedModules) {
			moduleButtons.add(new ModuleButton(this, module, offset));
			offset += height;
		}
	}

	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		synchronizeModules();
		GhostorTheme.panel(context, prevX, prevY, prevX + width, prevY + contentHeight,
				new Color(GhostorTheme.SURFACE.getRed(), GhostorTheme.SURFACE.getGreen(),
						GhostorTheme.SURFACE.getBlue(), 150), 9);
		GhostorTheme.outline(context, prevX, prevY, prevX + width, prevY + contentHeight, GhostorTheme.BORDER, 9);
		context.enableScissor(prevX + 1, prevY + 1, prevX + width - 1, prevY + contentHeight - 1);

		updateButtons(delta);

		for (ModuleButton moduleButton : moduleButtons)
			if (parent.isModuleVisible(moduleButton.module)) moduleButton.render(context, mouseX, mouseY, delta);
		context.disableScissor();
	}


	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		synchronizeModules();
		for (ModuleButton moduleButton : moduleButtons)
			moduleButton.keyPressed(keyCode, scanCode, modifiers);
	}

	public void onGuiClose() {
		currentColor = null;

		for (ModuleButton moduleButton : moduleButtons)
			moduleButton.onGuiClose();

		dragging = false;
	}


	public boolean isDraggingAlready() {
		for(Window window : parent.windows)
			if(window.dragging)
				return true;

		return false;
	}

	public void mouseClicked(double mouseX, double mouseY, int button) {
		synchronizeModules();
		if (extended)
			for (ModuleButton moduleButton : moduleButtons)
				moduleButton.mouseClicked(mouseX, mouseY, button);
	}

	public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		synchronizeModules();
		if (extended) {
			for (ModuleButton moduleButton : moduleButtons) {
				moduleButton.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
			}
		}
	}

	public void updateButtons(float delta) {
		int offset = 8 + scrollOffset;

		for(ModuleButton moduleButton : moduleButtons) {
			if (!parent.isModuleVisible(moduleButton.module)) continue;
			moduleButton.animation.animate(0.5 * delta, moduleButton.extended ? height * (moduleButton.visibleSettingsCount() + 1) : height);

			double supHeight = moduleButton.animation.getValue();
			moduleButton.offset = offset;

			offset += (int) supHeight;
		}
		laidOutHeight = offset - scrollOffset + 8;
		int minimumScroll = Math.min(0, contentHeight - laidOutHeight);
		scrollOffset = Math.max(minimumScroll, Math.min(0, scrollOffset));
	}

	public Category getCategory() { return category; }

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.prevX = x;
		this.prevY = y;
		this.width = width;
		this.contentHeight = height;
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragging)
			dragging = false;

		for (ModuleButton moduleButton : moduleButtons)
			moduleButton.mouseReleased(mouseX, mouseY, button);
	}

	/** Keeps category moves stable without creating duplicate module objects. */
	public void synchronizeModules() {
		List<Module> expected = Argon.INSTANCE.getModuleManager().getModulesInCategory(category);
		if (moduleButtons.size() == expected.size()) {
			boolean same = true;
			for (int index = 0; index < expected.size(); index++) {
				if (moduleButtons.get(index).module != expected.get(index)) {
					same = false;
					break;
				}
			}
			if (same) return;
		}
		Map<Module, ModuleButton> existing = new HashMap<>();
		for (ModuleButton button : moduleButtons) existing.put(button.module, button);
		List<ModuleButton> refreshed = new ArrayList<>(expected.size());
		for (Module module : expected) {
			ModuleButton button = existing.get(module);
			refreshed.add(button == null ? new ModuleButton(this, module, height) : button);
		}
		moduleButtons.clear();
		moduleButtons.addAll(refreshed);
		scrollOffset = 0;
	}

	public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX < prevX || mouseX > prevX + width || mouseY < prevY || mouseY > prevY + contentHeight)
			return;
		int minimumScroll = Math.min(0, contentHeight - laidOutHeight);
		scrollOffset = Math.max(minimumScroll, Math.min(0, scrollOffset + (int) (verticalAmount * 30)));
	}

	public int getX() {
		return prevX;
	}

	public int getY() {
		return prevY;
	}

	public void setY(int y) {
		this.y = y;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean isHovered(double mouseX, double mouseY) {
		return ((mouseX > x && mouseX < x + width) && (mouseY > y && mouseY < y + height));
	}

	public boolean isPrevHovered(double mouseX, double mouseY) {
		return ((mouseX > prevX && mouseX < prevX + width) && (mouseY > prevY && mouseY < prevY + height));
	}

	public void updatePosition(double mouseX, double mouseY, float delta) {
		prevX = x;
		prevY = y;
	}
}
