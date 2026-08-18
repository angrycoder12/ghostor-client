package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.utils.RenderUtils;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

/**
 * Small, allocation-free design token set shared by Ghostor screens.  Keeping
 * these values here prevents controls from slowly drifting into unrelated UI
 * styles as new settings are added.
 */
public final class GhostorTheme {
    public static final Color BACKDROP = new Color(9, 12, 18, 212);
    public static final Color SURFACE = new Color(17, 22, 31, 248);
    public static final Color SURFACE_ELEVATED = new Color(25, 31, 43, 255);
    public static final Color SURFACE_HOVER = new Color(34, 42, 57, 255);
    public static final Color BORDER = new Color(76, 89, 112, 105);
    public static final Color TEXT = new Color(238, 242, 250, 255);
    public static final Color TEXT_MUTED = new Color(157, 170, 191, 255);
    public static final Color DISABLED = new Color(109, 120, 139, 255);
    public static final Color ACCENT = new Color(116, 92, 255, 255);
    public static final Color ACCENT_HOVER = new Color(145, 125, 255, 255);
    public static final Color SUCCESS = new Color(82, 201, 142, 255);
    public static final int RADIUS = 7;
    public static final int GAP = 8;
    public static final int ROW = 34;

    private GhostorTheme() {}

    public static void panel(DrawContext context, double x, double y, double x2, double y2, Color color, double radius) {
        RenderUtils.renderRoundedQuad(context, color, x, y, x2, y2, radius, 16);
    }

    public static void outline(DrawContext context, double x, double y, double x2, double y2, Color color, double radius) {
        RenderUtils.renderRoundedOutline(context, color, x, y, x2, y2, radius, radius, radius, radius, 1, 16);
    }
}
