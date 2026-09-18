package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.components.FriendsPanel;
import dev.lvstrng.argon.gui.components.ConfigManagerPanel;
import dev.lvstrng.argon.gui.components.ConfigFormPanel;
import dev.lvstrng.argon.gui.components.GhostorIcons;
import dev.lvstrng.argon.gui.components.ThemeManagerPanel;
import dev.lvstrng.argon.gui.layout.GuiBounds;
import dev.lvstrng.argon.gui.layout.GuiLayoutState;
import dev.lvstrng.argon.gui.screens.BlockSelectorScreen;
import dev.lvstrng.argon.gui.screens.MobSelectorScreen;
import dev.lvstrng.argon.gui.theme.ThemeManager;
import dev.lvstrng.argon.gui.theme.ThemeParticles;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.modules.client.SelfDestruct;
import dev.lvstrng.argon.module.modules.render.BlockESP;
import dev.lvstrng.argon.module.modules.render.MobESP;
import dev.lvstrng.argon.config.ConfigManager;
import dev.lvstrng.argon.config.ConfigManager.ImportedConfig;
import dev.lvstrng.argon.utils.ColorUtils;
import dev.lvstrng.argon.utils.ClientState;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static dev.lvstrng.argon.Argon.mc;

public final class ClickGui extends Screen {
    private enum PanelLayer {
        MAIN, FRIENDS, BLOCK_SELECTOR, MOB_SELECTOR, CONFIGS, CONFIG_FORM, THEMES
    }

    private static final int SIDEBAR_WIDTH = 182;
    private static final int DEFAULT_PANEL_WIDTH = 900;
    private static final int DEFAULT_PANEL_HEIGHT = 710;
    private static final int FRIENDS_GAP = 12;
    private static final int BOMB_SIZE = 68;
    private static final int DEFAULT_BLOCK_SELECTOR_WIDTH = 800;
    private static final int DEFAULT_BLOCK_SELECTOR_HEIGHT = 620;
	private static final int DEFAULT_MOB_SELECTOR_WIDTH = 800;
	private static final int DEFAULT_MOB_SELECTOR_HEIGHT = 620;
	private static final int DEFAULT_CONFIG_WIDTH = ConfigManagerPanel.PREFERRED_WIDTH;
	private static final int DEFAULT_CONFIG_HEIGHT = ConfigManagerPanel.PREFERRED_HEIGHT;
	private static final int DEFAULT_CONFIG_FORM_WIDTH = ConfigFormPanel.PREFERRED_WIDTH;
	private static final int DEFAULT_CONFIG_FORM_HEIGHT = ConfigFormPanel.PREFERRED_HEIGHT;
	private static final int DEFAULT_THEMES_WIDTH = ThemeManagerPanel.PREFERRED_WIDTH;
	private static final int DEFAULT_THEMES_HEIGHT = ThemeManagerPanel.PREFERRED_HEIGHT;

    public final List<Window> windows = new ArrayList<>();
    public Color currentColor;
    private final FriendsPanel friendsPanel = new FriendsPanel(this::closeFriendsPanel);
	private final ConfigManagerPanel configPanel = new ConfigManagerPanel(this);
	private final ConfigFormPanel configForm = new ConfigFormPanel(this);
	private final ThemeManager themeManager = new ThemeManager();
	private final ThemeManagerPanel themesPanel = new ThemeManagerPanel(themeManager, this::closeThemesPanel);
	private final ThemeParticles themeParticles = new ThemeParticles();
    private final List<PanelLayer> panelStack = new ArrayList<>(List.of(
			PanelLayer.MAIN, PanelLayer.FRIENDS, PanelLayer.BLOCK_SELECTOR, PanelLayer.MOB_SELECTOR,
			PanelLayer.CONFIGS, PanelLayer.CONFIG_FORM, PanelLayer.THEMES));
    private Category selectedCategory = Category.COMBAT;
    private String search = "";
	private final Set<dev.lvstrng.argon.module.Module> searchMatches =
			Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean searchFocused;
    private boolean friendsOpen;
	private boolean configsOpen;
	private boolean configFormOpen;
	private boolean themesOpen;
    private boolean childScreenOpen;
    private BlockSelectorScreen blockSelector;
	private MobSelectorScreen mobSelector;
    private final GuiBounds mainBounds = new GuiBounds(GhostorTheme.MAIN_MIN_WIDTH, GhostorTheme.MAIN_MIN_HEIGHT);
    private final GuiBounds friendsBounds = new GuiBounds(GhostorTheme.FRIENDS_MIN_WIDTH, GhostorTheme.FRIENDS_MIN_HEIGHT);
    private final GuiBounds blockSelectorBounds = new GuiBounds(
            GhostorTheme.BLOCK_SELECTOR_MIN_WIDTH, GhostorTheme.BLOCK_SELECTOR_MIN_HEIGHT);
	private final GuiBounds mobSelectorBounds = new GuiBounds(
			GhostorTheme.MOB_SELECTOR_MIN_WIDTH, GhostorTheme.MOB_SELECTOR_MIN_HEIGHT);
	private final GuiBounds configBounds = new GuiBounds(GhostorTheme.CONFIG_MIN_WIDTH, GhostorTheme.CONFIG_MIN_HEIGHT);
	private final GuiBounds configFormBounds = new GuiBounds(
			GhostorTheme.CONFIG_FORM_MIN_WIDTH, GhostorTheme.CONFIG_FORM_MIN_HEIGHT);
	private final GuiBounds themesBounds = new GuiBounds(
			GhostorTheme.THEMES_MIN_WIDTH, GhostorTheme.THEMES_MIN_HEIGHT);
    private final GuiLayoutState layoutState = new GuiLayoutState();
    private boolean layoutInitialized;
	private GuiLayoutState.SavedLayout pendingLayout;
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int friendButtonY;
	private int configButtonY;
	private int themesButtonY;
    private int bombX;
    private int bombY;

    public ClickGui() {
        super(Component.empty());
        for (Category category : Category.values()) {
            windows.add(new Window(0, 0, 460, 46, category, this));
        }
    }

