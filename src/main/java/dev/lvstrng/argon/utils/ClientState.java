package dev.lvstrng.argon.utils;

import net.minecraft.client.Minecraft;

/** Shared null-safe checks for client actions that require an active world. */
public final class ClientState {
    private ClientState() {}

    public static boolean hasActiveWorld() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.level != null;
    }

    /** The ClickGUI key is intentionally accepted only from normal gameplay. */
    public static boolean canOpenClickGui() {
        Minecraft client = Minecraft.getInstance();
        return hasActiveWorld() && client.gui.screen() == null;
    }
}
