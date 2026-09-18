package dev.lvstrng.argon.gui.components;

import dev.lvstrng.argon.gui.GhostorTheme;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Renders a friend's real Minecraft face without doing profile or skin work on
 * the render thread. Online players use their already-loaded tab-list skin;
 * everyone else goes through Minecraft's asynchronous, expiring profile/skin
 * cache and displays the vanilla default face until that lookup completes.
 */
public final class PlayerHeadIcon {
    private static final int MAX_PROFILE_KEYS = 128;
    private static final Map<String, ResolvableProfile> PROFILE_KEYS = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResolvableProfile> eldest) {
            return size() > MAX_PROFILE_KEYS;
        }
    };

    private PlayerHeadIcon() {}

    public static void render(GuiGraphicsExtractor context, int x, int y, int size, String username) {
        String trimmedName = username == null ? "" : username.trim();
        String key = trimmedName.toLowerCase(Locale.ROOT);
        int hash = key.hashCode();
        Color frame = new Color(63 + Math.floorMod(hash, 34), 47 + Math.floorMod(hash >> 5, 26), 92 + Math.floorMod(hash >> 9, 38));
        GhostorTheme.panel(context, x, y, x + size, y + size, frame, 4);
        GhostorTheme.outline(context, x, y, x + size, y + size, GhostorTheme.BORDER, 4);

        int inset = Math.max(3, size / 7);
        int faceSize = Math.max(1, size - inset * 2);
        if (trimmedName.isEmpty()) {
            PlayerFaceExtractor.extractRenderState(context, DefaultPlayerSkin.getDefaultSkin(),
                    x + inset, y + inset, faceSize);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        PlayerInfo onlinePlayer = connection == null ? null : connection.getPlayerInfoIgnoreCase(trimmedName);
        if (onlinePlayer != null) {
            PlayerFaceExtractor.extractRenderState(context, onlinePlayer.getSkin(),
                    x + inset, y + inset, faceSize);
            return;
        }

        ResolvableProfile profile;
        synchronized (PROFILE_KEYS) {
            profile = PROFILE_KEYS.computeIfAbsent(key, ignored -> ResolvableProfile.createUnresolved(trimmedName));
        }
        PlayerFaceExtractor.extractRenderState(context, profile, x + inset, y + inset, faceSize);
    }
}