    public boolean isModuleVisible(dev.lvstrng.argon.module.Module module) {
		return search.isBlank() || searchMatches.contains(module);
    }

	private void refreshSearchMatches() {
		searchMatches.clear();
		if (search.isBlank()) return;
		String query = search.toLowerCase(Locale.ROOT);
		for (dev.lvstrng.argon.module.Module module : Argon.INSTANCE.getModuleManager().getModules()) {
			if (module.getName().toString().toLowerCase(Locale.ROOT).contains(query)
					|| module.getDescription() != null
					&& module.getDescription().toString().toLowerCase(Locale.ROOT).contains(query)) {
				searchMatches.add(module);
			}
		}
	}

    public Category getSelectedCategory() {
        return selectedCategory;
    }

	public boolean isDraggingAlready() {
		return mainBounds.isInteracting() || friendsBounds.isInteracting() || blockSelectorBounds.isInteracting()
				|| mobSelectorBounds.isInteracting()
				|| configBounds.isInteracting() || configFormBounds.isInteracting() || themesBounds.isInteracting();
    }

    public boolean isTextInputFocused() {
        return searchFocused || (friendsOpen && friendsPanel.isInputFocused())
				|| (blockSelector != null && blockSelector.isInputFocused())
				|| (mobSelector != null && mobSelector.isInputFocused())
				|| (configsOpen && configPanel.isInputFocused())
				|| (configFormOpen && configForm.isInputFocused())
				|| (themesOpen && themesPanel.isInputFocused());
    }

    public void suspendForChildScreen() {
        childScreenOpen = true;
        searchFocused = false;
        friendsPanel.clearFocus();
    }

    public void resumeFromChildScreen() {
        childScreenOpen = false;
    }

    public GuiBounds blockSelectorBounds() {
        return blockSelectorBounds;
    }

	public GuiBounds mobSelectorBounds() { return mobSelectorBounds; }

	public void openMobSelector(MobESP module) {
		if (mobSelector == null || !mobSelector.isFor(module)) mobSelector = new MobSelectorScreen(this, module);
		searchFocused = false;
		friendsPanel.clearFocus();
		if (blockSelector != null) blockSelector.clearInputFocus();
		bringToFront(PanelLayer.MOB_SELECTOR);
	}

	public void closeMobSelector() { mobSelector = null; }

    public void openBlockSelector(BlockESP module) {
        if (blockSelector == null || !blockSelector.isFor(module)) {
            blockSelector = new BlockSelectorScreen(this, module);
        }
        searchFocused = false;
        friendsPanel.clearFocus();
		if (mobSelector != null) mobSelector.clearInputFocus();
        bringToFront(PanelLayer.BLOCK_SELECTOR);
    }

    public void closeBlockSelector() {
        blockSelector = null;
    }

    public void openFriendsPanel() {
        friendsOpen = true;
        searchFocused = false;
        if (blockSelector != null) blockSelector.clearInputFocus();
		if (mobSelector != null) mobSelector.clearInputFocus();
        bringToFront(PanelLayer.FRIENDS);
        if (layoutInitialized) {
            friendsBounds.clampToScreen(mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                    GhostorTheme.PANEL_MARGIN);
            syncBounds();
        }
    }

	public void openConfigsPanel() {
		configsOpen = true;
		searchFocused = false;
		friendsPanel.clearFocus();
		if (blockSelector != null) blockSelector.clearInputFocus();
		if (mobSelector != null) mobSelector.clearInputFocus();
		bringToFront(PanelLayer.CONFIGS);
		if (layoutInitialized) {
			configBounds.clampToScreen(mc.getWindow().getWidth(), mc.getWindow().getHeight(), GhostorTheme.PANEL_MARGIN);
			syncBounds();
		}
	}

	public void closeConfigsPanel() {
		configsOpen = false;
		configPanel.clearFocus();
		if (configFormOpen) closeConfigForm();
		if (configBounds.endInteraction()) saveLayout();
	}

	public void openThemesPanel() {
		themesOpen = true;
		searchFocused = false;
		friendsPanel.clearFocus();
		configPanel.clearFocus();
		if (blockSelector != null) blockSelector.clearInputFocus();
		if (mobSelector != null) mobSelector.clearInputFocus();
		bringToFront(PanelLayer.THEMES);
		if (layoutInitialized) {
			themesBounds.clampToScreen(mc.getWindow().getWidth(), mc.getWindow().getHeight(), GhostorTheme.PANEL_MARGIN);
			syncBounds();
		}
	}

	public void closeThemesPanel() {
		themesOpen = false;
		themesPanel.clearFocus();
		if (themesBounds.endInteraction()) saveLayout();
	}

	public void openNewConfig(ImportedConfig imported) {
		configForm.beginNew(imported);
		configFormOpen = true;
		bringToFront(PanelLayer.CONFIG_FORM);
	}

	public void openConfigEdit(String id) {
		if (!configForm.beginEdit(id)) return;
		configFormOpen = true;
		bringToFront(PanelLayer.CONFIG_FORM);
	}

	public void closeConfigForm() {
		configFormOpen = false;
		configForm.clearFocus();
		if (configFormBounds.endInteraction()) saveLayout();
		if (configsOpen) bringToFront(PanelLayer.CONFIGS);
	}

	public void refreshModuleWindows() {
		for (Window window : windows) window.synchronizeModules();
		if (selectedCategory != Category.DISABLED
				&& Argon.INSTANCE.getModuleManager().getModulesInCategory(selectedCategory).isEmpty()) {
			selectedCategory = Category.COMBAT;
		}
	}

	public GuiLayoutState.SavedLayout captureLayoutState() {
		if (!layoutInitialized) return pendingLayout != null ? pendingLayout : layoutState.load();
		return layoutState.capture(mainBounds, friendsBounds, blockSelectorBounds, mobSelectorBounds,
				configBounds, configFormBounds, themesBounds);
	}

	public void applyLayoutState(GuiLayoutState.SavedLayout saved) {
		pendingLayout = saved;
		if (layoutInitialized && saved != null) {
			applySavedLayout(saved);
			clampAllBounds();
			syncBounds();
			saveLayout();
		}
	}

