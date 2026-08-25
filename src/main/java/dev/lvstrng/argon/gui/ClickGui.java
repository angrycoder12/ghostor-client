package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.components.FriendsPanel;
import dev.lvstrng.argon.gui.components.GhostorIcons;
import dev.lvstrng.argon.gui.layout.GuiBounds;
import dev.lvstrng.argon.gui.layout.GuiLayoutState;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.modules.client.SelfDestruct;
import dev.lvstrng.argon.utils.ColorUtils;
import dev.lvstrng.argon.utils.ClientState;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final int SIDEBAR_WIDTH = 182;
    private static final int DEFAULT_PANEL_WIDTH = 900;
    private static final int DEFAULT_PANEL_HEIGHT = 710;
    private static final int FRIENDS_GAP = 12;
    private static final int BOMB_SIZE = 68;

    public final List<Window> windows = new ArrayList<>();
    public Color currentColor;
    private final FriendsPanel friendsPanel = new FriendsPanel(this::closeFriendsPanel);
    private Category selectedCategory = Category.COMBAT;
    private String search = "";
    private boolean searchFocused;
    private boolean friendsOpen;
    private boolean childScreenOpen;
    private final GuiBounds mainBounds = new GuiBounds(GhostorTheme.MAIN_MIN_WIDTH, GhostorTheme.MAIN_MIN_HEIGHT);
    private final GuiBounds friendsBounds = new GuiBounds(GhostorTheme.FRIENDS_MIN_WIDTH, GhostorTheme.FRIENDS_MIN_HEIGHT);
    private final GuiLayoutState layoutState = new GuiLayoutState();
    private boolean layoutInitialized;
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int friendButtonY;
    private int bombX;
    private int bombY;

    public ClickGui() {
        super(Component.empty());
        for (Category category : Category.values()) {
            windows.add(new Window(0, 0, 460, 46, category, this));
        }
    }

    public boolean isModuleVisible(dev.lvstrng.argon.module.Module module) {
        if (search.isBlank()) {
            return true;
        }
        String query = search.toLowerCase(Locale.ROOT);
        return module.getName().toString().toLowerCase(Locale.ROOT).contains(query)
                || (module.getDescription() != null && module.getDescription().toString().toLowerCase(Locale.ROOT).contains(query));
    }

    public Category getSelectedCategory() {
        return selectedCategory;
    }

    public boolean isDraggingAlready() {
        return mainBounds.isInteracting() || friendsBounds.isInteracting();
    }

    public boolean isTextInputFocused() {
        return searchFocused || (friendsOpen && friendsPanel.isInputFocused());
    }

    public void suspendForChildScreen() {
        childScreenOpen = true;
        searchFocused = false;
        friendsPanel.clearFocus();
    }

    public void resumeFromChildScreen() {
        childScreenOpen = false;
    }

    public void openFriendsPanel() {
        friendsOpen = true;
        searchFocused = false;
        if (layoutInitialized) {
            friendsBounds.clampToScreen(mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                    GhostorTheme.PANEL_MARGIN);
            syncBounds();
        }
    }

    public void closeFriendsPanel() {
        friendsOpen = false;
        friendsPanel.clearFocus();
        if (friendsBounds.endInteraction()) {
            saveLayout();
        }
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
        if (!ClientState.hasActiveWorld()) {
            minecraft.execute(this::onClose);
            return;
        }

        if (Argon.INSTANCE.previousScreen != null) {
            Argon.INSTANCE.previousScreen.extractRenderState(context, 0, 0, delta);
        }
        context.blurBeforeThisStratum();

        int targetAlpha = ClickGUI.background.getValue() ? 176 : 104;
        if (currentColor == null) {
            currentColor = new Color(5, 7, 12, targetAlpha);
        } else {
            currentColor = new Color(5, 7, 12, currentColor.getAlpha());
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
        renderShell(context, physicalMouseX, physicalMouseY);

        for (Window window : windows) {
            if (window.getCategory() != selectedCategory) {
                continue;
            }
            window.setBounds(panelX + SIDEBAR_WIDTH + 22, panelY + 88,
                    panelWidth - SIDEBAR_WIDTH - 44, panelHeight - 108);
            window.render(context, physicalMouseX, physicalMouseY, delta);
        }

        boolean mainResizeHovered = mainBounds.isOverResizeHandle(
                physicalMouseX, physicalMouseY, GhostorTheme.RESIZE_HANDLE);
        GhostorTheme.resizeHandle(context, mainBounds.right(), mainBounds.bottom(),
                mainResizeHovered || mainBounds.isResizing());

        if (friendsOpen) {
            friendsPanel.render(context, physicalMouseX, physicalMouseY);
            boolean friendsResizeHovered = friendsBounds.isOverResizeHandle(
                    physicalMouseX, physicalMouseY, GhostorTheme.RESIZE_HANDLE);
            GhostorTheme.resizeHandle(context, friendsBounds.right(), friendsBounds.bottom(),
                    friendsResizeHovered || friendsBounds.isResizing());
        }
        renderBombButton(context, physicalMouseX, physicalMouseY);
        RenderUtils.scaledProjection(context);
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

            GuiLayoutState.SavedLayout saved = layoutState.load();
            if (saved.main != null) {
                saved.main.applyTo(mainBounds);
            }
            if (saved.friends != null) {
                saved.friends.applyTo(friendsBounds);
            }
            layoutInitialized = true;
        }

        boolean resolutionChanged = screenWidth != lastScreenWidth || screenHeight != lastScreenHeight;
        boolean clamped = mainBounds.clampToScreen(screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
        clamped |= friendsBounds.clampToScreen(screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
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
    }

    private void saveLayout() {
        if (layoutInitialized) {
            layoutState.save(mainBounds, friendsBounds);
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
        if (friendsOpen && friendsPanel.keyPressed(keyCode)) {
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                int end = search.offsetByCodePoints(search.length(), -1);
                search = search.substring(0, end);
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
        if (friendsOpen && charInput.isAllowedChatCharacter()
                && friendsPanel.charTyped(charInput.codepointAsString())) {
            return true;
        }
        if (searchFocused && charInput.isAllowedChatCharacter() && search.length() < 64) {
            search += charInput.codepointAsString();
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

        if (hovered(mouseX, mouseY, bombX, bombY, bombX + BOMB_SIZE, bombY + BOMB_SIZE)
                && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            SelfDestruct selfDestruct = Argon.INSTANCE.getModuleManager().getModule(SelfDestruct.class);
            if (selfDestruct != null && !SelfDestruct.destruct) {
                selfDestruct.setEnabled(true);
            }
            return true;
        }

        if (friendsOpen && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && friendsBounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE)) {
            if (mainBounds.endInteraction()) {
                saveLayout();
            }
            friendsPanel.clearFocus();
            searchFocused = false;
            friendsBounds.beginResize(mouseX, mouseY);
            return true;
        }
        if (friendsOpen && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && friendsBounds.isOverHeader(mouseX, mouseY, GhostorTheme.HEADER_HEIGHT)) {
            if (mainBounds.endInteraction()) {
                saveLayout();
            }
            friendsPanel.clearFocus();
            searchFocused = false;
            friendsBounds.beginDrag(mouseX, mouseY);
            return true;
        }
        if (friendsOpen && friendsPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mainBounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE)) {
            if (friendsBounds.endInteraction()) {
                saveLayout();
            }
            searchFocused = false;
            friendsPanel.clearFocus();
            mainBounds.beginResize(mouseX, mouseY);
            return true;
        }

        int searchWidth = Math.min(240, Math.max(160, panelWidth / 3));
        int searchX = panelX + panelWidth - searchWidth - 20;
        int searchY = panelY + 28;
        if (hovered(mouseX, mouseY, searchX, searchY, searchX + searchWidth, searchY + 38)) {
            searchFocused = true;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mainBounds.isOverHeader(mouseX, mouseY, GhostorTheme.HEADER_HEIGHT)) {
            if (friendsBounds.endInteraction()) {
                saveLayout();
            }
            searchFocused = false;
            friendsPanel.clearFocus();
            mainBounds.beginDrag(mouseX, mouseY);
            return true;
        }

        int categoryY = panelY + 96;
        for (Category category : Category.values()) {
            if (hovered(mouseX, mouseY, panelX + 20, categoryY,
                    panelX + SIDEBAR_WIDTH - 20, categoryY + GhostorTheme.ROW)) {
                selectedCategory = category;
                searchFocused = false;
                return true;
            }
            categoryY += GhostorTheme.ROW + 8;
        }

        if (hovered(mouseX, mouseY, panelX + 20, friendButtonY,
                panelX + SIDEBAR_WIDTH - 20, friendButtonY + GhostorTheme.ROW)) {
            if (friendsOpen) {
                closeFriendsPanel();
            } else {
                openFriendsPanel();
            }
            return true;
        }

        searchFocused = false;
        for (Window window : windows) {
            if (window.getCategory() == selectedCategory) {
                window.mouseClicked(mouseX, mouseY, button);
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        double mouseX = click.x() * scale;
        double mouseY = click.y() * scale;
        int screenWidth = mc.getWindow().getWidth();
        int screenHeight = mc.getWindow().getHeight();
        if (friendsBounds.isInteracting()) {
            friendsBounds.update(mouseX, mouseY, screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
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
        if (friendsOpen && friendsPanel.mouseScrolled(physicalX, physicalY, verticalAmount)) {
            return true;
        }
        for (Window window : windows) {
            if (window.getCategory() == selectedCategory) {
                window.mouseScrolled(physicalX, physicalY, horizontalAmount, verticalAmount);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        double mouseX = click.x() * scale;
        double mouseY = click.y() * scale;
        boolean layoutInteraction = friendsBounds.isInteracting() || mainBounds.isInteracting();
        boolean layoutChanged = friendsBounds.endInteraction();
        layoutChanged |= mainBounds.endInteraction();
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
        mainBounds.endInteraction();
        friendsBounds.endInteraction();
        saveLayout();
        currentColor = null;
        friendsOpen = false;
        searchFocused = false;
        friendsPanel.clearFocus();
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
