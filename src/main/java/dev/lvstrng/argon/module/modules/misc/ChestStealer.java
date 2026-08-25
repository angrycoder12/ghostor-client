package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Moves chest contents through a human-like randomized nearest-slot path. */
public final class ChestStealer extends Module implements TickListener {
    private final MinMaxSetting firstDelay = new MinMaxSetting(
            EncryptedString.of("Open delay"), 0, 1000, 1, 250, 450);
    private final MinMaxSetting delay = new MinMaxSetting(
            EncryptedString.of("Delay"), 0, 1000, 1, 150, 250);
    private final BooleanSetting autoClose = new BooleanSetting(EncryptedString.of("Auto Close"), false);
    private final MinMaxSetting closeDelay = new MinMaxSetting(
            EncryptedString.of("Close delay"), 0, 1000, 1, 150, 250);

    private final List<Integer> path = new ArrayList<>();
    private ChestMenu trackedMenu;
    private long nextActionAt;
    private long closeAt = -1;

    public ChestStealer() {
        super(EncryptedString.of("ChestStealer"), EncryptedString.of("Automatically steals items from chests with delay"), -1, Category.MISC);
        addSettings(firstDelay, delay, autoClose, closeDelay);
    }

    @Override
    public void onEnable() {
        reset();
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        reset();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.player.isDeadOrDying()
                || !(mc.gui.screen() instanceof ContainerScreen)
                || !(mc.player.containerMenu instanceof ChestMenu menu)) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (menu != trackedMenu) {
            trackedMenu = menu;
            generatePath(menu);
            nextActionAt = now + randomDelay(firstDelay);
            closeAt = -1;
            return;
        }

        path.removeIf(slot -> slot >= menu.getRowCount() * 9 || menu.getSlot(slot).getItem().isEmpty());
        if (!path.isEmpty()) {
            closeAt = -1;
            if (now >= nextActionAt && mc.gameMode != null) {
                int slot = path.remove(0);
                mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                nextActionAt = now + randomDelay(delay);
            }
            return;
        }

        if (!menu.getContainer().isEmpty()) {
            if (now >= nextActionAt) {
                generatePath(menu);
                nextActionAt = now + randomDelay(delay);
            }
            return;
        }

        if (!autoClose.getValue()) {
            return;
        }
        if (closeAt < 0) {
            closeAt = now + randomDelay(closeDelay);
        } else if (now >= closeAt) {
            mc.player.closeContainer();
            reset();
        }
    }

    private void generatePath(ChestMenu menu) {
        path.clear();
        List<Integer> remaining = new ArrayList<>();
        int chestSlots = menu.getRowCount() * 9;
        for (int slot = 0; slot < chestSlots; slot++) {
            if (!menu.getSlot(slot).getItem().isEmpty()) {
                remaining.add(slot);
            }
        }
        if (remaining.isEmpty()) {
            return;
        }

        Collections.shuffle(remaining, ThreadLocalRandom.current());
        int current = remaining.remove(0);
        path.add(current);
        while (!remaining.isEmpty()) {
            final int from = current;
            current = remaining.stream()
                    .min(Comparator.comparingInt(slot -> distance(from, slot)))
                    .orElseThrow();
            path.add(current);
            remaining.remove(Integer.valueOf(current));
        }
    }

    private static int distance(int first, int second) {
        return Math.abs(first % 9 - second % 9) + Math.abs(first / 9 - second / 9);
    }

    private static long randomDelay(MinMaxSetting setting) {
        long min = setting.getMinLong();
        long max = setting.getMaxLong();
        return max > min ? ThreadLocalRandom.current().nextLong(min, max + 1) : min;
    }

    private void reset() {
        trackedMenu = null;
        path.clear();
        nextActionAt = 0;
        closeAt = -1;
    }
}