	public void resetLayoutState() {
		pendingLayout = new GuiLayoutState.SavedLayout();
		layoutInitialized = false;
	}

    public void closeFriendsPanel() {
        friendsOpen = false;
        friendsPanel.clearFocus();
        if (friendsBounds.endInteraction()) {
            saveLayout();
        }
    }

    private void bringToFront(PanelLayer layer) {
        panelStack.remove(layer);
        panelStack.add(layer);
    }

    private PanelLayer topPanelAt(double mouseX, double mouseY) {
        for (int index = panelStack.size() - 1; index >= 0; index--) {
            PanelLayer layer = panelStack.get(index);
            boolean contains = switch (layer) {
                case MAIN -> mainBounds.contains(mouseX, mouseY);
                case FRIENDS -> friendsOpen && friendsBounds.contains(mouseX, mouseY);
                case BLOCK_SELECTOR -> blockSelector != null && blockSelectorBounds.contains(mouseX, mouseY);
				case MOB_SELECTOR -> mobSelector != null && mobSelectorBounds.contains(mouseX, mouseY);
				case CONFIGS -> configsOpen && configBounds.contains(mouseX, mouseY);
				case CONFIG_FORM -> configFormOpen && configFormBounds.contains(mouseX, mouseY);
				case THEMES -> themesOpen && themesBounds.contains(mouseX, mouseY);
            };
            if (contains) return layer;
        }
        return null;
    }

	private PanelLayer topOpenPanel() {
		for (int index = panelStack.size() - 1; index >= 0; index--) {
			PanelLayer layer = panelStack.get(index);
			if (layer == PanelLayer.MAIN
					|| layer == PanelLayer.FRIENDS && friendsOpen
					|| layer == PanelLayer.BLOCK_SELECTOR && blockSelector != null
					|| layer == PanelLayer.MOB_SELECTOR && mobSelector != null
					|| layer == PanelLayer.CONFIGS && configsOpen
					|| layer == PanelLayer.CONFIG_FORM && configFormOpen
					|| layer == PanelLayer.THEMES && themesOpen) return layer;
		}
		return PanelLayer.MAIN;
	}

    private void focusPanel(PanelLayer layer) {
        boolean layoutChanged = false;
        if (layer != PanelLayer.MAIN) {
            layoutChanged |= mainBounds.endInteraction();
            searchFocused = false;
        }
        if (layer != PanelLayer.FRIENDS) {
            layoutChanged |= friendsBounds.endInteraction();
            friendsPanel.clearFocus();
        }
        if (layer != PanelLayer.BLOCK_SELECTOR && blockSelector != null) {
            layoutChanged |= blockSelector.loseFocus();
        }
		if (layer != PanelLayer.MOB_SELECTOR && mobSelector != null) layoutChanged |= mobSelector.loseFocus();
		if (layer != PanelLayer.CONFIGS) {
			layoutChanged |= configBounds.endInteraction();
			configPanel.clearFocus();
		}
		if (layer != PanelLayer.CONFIG_FORM) {
			layoutChanged |= configFormBounds.endInteraction();
			configForm.clearFocus();
		}
		if (layer != PanelLayer.THEMES) {
			layoutChanged |= themesBounds.endInteraction();
			themesPanel.clearFocus();
		}
        if (layoutChanged) saveLayout();
        bringToFront(layer);
    }

