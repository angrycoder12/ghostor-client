package dev.lvstrng.argon.gui.components;

import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.utils.RenderUtils;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Backend-neutral, pixel-aligned icons used by the ClickGUI. */
public final class GhostorIcons {
    private GhostorIcons() {}

    public static void moduleBadge(GuiGraphicsExtractor context, Category category, int x, int y) {
        GhostorTheme.panel(context, x, y, x + 24, y + 24, GhostorTheme.ACCENT_SOFT, 6);
        GhostorTheme.outline(context, x, y, x + 24, y + 24, GhostorTheme.ACCENT_BORDER, 6);
        category(context, category, x + 5, y + 5, GhostorTheme.ACCENT);
    }

    public static void category(GuiGraphicsExtractor context, Category category, int x, int y, Color color) {
        int c = color.getRGB();
        switch (category) {
            case COMBAT -> {
                diagonal(context, x + 2, y + 2, 11, 1, c);
                diagonal(context, x + 13, y + 2, 11, -1, c);
                context.fill(x + 1, y + 12, x + 6, y + 14, c);
                context.fill(x + 10, y + 12, x + 15, y + 14, c);
            }
            case MISC -> {
                context.outline(x + 2, y + 3, 12, 11, c);
                context.fill(x + 4, y + 1, x + 12, y + 3, c);
                context.fill(x + 7, y + 5, x + 9, y + 14, c);
            }
            case RENDER -> {
                context.outline(x + 1, y + 2, 14, 10, c);
                context.fill(x + 7, y + 12, x + 9, y + 15, c);
                context.fill(x + 4, y + 14, x + 12, y + 16, c);
            }
            case CLIENT -> {
                context.outline(x + 4, y + 4, 8, 8, c);
                context.fill(x + 7, y + 1, x + 9, y + 4, c);
                context.fill(x + 7, y + 12, x + 9, y + 15, c);
                context.fill(x + 1, y + 7, x + 4, y + 9, c);
                context.fill(x + 12, y + 7, x + 15, y + 9, c);
            }
			case DISABLED -> {
				context.outline(x + 2, y + 2, 12, 12, c);
				diagonal(context, x + 3, y + 3, 11, 1, c);
			}
        }
    }

    public static void search(GuiGraphicsExtractor context, int x, int y, Color color) {
        RenderUtils.renderCircle(context, color, x + 5, y + 5, 5, 16);
        RenderUtils.renderCircle(context, GhostorTheme.SURFACE_ELEVATED, x + 5, y + 5, 3, 16);
        diagonal(context, x + 9, y + 9, 5, 1, color.getRGB());
    }

    public static void friends(GuiGraphicsExtractor context, int x, int y, Color color) {
        int c = color.getRGB();
        RenderUtils.renderCircle(context, color, x + 6, y + 5, 3, 12);
        RenderUtils.renderCircle(context, color, x + 12, y + 6, 2.5, 12);
        GhostorTheme.outline(context, x + 1, y + 9, x + 11, y + 16, color, 4);
        GhostorTheme.outline(context, x + 8, y + 10, x + 16, y + 16, color, 3);
        context.fill(x + 4, y + 14, x + 14, y + 16, c);
    }

	public static void configs(GuiGraphicsExtractor context, int x, int y, Color color) {
		int c = color.getRGB();
		context.outline(x + 2, y + 2, 12, 13, c);
		context.fill(x + 5, y + 5, x + 12, y + 7, c);
		context.fill(x + 5, y + 9, x + 12, y + 11, c);
		context.fill(x + 5, y + 13, x + 10, y + 15, c);
	}

    public static void trash(GuiGraphicsExtractor context, int x, int y, Color color) {
        int c = color.getRGB();
        context.fill(x + 3, y + 4, x + 13, y + 6, c);
        context.fill(x + 6, y + 2, x + 10, y + 4, c);
        context.outline(x + 4, y + 6, 8, 9, c);
        context.fill(x + 7, y + 8, x + 8, y + 13, c);
        context.fill(x + 10, y + 8, x + 11, y + 13, c);
    }

    public static void bomb(GuiGraphicsExtractor context, int x, int y, Color color) {
        RenderUtils.renderCircle(context, new Color(35, 40, 54), x + 18, y + 23, 15, 24);
        RenderUtils.renderCircle(context, color, x + 14, y + 18, 4, 12);
        context.fill(x + 26, y + 6, x + 29, y + 13, color.getRGB());
        diagonal(context, x + 27, y + 5, 8, 1, color.getRGB());
        int spark = GhostorTheme.ACCENT_HOVER.getRGB();
        context.fill(x + 35, y + 3, x + 37, y + 10, spark);
        context.fill(x + 32, y + 6, x + 40, y + 8, spark);
    }

    private static void diagonal(GuiGraphicsExtractor context, int x, int y, int length, int direction, int color) {
        for (int i = 0; i < length; i++) {
            int px = direction > 0 ? x + i : x - i;
            context.fill(px, y + i, px + 2, y + i + 2, color);
        }
    }
}
