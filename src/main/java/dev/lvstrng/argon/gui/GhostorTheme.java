package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.gui.theme.ThemeDefinition;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small, allocation-free design token set shared by Ghostor screens.  Keeping
 * these values here prevents controls from slowly drifting into unrelated UI
 * styles as new settings are added.
 */
public final class GhostorTheme {
	public static Color BACKDROP = new Color(5, 7, 12, 172);
	public static Color SURFACE = new Color(12, 17, 25, 242);
	public static Color SURFACE_GLASS = new Color(14, 20, 30, 222);
	public static Color SIDEBAR = new Color(11, 16, 24, 230);
	public static Color SURFACE_ELEVATED = new Color(20, 27, 39, 245);
	public static Color SURFACE_HOVER = new Color(29, 37, 52, 250);
	public static Color ACTIVE_SURFACE = new Color(35, 30, 63, 245);
	public static Color BORDER = new Color(91, 106, 133, 105);
	public static Color TEXT = new Color(238, 242, 250, 255);
	public static Color TEXT_MUTED = new Color(157, 170, 191, 255);
	public static Color DISABLED = new Color(109, 120, 139, 255);
	public static Color DISABLED_TOGGLE = new Color(65, 76, 96, 255);
	public static Color ACCENT = new Color(116, 92, 255, 255);
	public static Color ACCENT_SECONDARY = new Color(145, 125, 255, 255);
	public static Color ACCENT_HOVER = new Color(145, 125, 255, 255);
	public static Color ACCENT_SOFT = new Color(116, 92, 255, 38);
	public static Color ACCENT_BORDER = new Color(133, 108, 255, 150);
	public static Color SUCCESS = new Color(82, 201, 142, 255);
	public static Color DANGER = new Color(241, 67, 78, 255);
	public static Color DANGER_HOVER = new Color(255, 92, 101, 255);
	public static int RADIUS = 14;
	public static int GLOW_INTENSITY = 70;
    public static final int GAP = 8;
    public static final int ROW = 44;
    public static final int HEADER_HEIGHT = 78;
    public static final int RESIZE_HANDLE = 18;
    public static final int PANEL_MARGIN = 16;
    public static final int MAIN_MIN_WIDTH = 680;
	public static final int MAIN_MIN_HEIGHT = 600;
    public static final int FRIENDS_MIN_WIDTH = 240;
    public static final int FRIENDS_MIN_HEIGHT = 300;
    public static final int BLOCK_SELECTOR_MIN_WIDTH = 640;
    public static final int BLOCK_SELECTOR_MIN_HEIGHT = 480;
	public static final int MOB_SELECTOR_MIN_WIDTH = 650;
	public static final int MOB_SELECTOR_MIN_HEIGHT = 500;
	public static final int CONFIG_MIN_WIDTH = 500;
	public static final int CONFIG_MIN_HEIGHT = 390;
	public static final int CONFIG_FORM_MIN_WIDTH = 380;
	public static final int CONFIG_FORM_MIN_HEIGHT = 330;
	public static final int THEMES_MIN_WIDTH = 700;
	public static final int THEMES_MIN_HEIGHT = 660;

    private GhostorTheme() {}