    @Override
    protected void setInitialFocus() {
        if (minecraft != null) {
            super.setInitialFocus();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (mc.gui.screen() != this) {
            return;
        }
        renderClickGui(context, mouseX, mouseY, delta);
    }

    private void renderClickGui(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!ClientState.hasActiveWorld()) {
            minecraft.execute(this::onClose);
            return;
        }

        if (Argon.INSTANCE.previousScreen != null) {
            Argon.INSTANCE.previousScreen.extractRenderState(context, 0, 0, delta);
        }
        context.blurBeforeThisStratum();

		int targetAlpha = ClickGUI.background.getValue() ? GhostorTheme.BACKDROP.getAlpha() : 104;
		if (currentColor == null) {
			currentColor = new Color(GhostorTheme.BACKDROP.getRed(), GhostorTheme.BACKDROP.getGreen(),
					GhostorTheme.BACKDROP.getBlue(), targetAlpha);
		} else {
			currentColor = new Color(GhostorTheme.BACKDROP.getRed(), GhostorTheme.BACKDROP.getGreen(),
					GhostorTheme.BACKDROP.getBlue(), currentColor.getAlpha());
            if (currentColor.getAlpha() != targetAlpha) {
                currentColor = ColorUtils.smoothAlphaTransition(0.08F, targetAlpha, currentColor);
            }
        }
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), currentColor.getRGB());

        RenderUtils.unscaledProjection(context);
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int physicalMouseX = (int) Math.round(mouseX * scale);
		int physicalMouseY = (int) Math.round(mouseY * scale);
		layout();
		themeParticles.render(context, mc.getWindow().getWidth(), mc.getWindow().getHeight(), themeManager.particles());
		renderBombButton(context, physicalMouseX, physicalMouseY);
        for (PanelLayer layer : panelStack) {
            renderPanelLayer(layer, context, physicalMouseX, physicalMouseY, delta);
        }
        RenderUtils.scaledProjection(context);
    }

    private void renderPanelLayer(PanelLayer layer, GuiGraphicsExtractor context,
            int mouseX, int mouseY, float delta) {
        switch (layer) {
            case MAIN -> {
                renderShell(context, mouseX, mouseY);
                for (Window window : windows) {
                    if (window.getCategory() != selectedCategory) continue;
                    window.setBounds(panelX + SIDEBAR_WIDTH + 22, panelY + 88,
                            panelWidth - SIDEBAR_WIDTH - 44, panelHeight - 108);
                    window.render(context, mouseX, mouseY, delta);
                }
                boolean resizeHovered = mainBounds.isOverResizeHandle(
                        mouseX, mouseY, GhostorTheme.RESIZE_HANDLE);
                GhostorTheme.resizeHandle(context, mainBounds.right(), mainBounds.bottom(),
                        resizeHovered || mainBounds.isResizing());
            }
            case FRIENDS -> {
                if (!friendsOpen) return;
                friendsPanel.render(context, mouseX, mouseY);
                boolean resizeHovered = friendsBounds.isOverResizeHandle(
                        mouseX, mouseY, GhostorTheme.RESIZE_HANDLE);
                GhostorTheme.resizeHandle(context, friendsBounds.right(), friendsBounds.bottom(),
                        resizeHovered || friendsBounds.isResizing());
            }
            case BLOCK_SELECTOR -> {
                if (blockSelector != null) blockSelector.render(context, mouseX, mouseY, delta);
            }
			case MOB_SELECTOR -> {
				if (mobSelector != null) mobSelector.render(context, mouseX, mouseY, delta);
			}
			case CONFIGS -> {
				if (!configsOpen) return;
				configPanel.render(context, mouseX, mouseY);
				GhostorTheme.resizeHandle(context, configBounds.right(), configBounds.bottom(),
						configBounds.isResizing() || configBounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE));
			}
			case CONFIG_FORM -> {
				if (!configFormOpen) return;
				configForm.render(context, mouseX, mouseY);
				GhostorTheme.resizeHandle(context, configFormBounds.right(), configFormBounds.bottom(),
						configFormBounds.isResizing() || configFormBounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE));
			}
			case THEMES -> {
				if (!themesOpen) return;
				themesPanel.render(context, mouseX, mouseY);
				GhostorTheme.resizeHandle(context, themesBounds.right(), themesBounds.bottom(),
						themesBounds.isResizing()
								|| themesBounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE));
			}
		}
    }

    private void layout() {
        int screenWidth = mc.getWindow().getWidth();
        int screenHeight = mc.getWindow().getHeight();
        if (!layoutInitialized) {
            int usableWidth = Math.max(1, screenWidth - GhostorTheme.PANEL_MARGIN * 2);
            int usableHeight = Math.max(1, screenHeight - GhostorTheme.PANEL_MARGIN * 2);
            int mainWidth = Math.min(DEFAULT_PANEL_WIDTH, usableWidth);
            int mainHeight = Math.min(DEFAULT_PANEL_HEIGHT, usableHeight);
            int friendsWidth = Math.min(FriendsPanel.PREFERRED_WIDTH, usableWidth);
            int friendsHeight = Math.min(FriendsPanel.PREFERRED_HEIGHT, usableHeight);
            int selectorWidth = Math.min(DEFAULT_BLOCK_SELECTOR_WIDTH, usableWidth);
            int selectorHeight = Math.min(DEFAULT_BLOCK_SELECTOR_HEIGHT, usableHeight);
			int mobSelectorWidth = Math.min(DEFAULT_MOB_SELECTOR_WIDTH, usableWidth);
			int mobSelectorHeight = Math.min(DEFAULT_MOB_SELECTOR_HEIGHT, usableHeight);
			int configsWidth = Math.min(DEFAULT_CONFIG_WIDTH, usableWidth);
			int configsHeight = Math.min(DEFAULT_CONFIG_HEIGHT, usableHeight);
			int formWidth = Math.min(DEFAULT_CONFIG_FORM_WIDTH, usableWidth);
			int formHeight = Math.min(DEFAULT_CONFIG_FORM_HEIGHT, usableHeight);
			int themesWidth = Math.min(DEFAULT_THEMES_WIDTH, usableWidth);
			int themesHeight = Math.min(DEFAULT_THEMES_HEIGHT, usableHeight);
            int groupWidth = mainWidth + FRIENDS_GAP + friendsWidth;
            int mainX = groupWidth <= usableWidth
                    ? (screenWidth - groupWidth) / 2
                    : (screenWidth - mainWidth) / 2;
            int mainY = (screenHeight - mainHeight) / 2;
            mainBounds.initialize(mainX, mainY, mainWidth, mainHeight);

            int friendsX = mainX + mainWidth + FRIENDS_GAP;
            if (friendsX + friendsWidth > screenWidth - GhostorTheme.PANEL_MARGIN) {
                friendsX = mainX + mainWidth - friendsWidth - 18;
            }
            friendsBounds.initialize(friendsX, (screenHeight - friendsHeight) / 2,
                    friendsWidth, friendsHeight);
            int selectorX = Math.min(screenWidth - GhostorTheme.PANEL_MARGIN - selectorWidth,
                    mainX + Math.min(240, Math.max(40, mainWidth / 4)));
            blockSelectorBounds.initialize(selectorX, (screenHeight - selectorHeight) / 2,
                    selectorWidth, selectorHeight);
			mobSelectorBounds.initialize(Math.max(GhostorTheme.PANEL_MARGIN, selectorX - 22),
					Math.max(GhostorTheme.PANEL_MARGIN, (screenHeight - mobSelectorHeight) / 2 + 18),
					mobSelectorWidth, mobSelectorHeight);
			configBounds.initialize(Math.max(GhostorTheme.PANEL_MARGIN,
					mainX + mainWidth - configsWidth / 2), (screenHeight - configsHeight) / 2,
					configsWidth, configsHeight);
			configFormBounds.initialize((screenWidth - formWidth) / 2,
					(screenHeight - formHeight) / 2, formWidth, formHeight);
			themesBounds.initialize((screenWidth - themesWidth) / 2,
					(screenHeight - themesHeight) / 2, themesWidth, themesHeight);

			GuiLayoutState.SavedLayout saved = pendingLayout != null ? pendingLayout : layoutState.load();
			applySavedLayout(saved);
            layoutInitialized = true;
        }

        boolean resolutionChanged = screenWidth != lastScreenWidth || screenHeight != lastScreenHeight;
		boolean clamped = clampAllBounds();
        syncBounds();
        if (resolutionChanged && clamped && lastScreenWidth >= 0) {
            saveLayout();
        }
        lastScreenWidth = screenWidth;
        lastScreenHeight = screenHeight;

        bombX = 28;
        bombY = screenHeight - BOMB_SIZE - 28;
    }

    private void syncBounds() {
        panelX = mainBounds.x();
        panelY = mainBounds.y();
        panelWidth = mainBounds.width();
        panelHeight = mainBounds.height();
        friendsPanel.setBounds(friendsBounds.x(), friendsBounds.y(), friendsBounds.width(), friendsBounds.height());
		configPanel.setBounds(configBounds.x(), configBounds.y(), configBounds.width(), configBounds.height());
		configForm.setBounds(configFormBounds.x(), configFormBounds.y(), configFormBounds.width(), configFormBounds.height());
		themesPanel.setBounds(themesBounds.x(), themesBounds.y(), themesBounds.width(), themesBounds.height());
    }

	private void applySavedLayout(GuiLayoutState.SavedLayout saved) {
		if (saved == null) return;
		if (saved.main != null) saved.main.applyTo(mainBounds);
		if (saved.friends != null) saved.friends.applyTo(friendsBounds);
		if (saved.blockSelector != null) saved.blockSelector.applyTo(blockSelectorBounds);
		if (saved.mobSelector != null) saved.mobSelector.applyTo(mobSelectorBounds);
		if (saved.configs != null) saved.configs.applyTo(configBounds);
		if (saved.configForm != null) saved.configForm.applyTo(configFormBounds);
		if (saved.themes != null) saved.themes.applyTo(themesBounds);
	}

	private boolean clampAllBounds() {
		int screenWidth = mc.getWindow().getWidth();
		int screenHeight = mc.getWindow().getHeight();
		boolean changed = mainBounds.clampToScreen(screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
		changed |= friendsBounds.clampToScreen(screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
		changed |= blockSelectorBounds.clampToScreen(screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
		changed |= mobSelectorBounds.clampToScreen(screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
		changed |= configBounds.clampToScreen(screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
		changed |= configFormBounds.clampToScreen(screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
		changed |= themesBounds.clampToScreen(screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
		return changed;
	}

    public void saveLayout() {
        if (layoutInitialized) {
			layoutState.save(mainBounds, friendsBounds, blockSelectorBounds, mobSelectorBounds,
					configBounds, configFormBounds, themesBounds);
			ConfigManager.notifyChanged();
        }
    }

    private void renderShell(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        GhostorTheme.glowOutline(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight, GhostorTheme.RADIUS);
        GhostorTheme.panel(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                GhostorTheme.SURFACE_GLASS, GhostorTheme.RADIUS);
        GhostorTheme.outline(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                GhostorTheme.BORDER, GhostorTheme.RADIUS);
        context.fill(panelX + panelWidth / 2 - 14, panelY - 2,
                panelX + panelWidth / 2 + 14, panelY + 2, GhostorTheme.ACCENT.getRGB());

        GhostorTheme.panel(context, panelX, panelY, panelX + SIDEBAR_WIDTH, panelY + panelHeight,
                GhostorTheme.SIDEBAR, GhostorTheme.RADIUS);
        context.fill(panelX + SIDEBAR_WIDTH, panelY + 18, panelX + SIDEBAR_WIDTH + 1,
                panelY + panelHeight - 18, GhostorTheme.BORDER.getRGB());

        TextRenderer.drawString("GHOSTOR", context, panelX + 32, panelY + 32, GhostorTheme.TEXT.getRGB());
        TextRenderer.drawString("CLIENT", context, panelX + 32, panelY + 52, GhostorTheme.ACCENT.getRGB());
        TextRenderer.drawString(selectedCategory.name.toString().toUpperCase(Locale.ROOT), context,
                panelX + SIDEBAR_WIDTH + 22, panelY + 30, GhostorTheme.TEXT.getRGB());
        TextRenderer.drawString("Modules", context, panelX + SIDEBAR_WIDTH + 22, panelY + 51, GhostorTheme.TEXT_MUTED.getRGB());

        int searchWidth = Math.min(240, Math.max(160, panelWidth / 3));
        int searchX = panelX + panelWidth - searchWidth - 20;
        int searchY = panelY + 28;
        boolean searchHovered = hovered(mouseX, mouseY, searchX, searchY, searchX + searchWidth, searchY + 38);
        GhostorTheme.panel(context, searchX, searchY, searchX + searchWidth, searchY + 38,
                searchFocused || searchHovered ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 8);
        GhostorTheme.outline(context, searchX, searchY, searchX + searchWidth, searchY + 38,
                searchFocused ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 8);
        String searchLabel = search.isBlank() ? "Search modules..." : fitEndSmall(search, searchWidth - 57);
        TextRenderer.drawSmallString(searchLabel, context, searchX + 14, searchY + 15,
                search.isBlank() ? GhostorTheme.DISABLED.getRGB() : GhostorTheme.TEXT.getRGB());
        GhostorIcons.search(context, searchX + searchWidth - 27, searchY + 11,
                searchFocused ? GhostorTheme.ACCENT_HOVER : GhostorTheme.TEXT_MUTED);

        int categoryY = panelY + 96;
        for (Category category : Category.values()) {
            boolean selected = category == selectedCategory;
            boolean categoryHovered = hovered(mouseX, mouseY, panelX + 20, categoryY,
                    panelX + SIDEBAR_WIDTH - 20, categoryY + GhostorTheme.ROW);
            if (selected || categoryHovered) {
                GhostorTheme.panel(context, panelX + 20, categoryY, panelX + SIDEBAR_WIDTH - 20,
                        categoryY + GhostorTheme.ROW,
                        selected ? GhostorTheme.ACCENT_SOFT : GhostorTheme.SURFACE_HOVER, 8);
            }
            if (selected) {
                context.fill(panelX + 20, categoryY + 10, panelX + 23,
                        categoryY + GhostorTheme.ROW - 10, GhostorTheme.ACCENT.getRGB());
            }
            Color iconColor = selected ? GhostorTheme.ACCENT_HOVER : GhostorTheme.TEXT_MUTED;
            GhostorIcons.category(context, category, panelX + 34, categoryY + 14, iconColor);
            TextRenderer.drawString(category.name, context, panelX + 62, categoryY + 15,
                    (selected ? GhostorTheme.TEXT : GhostorTheme.TEXT_MUTED).getRGB());
            categoryY += GhostorTheme.ROW + 8;
        }

        int dividerY = categoryY + 10;
        context.fill(panelX + 24, dividerY, panelX + SIDEBAR_WIDTH - 24, dividerY + 1, GhostorTheme.BORDER.getRGB());
        friendButtonY = dividerY + 22;
        boolean friendHovered = hovered(mouseX, mouseY, panelX + 20, friendButtonY,
                panelX + SIDEBAR_WIDTH - 20, friendButtonY + GhostorTheme.ROW);
        if (friendsOpen || friendHovered) {
            GhostorTheme.panel(context, panelX + 20, friendButtonY, panelX + SIDEBAR_WIDTH - 20,
                    friendButtonY + GhostorTheme.ROW,
                    friendsOpen ? GhostorTheme.ACCENT_SOFT : GhostorTheme.SURFACE_HOVER, 8);
        }
        GhostorIcons.friends(context, panelX + 33, friendButtonY + 13,
                friendsOpen ? GhostorTheme.ACCENT_HOVER : GhostorTheme.TEXT_MUTED);
        TextRenderer.drawString("Friends", context, panelX + 62, friendButtonY + 15,
                (friendsOpen ? GhostorTheme.TEXT : GhostorTheme.TEXT_MUTED).getRGB());
		configButtonY = friendButtonY + GhostorTheme.ROW + 8;
		boolean configHovered = hovered(mouseX, mouseY, panelX + 20, configButtonY,
				panelX + SIDEBAR_WIDTH - 20, configButtonY + GhostorTheme.ROW);
		if (configsOpen || configHovered) {
			GhostorTheme.panel(context, panelX + 20, configButtonY, panelX + SIDEBAR_WIDTH - 20,
					configButtonY + GhostorTheme.ROW,
					configsOpen ? GhostorTheme.ACCENT_SOFT : GhostorTheme.SURFACE_HOVER, 8);
		}
		GhostorIcons.configs(context, panelX + 33, configButtonY + 13,
				configsOpen ? GhostorTheme.ACCENT_HOVER : GhostorTheme.TEXT_MUTED);
		TextRenderer.drawString("Configs", context, panelX + 62, configButtonY + 15,
				(configsOpen ? GhostorTheme.TEXT : GhostorTheme.TEXT_MUTED).getRGB());
		themesButtonY = configButtonY + GhostorTheme.ROW + 8;
		boolean themesHovered = hovered(mouseX, mouseY, panelX + 20, themesButtonY,
				panelX + SIDEBAR_WIDTH - 20, themesButtonY + GhostorTheme.ROW);
		if (themesOpen || themesHovered) {
			GhostorTheme.panel(context, panelX + 20, themesButtonY, panelX + SIDEBAR_WIDTH - 20,
					themesButtonY + GhostorTheme.ROW,
					themesOpen ? GhostorTheme.ACCENT_SOFT : GhostorTheme.SURFACE_HOVER, 8);
		}
		GhostorIcons.themes(context, panelX + 33, themesButtonY + 13,
				themesOpen ? GhostorTheme.ACCENT_HOVER : GhostorTheme.TEXT_MUTED);
		TextRenderer.drawString("Themes", context, panelX + 62, themesButtonY + 15,
				(themesOpen ? GhostorTheme.TEXT : GhostorTheme.TEXT_MUTED).getRGB());
    }

    private void renderBombButton(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        boolean hovered = hovered(mouseX, mouseY, bombX, bombY, bombX + BOMB_SIZE, bombY + BOMB_SIZE);
        GhostorTheme.glowOutline(context, bombX, bombY, bombX + BOMB_SIZE, bombY + BOMB_SIZE, 14);
        GhostorTheme.panel(context, bombX, bombY, bombX + BOMB_SIZE, bombY + BOMB_SIZE,
                hovered ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_GLASS, 14);
        GhostorTheme.outline(context, bombX, bombY, bombX + BOMB_SIZE, bombY + BOMB_SIZE,
                hovered ? GhostorTheme.ACCENT_HOVER : GhostorTheme.ACCENT_BORDER, 14);
        GhostorIcons.bomb(context, bombX + 13, bombY + 12, hovered ? GhostorTheme.ACCENT_HOVER : GhostorTheme.TEXT_MUTED);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.key();
		PanelLayer top = topOpenPanel();
		if (top == PanelLayer.THEMES) { themesPanel.keyPressed(keyCode); return true; }
		if (top == PanelLayer.CONFIG_FORM) { configForm.keyPressed(keyCode); return true; }
		if (top == PanelLayer.CONFIGS) { configPanel.keyPressed(keyCode); return true; }
		if (top == PanelLayer.BLOCK_SELECTOR) { blockSelector.keyPressed(keyInput); return true; }
		if (top == PanelLayer.MOB_SELECTOR) { mobSelector.keyPressed(keyInput); return true; }
		if (top == PanelLayer.FRIENDS) { friendsPanel.keyPressed(keyCode); return true; }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                int end = search.offsetByCodePoints(search.length(), -1);
                search = search.substring(0, end);
				refreshSearchMatches();
            }
            return true;
        }

        for (Window window : windows) {
            if (window.getCategory() == selectedCategory) {
                window.keyPressed(keyCode, keyInput.scancode(), keyInput.modifiers());
            }
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharacterEvent charInput) {
		PanelLayer top = topOpenPanel();
		if (top == PanelLayer.THEMES && charInput.isAllowedChatCharacter()) {
			themesPanel.charTyped(charInput.codepointAsString());
			return true;
		}
		if (top == PanelLayer.CONFIG_FORM && charInput.isAllowedChatCharacter()) {
			configForm.charTyped(charInput.codepointAsString());
			return true;
		}
		if (top == PanelLayer.CONFIGS) return true;
		if (top == PanelLayer.BLOCK_SELECTOR) { blockSelector.charTyped(charInput); return true; }
		if (top == PanelLayer.MOB_SELECTOR) { mobSelector.charTyped(charInput); return true; }
		if (top == PanelLayer.FRIENDS && charInput.isAllowedChatCharacter()
                && friendsPanel.charTyped(charInput.codepointAsString())) {
            return true;
        }
		if (top != PanelLayer.MAIN) return true;
        if (searchFocused && charInput.isAllowedChatCharacter() && search.length() < 64) {
            search += charInput.codepointAsString();
			refreshSearchMatches();
            return true;
        }
        return super.charTyped(charInput);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        double mouseX = click.x() * scale;
        double mouseY = click.y() * scale;
        int button = click.button();

		PanelLayer topPanel = topPanelAt(mouseX, mouseY);
		if (topPanel == null && hovered(mouseX, mouseY, bombX, bombY, bombX + BOMB_SIZE, bombY + BOMB_SIZE)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                SelfDestruct selfDestruct = Argon.INSTANCE.getModuleManager().getModule(SelfDestruct.class);
                if (selfDestruct != null && !SelfDestruct.destruct) {
                    selfDestruct.setEnabled(true);
                }
            }
            return true;
        }

		if (topPanel == null) {
            boolean layoutChanged = mainBounds.endInteraction();
            layoutChanged |= friendsBounds.endInteraction();
            if (blockSelector != null) layoutChanged |= blockSelector.loseFocus();
			if (mobSelector != null) layoutChanged |= mobSelector.loseFocus();
			layoutChanged |= configBounds.endInteraction();
			layoutChanged |= configFormBounds.endInteraction();
			layoutChanged |= themesBounds.endInteraction();
            if (layoutChanged) saveLayout();
            searchFocused = false;
			friendsPanel.clearFocus();
			themesPanel.clearFocus();
            return super.mouseClicked(click, doubled);
        }

        focusPanel(topPanel);
        return switch (topPanel) {
            case BLOCK_SELECTOR -> {
                blockSelector.mouseClicked(mouseX, mouseY, button);
                yield true;
            }
			case MOB_SELECTOR -> { mobSelector.mouseClicked(mouseX, mouseY, button); yield true; }
            case FRIENDS -> {
                handleFriendsClick(mouseX, mouseY, button);
                yield true;
            }
            case MAIN -> {
                handleMainClick(mouseX, mouseY, button);
                yield true;
            }
			case CONFIGS -> {
				handleConfigsClick(mouseX, mouseY, button);
				yield true;
			}
			case CONFIG_FORM -> {
				handleConfigFormClick(mouseX, mouseY, button);
				yield true;
			}
			case THEMES -> {
				handleThemesClick(mouseX, mouseY, button);
				yield true;
			}
		};
	}

	private void handleThemesClick(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& themesBounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE)) {
			themesPanel.clearFocus();
			themesBounds.beginResize(mouseX, mouseY);
			return;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& themesBounds.isOverHeader(mouseX, mouseY, GhostorTheme.HEADER_HEIGHT)) {
			themesPanel.clearFocus();
			themesBounds.beginDrag(mouseX, mouseY);
			return;
		}
		themesPanel.mouseClicked(mouseX, mouseY, button);
	}

	private void handleConfigsClick(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& configBounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE)) {
			configPanel.clearFocus();
			configBounds.beginResize(mouseX, mouseY);
			return;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& configBounds.isOverHeader(mouseX, mouseY, GhostorTheme.HEADER_HEIGHT)) {
			configPanel.clearFocus();
			configBounds.beginDrag(mouseX, mouseY);
			return;
		}
		configPanel.mouseClicked(mouseX, mouseY, button);
	}

	private void handleConfigFormClick(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& configFormBounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE)) {
			configForm.clearFocus();
			configFormBounds.beginResize(mouseX, mouseY);
			return;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& configFormBounds.isOverHeader(mouseX, mouseY, 68)) {
			configForm.clearFocus();
			configFormBounds.beginDrag(mouseX, mouseY);
			return;
		}
		configForm.mouseClicked(mouseX, mouseY, button);
	}

    private void handleFriendsClick(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && friendsBounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE)) {
            friendsPanel.clearFocus();
            friendsBounds.beginResize(mouseX, mouseY);
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && friendsBounds.isOverHeader(mouseX, mouseY, GhostorTheme.HEADER_HEIGHT)) {
            friendsPanel.clearFocus();
            friendsBounds.beginDrag(mouseX, mouseY);
            return;
        }
        friendsPanel.mouseClicked(mouseX, mouseY, button);
    }

    private void handleMainClick(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mainBounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE)) {
            searchFocused = false;
            mainBounds.beginResize(mouseX, mouseY);
            return;
        }

        int searchWidth = Math.min(240, Math.max(160, panelWidth / 3));
        int searchX = panelX + panelWidth - searchWidth - 20;
        int searchY = panelY + 28;
        if (hovered(mouseX, mouseY, searchX, searchY, searchX + searchWidth, searchY + 38)) {
            searchFocused = true;
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mainBounds.isOverHeader(mouseX, mouseY, GhostorTheme.HEADER_HEIGHT)) {
            searchFocused = false;
            mainBounds.beginDrag(mouseX, mouseY);
            return;
        }

        int categoryY = panelY + 96;
        for (Category category : Category.values()) {
            if (hovered(mouseX, mouseY, panelX + 20, categoryY,
                    panelX + SIDEBAR_WIDTH - 20, categoryY + GhostorTheme.ROW)) {
                selectedCategory = category;
                searchFocused = false;
                return;
            }
            categoryY += GhostorTheme.ROW + 8;
        }

        if (hovered(mouseX, mouseY, panelX + 20, friendButtonY,
                panelX + SIDEBAR_WIDTH - 20, friendButtonY + GhostorTheme.ROW)) {
            if (friendsOpen) closeFriendsPanel();
            else openFriendsPanel();
            return;
        }
		if (hovered(mouseX, mouseY, panelX + 20, configButtonY,
				panelX + SIDEBAR_WIDTH - 20, configButtonY + GhostorTheme.ROW)) {
			if (configsOpen) closeConfigsPanel();
			else openConfigsPanel();
			return;
		}
		if (hovered(mouseX, mouseY, panelX + 20, themesButtonY,
				panelX + SIDEBAR_WIDTH - 20, themesButtonY + GhostorTheme.ROW)) {
			if (themesOpen) closeThemesPanel();
			else openThemesPanel();
			return;
		}

        searchFocused = false;
        for (Window window : windows) {
            if (window.getCategory() == selectedCategory) {
                window.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        double mouseX = click.x() * scale;
        double mouseY = click.y() * scale;
        int screenWidth = mc.getWindow().getWidth();
        int screenHeight = mc.getWindow().getHeight();
		if (blockSelector != null && blockSelector.mouseDragged(mouseX, mouseY, click.button())) {
			return true;
		}
		if (mobSelector != null && mobSelector.mouseDragged(mouseX, mouseY, click.button())) return true;
		if (themesBounds.isInteracting()) {
			themesBounds.update(mouseX, mouseY, screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
			syncBounds();
			return true;
		}
		if (themesOpen && themesPanel.mouseDragged(mouseX, mouseY, click.button())) return true;
        if (friendsBounds.isInteracting()) {
            friendsBounds.update(mouseX, mouseY, screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
            syncBounds();
            return true;
        }
		if (configBounds.isInteracting()) {
			configBounds.update(mouseX, mouseY, screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
			syncBounds();
			return true;
		}
		if (configFormBounds.isInteracting()) {
			configFormBounds.update(mouseX, mouseY, screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
			syncBounds();
			return true;
		}
        if (mainBounds.isInteracting()) {
            mainBounds.update(mouseX, mouseY, screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
            syncBounds();
            return true;
        }
        for (Window window : windows) {
            if (window.getCategory() == selectedCategory) {
                window.mouseDragged(mouseX, mouseY, click.button(), deltaX * scale, deltaY * scale);
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        double physicalX = mouseX * scale;
        double physicalY = mouseY * scale;
        PanelLayer topPanel = topPanelAt(physicalX, physicalY);
		if (topPanel == null && hovered(physicalX, physicalY,
				bombX, bombY, bombX + BOMB_SIZE, bombY + BOMB_SIZE)) return true;
        if (topPanel == null) return true;

        switch (topPanel) {
            case BLOCK_SELECTOR -> blockSelector.mouseScrolled(physicalX, physicalY, verticalAmount);
			case MOB_SELECTOR -> mobSelector.mouseScrolled(physicalX, physicalY, verticalAmount);
            case FRIENDS -> friendsPanel.mouseScrolled(physicalX, physicalY, verticalAmount);
			case CONFIGS -> configPanel.mouseScrolled(physicalX, physicalY, verticalAmount);
			case CONFIG_FORM -> { }
			case THEMES -> themesPanel.mouseScrolled(physicalX, physicalY, verticalAmount);
            case MAIN -> {
                for (Window window : windows) {
                    if (window.getCategory() == selectedCategory) {
                        window.mouseScrolled(physicalX, physicalY, horizontalAmount, verticalAmount);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
		if (blockSelector != null && blockSelector.mouseReleased(click.button())) {
			return true;
		}
		if (mobSelector != null && mobSelector.mouseReleased(click.button())) return true;
		if (themesPanel.mouseReleased(click.button())) return true;
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        double mouseX = click.x() * scale;
        double mouseY = click.y() * scale;
		boolean layoutInteraction = friendsBounds.isInteracting() || mainBounds.isInteracting()
				|| configBounds.isInteracting() || configFormBounds.isInteracting() || themesBounds.isInteracting();
        boolean layoutChanged = friendsBounds.endInteraction();
        layoutChanged |= mainBounds.endInteraction();
		layoutChanged |= configBounds.endInteraction();
		layoutChanged |= configFormBounds.endInteraction();
		layoutChanged |= themesBounds.endInteraction();
        if (layoutChanged) {
            saveLayout();
        }
        if (layoutInteraction) {
            return true;
        }
        for (Window window : windows) {
            if (window.getCategory() == selectedCategory) {
                window.mouseReleased(mouseX, mouseY, click.button());
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Argon.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
        onGuiClose();
    }

    @Override
    public void removed() {
        super.removed();
        if (childScreenOpen) {
            return;
        }
        Argon.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
        Argon.INSTANCE.previousScreen = null;
        cleanupGuiState();
    }

    public void onGuiClose() {
        Screen returnScreen = Argon.INSTANCE.previousScreen;
        Argon.INSTANCE.previousScreen = null;
        cleanupGuiState();
        mc.gui.setScreen(returnScreen);
    }

    private void cleanupGuiState() {
        if (blockSelector != null) {
            blockSelector.onParentClosing();
            blockSelector = null;
        }
		if (mobSelector != null) { mobSelector.onParentClosing(); mobSelector = null; }
        mainBounds.endInteraction();
        friendsBounds.endInteraction();
        blockSelectorBounds.endInteraction();
		mobSelectorBounds.endInteraction();
		configBounds.endInteraction();
		configFormBounds.endInteraction();
		themesBounds.endInteraction();
        saveLayout();
        currentColor = null;
        friendsOpen = false;
		configsOpen = false;
		configFormOpen = false;
		themesOpen = false;
        searchFocused = false;
        friendsPanel.clearFocus();
		configPanel.clearFocus();
		configForm.clearFocus();
		themesPanel.clearFocus();
		themeParticles.reset();
        for (Window window : windows) {
            window.onGuiClose();
        }
    }

    private static boolean hovered(double mouseX, double mouseY, int x1, int y1, int x2, int y2) {
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    private static String fitEndSmall(String value, int maximumWidth) {
        if (TextRenderer.getSmallWidth(value) <= maximumWidth) {
            return value;
        }
        String result = value;
        while (!result.isEmpty() && TextRenderer.getSmallWidth("…" + result) > maximumWidth) {
            int firstCodePoint = result.offsetByCodePoints(0, 1);
            result = result.substring(firstCodePoint);
        }
        return "…" + result;
    }
}
