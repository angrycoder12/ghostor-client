package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.utils.RenderUtils;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small, allocation-free design token set shared by Ghostor screens.  Keeping
 * these values here prevents controls from slowly drifting into unrelated UI
 * styles as new settings are added.
 */
public final class GhostorTheme {
    public static final Color BACKDROP = new Color(5, 7, 12, 172);
    public static final Color SURFACE = new Color(12, 17, 25, 242);
    public static final Color SURFACE_GLASS = new Color(14, 20, 30, 222);
    public static final Color SIDEBAR = new Color(11, 16, 24, 230);
    public static final Color SURFACE_ELEVATED = new Color(20, 27, 39, 245);
    public static final Color SURFACE_HOVER = new Color(29, 37, 52, 250);
    public static final Color BORDER = new Color(91, 106, 133, 105);
    public static final Color TEXT = new Color(238, 242, 250, 255);
    public static final Color TEXT_MUTED = new Color(157, 170, 191, 255);
    public static final Color DISABLED = new Color(109, 120, 139, 255);
    public static final Color ACCENT = new Color(116, 92, 255, 255);
    public static final Color ACCENT_HOVER = new Color(145, 125, 255, 255);
    public static final Color ACCENT_SOFT = new Color(116, 92, 255, 38);
    public static final Color ACCENT_BORDER = new Color(133, 108, 255, 150);
    public static final Color SUCCESS = new Color(82, 201, 142, 255);
    public static final Color DANGER = new Color(241, 67, 78, 255);
    public static final Color DANGER_HOVER = new Color(255, 92, 101, 255);
    public static final int RADIUS = 14;
    public static final int GAP = 8;
    public static final int ROW = 44;
    public static final int HEADER_HEIGHT = 78;
    public static final int RESIZE_HANDLE = 18;
    public static final int PANEL_MARGIN = 16;
    public static final int MAIN_MIN_WIDTH = 680;
	public static final int MAIN_MIN_HEIGHT = 540;
    public static final int FRIENDS_MIN_WIDTH = 240;
    public static final int FRIENDS_MIN_HEIGHT = 300;
    public static final int BLOCK_SELECTOR_MIN_WIDTH = 640;
    public static final int BLOCK_SELECTOR_MIN_HEIGHT = 480;
	public static final int CONFIG_MIN_WIDTH = 500;
	public static final int CONFIG_MIN_HEIGHT = 390;
	public static final int CONFIG_FORM_MIN_WIDTH = 380;
	public static final int CONFIG_FORM_MIN_HEIGHT = 330;

    private GhostorTheme() {}

    public static void panel(GuiGraphicsExtractor context, double x, double y, double x2, double y2, Color color, double radius) {
        RenderUtils.renderRoundedQuad(context, color, x, y, x2, y2, radius, 16);
    }

    public static void outline(GuiGraphicsExtractor context, double x, double y, double x2, double y2, Color color, double radius) {
        RenderUtils.renderRoundedOutline(context, color, x, y, x2, y2, radius, radius, radius, radius, 1, 16);
    }

    public static void glowOutline(GuiGraphicsExtractor context, double x, double y, double x2, double y2, double radius) {
        RenderUtils.renderRoundedOutline(context, new Color(116, 92, 255, 24), x - 3, y - 3, x2 + 3, y2 + 3,
                radius + 3, radius + 3, radius + 3, radius + 3, 1, 16);
        RenderUtils.renderRoundedOutline(context, new Color(116, 92, 255, 52), x - 1, y - 1, x2 + 1, y2 + 1,
                radius + 1, radius + 1, radius + 1, radius + 1, 1, 16);
    }

    public static void resizeHandle(GuiGraphicsExtractor context, int right, int bottom, boolean highlighted) {
        Color color = highlighted ? ACCENT_HOVER : ACCENT_BORDER;
        context.fill(right - 15, bottom - 5, right - 5, bottom - 3, color.getRGB());
        context.fill(right - 5, bottom - 15, right - 3, bottom - 3, color.getRGB());
        context.fill(right - 11, bottom - 9, right - 5, bottom - 7, color.getRGB());
        context.fill(right - 9, bottom - 11, right - 7, bottom - 7, color.getRGB());
    }
}