	public static void apply(ThemeDefinition theme) {
		if (theme == null) return;
		BACKDROP = color(theme.background);
		SURFACE = color(theme.panel);
		SURFACE_GLASS = color(theme.panel);
		SIDEBAR = shade(SURFACE_GLASS, -10, Math.min(255, SURFACE_GLASS.getAlpha() + 8));
		SURFACE_ELEVATED = shade(SURFACE_GLASS, 8, Math.min(255, SURFACE_GLASS.getAlpha() + 12));
		SURFACE_HOVER = shade(SURFACE_GLASS, 18, Math.min(255, SURFACE_GLASS.getAlpha() + 20));
		BORDER = color(theme.border);
		TEXT = color(theme.text);
		TEXT_MUTED = color(theme.secondaryText);
		DISABLED = color(theme.secondaryText);
		DISABLED_TOGGLE = color(theme.disabledToggle);
		ACCENT = color(theme.primary);
		ACCENT_SECONDARY = color(theme.secondary);
		ACCENT_HOVER = color(theme.secondary);
		ACCENT_SOFT = withAlpha(ACCENT, 38);
		ACCENT_BORDER = withAlpha(ACCENT_SECONDARY, 150);
		ACTIVE_SURFACE = mix(SURFACE_ELEVATED, ACCENT, 0.22F, Math.max(220, SURFACE_ELEVATED.getAlpha()));
		SUCCESS = color(theme.enabledToggle);
		DANGER = color(theme.danger);
		DANGER_HOVER = shade(DANGER, 22, DANGER.getAlpha());
		RADIUS = Math.max(0, Math.min(24, theme.cornerRadius));
		GLOW_INTENSITY = Math.max(0, Math.min(100, theme.glowIntensity));
	}

    public static void panel(GuiGraphicsExtractor context, double x, double y, double x2, double y2, Color color, double radius) {
        RenderUtils.renderRoundedQuad(context, color, x, y, x2, y2, radius, 16);
    }

    public static void outline(GuiGraphicsExtractor context, double x, double y, double x2, double y2, Color color, double radius) {
        RenderUtils.renderRoundedOutline(context, color, x, y, x2, y2, radius, radius, radius, radius, 1, 16);
    }

    public static void glowOutline(GuiGraphicsExtractor context, double x, double y, double x2, double y2, double radius) {
		int outerAlpha = Math.round(24.0F * GLOW_INTENSITY / 70.0F);
		int innerAlpha = Math.round(52.0F * GLOW_INTENSITY / 70.0F);
		if (GLOW_INTENSITY <= 0) return;
		RenderUtils.renderRoundedOutline(context, withAlpha(ACCENT, Math.min(255, outerAlpha)), x - 3, y - 3, x2 + 3, y2 + 3,
                radius + 3, radius + 3, radius + 3, radius + 3, 1, 16);
		RenderUtils.renderRoundedOutline(context, withAlpha(ACCENT_SECONDARY, Math.min(255, innerAlpha)), x - 1, y - 1, x2 + 1, y2 + 1,
                radius + 1, radius + 1, radius + 1, radius + 1, 1, 16);
    }

	public static void resizeHandle(GuiGraphicsExtractor context, int right, int bottom, boolean highlighted) {
        Color color = highlighted ? ACCENT_HOVER : ACCENT_BORDER;
		RenderUtils.renderCircle(context, color, right - 5.0D, bottom - 5.0D, 1.5D, 16);
		RenderUtils.renderCircle(context, color, right - 9.0D, bottom - 5.0D, 1.5D, 16);
		RenderUtils.renderCircle(context, color, right - 5.0D, bottom - 9.0D, 1.5D, 16);
		RenderUtils.renderCircle(context, color, right - 13.0D, bottom - 5.0D, 1.25D, 16);
		RenderUtils.renderCircle(context, color, right - 9.0D, bottom - 9.0D, 1.25D, 16);
		RenderUtils.renderCircle(context, color, right - 5.0D, bottom - 13.0D, 1.25D, 16);
    }

	private static Color color(int argb) {
		return new Color(argb, true);
	}

	private static Color withAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
	}

	private static Color shade(Color color, int amount, int alpha) {
		return new Color(clamp(color.getRed() + amount), clamp(color.getGreen() + amount),
				clamp(color.getBlue() + amount), clamp(alpha));
	}

	private static Color mix(Color first, Color second, float amount, int alpha) {
		float value = Math.max(0.0F, Math.min(1.0F, amount));
		return new Color(
				clamp(Math.round(first.getRed() * (1.0F - value) + second.getRed() * value)),
				clamp(Math.round(first.getGreen() * (1.0F - value) + second.getGreen() * value)),
				clamp(Math.round(first.getBlue() * (1.0F - value) + second.getBlue() * value)),
				clamp(alpha));
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
