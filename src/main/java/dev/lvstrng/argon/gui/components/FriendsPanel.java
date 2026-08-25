package dev.lvstrng.argon.gui.components;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.managers.FriendManager;
import dev.lvstrng.argon.utils.TextRenderer;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/** Compact friend editor embedded beside the main ClickGUI. */
public final class FriendsPanel {
    public static final int PREFERRED_WIDTH = 252;
    public static final int PREFERRED_HEIGHT = 414;
    private static final int ROW_HEIGHT = 44;

    private final Runnable closeAction;
    private String input = "";
    private boolean inputFocused;
    private int scroll;
    private int x;
    private int y;
    private int width = PREFERRED_WIDTH;
    private int height = PREFERRED_HEIGHT;

    public FriendsPanel(Runnable closeAction) {
        this.closeAction = closeAction;
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

        TextRenderer.drawString("Manage Friends", context, x + 20, y + 32, GhostorTheme.TEXT.getRGB());
        TextRenderer.drawSmallString("Names are saved between sessions", context, x + 20, y + 55, GhostorTheme.TEXT_MUTED.getRGB());

        int inputY = y + 108;
        int addWidth = 52;
        int addX = x + width - 18 - addWidth;
        int inputRight = addX - 8;
        Color inputColor = inputFocused ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED;
        GhostorTheme.panel(context, x + 18, inputY, inputRight, inputY + 40, inputColor, 7);
        GhostorTheme.outline(context, x + 18, inputY, inputRight, inputY + 40,
                inputFocused ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 7);
        String inputLabel = input.isEmpty() ? "Enter player name..." : fitEndSmall(input, inputRight - x - 35);
        TextRenderer.drawSmallString(inputLabel, context, x + 29, inputY + 16,
                (input.isEmpty() ? GhostorTheme.DISABLED : GhostorTheme.TEXT).getRGB());
        drawButton(context, addX, inputY, addX + addWidth, inputY + 40, "Add",
                hovered(mouseX, mouseY, addX, inputY, addX + addWidth, inputY + 40));

        List<String> friends = manager().getFriends();
        int listTop = inputY + 66;
        int listBottom = y + height - 73;
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, friends.size() - visibleRows)));
        context.enableScissor(x + 12, listTop, x + width - 12, listBottom);
        if (friends.isEmpty()) {
            String emptyLabel = "No friends added";
            TextRenderer.drawSmallString(emptyLabel, context, x + (width - TextRenderer.getSmallWidth(emptyLabel)) / 2,
                    listTop + 25, GhostorTheme.TEXT_MUTED.getRGB());
        } else {
            for (int row = 0; row < visibleRows && row + scroll < friends.size(); row++) {
                String name = friends.get(row + scroll);
                int rowY = listTop + row * ROW_HEIGHT;
                boolean rowHovered = hovered(mouseX, mouseY, x + 18, rowY, x + width - 18, rowY + ROW_HEIGHT - 4);
                GhostorTheme.panel(context, x + 18, rowY, x + width - 18, rowY + ROW_HEIGHT - 4,
                        rowHovered ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 7);
                GhostorTheme.outline(context, x + 18, rowY, x + width - 18, rowY + ROW_HEIGHT - 4, GhostorTheme.BORDER, 7);
                PlayerHeadIcon.render(context, x + 24, rowY + 6, 28, name);
                int removeX = x + width - 53;
                String visibleName = fitStart(name, removeX - x - 69);
                TextRenderer.drawString(visibleName, context, x + 61, rowY + 14, GhostorTheme.TEXT.getRGB());

                boolean removeHovered = hovered(mouseX, mouseY, removeX, rowY + 6, removeX + 28, rowY + 34);
                GhostorTheme.panel(context, removeX, rowY + 6, removeX + 28, rowY + 34,
                        removeHovered ? GhostorTheme.DANGER_HOVER : GhostorTheme.DANGER, 6);
                GhostorIcons.trash(context, removeX + 6, rowY + 12, Color.WHITE);
            }
        }
        context.disableScissor();

        int footerY = y + height - 55;
        drawSecondaryButton(context, x + 18, footerY, x + 80, footerY + 36, "Back",
                hovered(mouseX, mouseY, x + 18, footerY, x + 80, footerY + 36));
        String count = friends.size() + (friends.size() == 1 ? " friend" : " friends");
        TextRenderer.drawSmallString(count, context, x + width - 18 - TextRenderer.getSmallWidth(count), footerY + 15,
                GhostorTheme.TEXT_MUTED.getRGB());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            inputFocused = false;
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }

        int inputY = y + 108;
        int addWidth = 52;
        int addX = x + width - 18 - addWidth;
        int inputRight = addX - 8;
        if (hovered(mouseX, mouseY, x + 18, inputY, inputRight, inputY + 40)) {
            inputFocused = true;
            return true;
        }
        if (hovered(mouseX, mouseY, addX, inputY, addX + addWidth, inputY + 40)) {
            addInput();
            return true;
        }

        List<String> friends = manager().getFriends();
        int listTop = inputY + 66;
        int listBottom = y + height - 73;
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        for (int row = 0; row < visibleRows && row + scroll < friends.size(); row++) {
            int rowY = listTop + row * ROW_HEIGHT;
            int removeX = x + width - 53;
            if (hovered(mouseX, mouseY, removeX, rowY + 6, removeX + 28, rowY + 34)) {
                manager().removeFriend(friends.get(row + scroll));
                return true;
            }
        }

        int footerY = y + height - 55;
        if (hovered(mouseX, mouseY, x + 18, footerY, x + 80, footerY + 36)) {
            closeAction.run();
            return true;
        }
        inputFocused = false;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        scroll = Math.max(0, scroll + (verticalAmount < 0 ? 1 : verticalAmount > 0 ? -1 : 0));
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeAction.run();
            return true;
        }
        if (!inputFocused) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            addInput();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !input.isEmpty()) {
            int end = input.offsetByCodePoints(input.length(), -1);
            input = input.substring(0, end);
            return true;
        }
        return true;
    }

    public boolean charTyped(String characters) {
        if (!inputFocused || input.length() >= 32) {
            return false;
        }
        input += characters;
        return true;
    }

    public boolean contains(double mouseX, double mouseY) {
        return hovered(mouseX, mouseY, x, y, x + width, y + height);
    }

    public void clearFocus() {
        inputFocused = false;
    }

    public boolean isInputFocused() {
        return inputFocused;
    }

    private void addInput() {
        if (manager().addFriend(input)) {
            input = "";
            scroll = 0;
        }
    }

    private FriendManager manager() {
        return Argon.INSTANCE.getFriendManager();
    }

    private static void drawButton(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, String label, boolean hovered) {
        GhostorTheme.panel(context, x1, y1, x2, y2, hovered ? GhostorTheme.ACCENT_HOVER : GhostorTheme.ACCENT, 7);
        TextRenderer.drawCenteredString(label, context, (x1 + x2) / 2, y1 + 14, GhostorTheme.TEXT.getRGB());
    }

    private static void drawSecondaryButton(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, String label, boolean hovered) {
        GhostorTheme.panel(context, x1, y1, x2, y2, hovered ? GhostorTheme.SURFACE_HOVER : GhostorTheme.ACCENT_SOFT, 7);
        GhostorTheme.outline(context, x1, y1, x2, y2, hovered ? GhostorTheme.ACCENT : GhostorTheme.ACCENT_BORDER, 7);
        TextRenderer.drawCenteredString(label, context, (x1 + x2) / 2, y1 + 13, GhostorTheme.TEXT.getRGB());
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
            result = result.substring(result.offsetByCodePoints(0, 1));
        }
        return "…" + result;
    }

    private static String fitStart(String value, int maximumWidth) {
        if (TextRenderer.getWidth(value) <= maximumWidth) {
            return value;
        }
        String result = value;
        while (!result.isEmpty() && TextRenderer.getWidth(result + "…") > maximumWidth) {
            result = result.substring(0, result.offsetByCodePoints(result.length(), -1));
        }
        return result + "…";
    }
}
