package dev.lvstrng.argon.gui.components;

import dev.lvstrng.argon.gui.GhostorTheme;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Offline-safe Minecraft-style face. It is intentionally deterministic per
 * username so the friends panel never needs an extra network request.
 */
public final class PlayerHeadIcon {
    private PlayerHeadIcon() {}

    public static void render(GuiGraphicsExtractor context, int x, int y, int size, String username) {
        int hash = username == null ? 0 : username.toLowerCase().hashCode();
        Color frame = new Color(63 + Math.floorMod(hash, 34), 47 + Math.floorMod(hash >> 5, 26), 92 + Math.floorMod(hash >> 9, 38));
        GhostorTheme.panel(context, x, y, x + size, y + size, frame, 4);
        GhostorTheme.outline(context, x, y, x + size, y + size, GhostorTheme.BORDER, 4);

        int inset = Math.max(3, size / 6);
        int faceLeft = x + inset;
        int faceTop = y + inset;
        int faceRight = x + size - inset;
        int faceBottom = y + size - inset;
        context.fill(faceLeft, faceTop, faceRight, faceBottom, new Color(193, 135, 92).getRGB());
        context.fill(faceLeft, faceTop, faceRight, faceTop + Math.max(2, size / 5), new Color(91, 58, 38).getRGB());

        int eyeY = faceTop + Math.max(4, size / 3);
        context.fill(faceLeft + 2, eyeY, faceLeft + 4, eyeY + 2, Color.WHITE.getRGB());
        context.fill(faceRight - 4, eyeY, faceRight - 2, eyeY + 2, Color.WHITE.getRGB());
        context.fill(faceLeft + 3, eyeY, faceLeft + 4, eyeY + 2, new Color(70, 101, 137).getRGB());
        context.fill(faceRight - 3, eyeY, faceRight - 2, eyeY + 2, new Color(70, 101, 137).getRGB());
        context.fill(x + size / 2 - 2, faceBottom - 4, x + size / 2 + 3, faceBottom - 2, new Color(112, 63, 54).getRGB());
    }
}
