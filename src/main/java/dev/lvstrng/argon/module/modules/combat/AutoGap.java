package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.ItemUseListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.imixin.IKeyBinding;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.misc.AutoXP;
import dev.lvstrng.argon.module.modules.misc.Scaffold;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Automatically consumes a suitable golden apple and restores all borrowed state. */
public final class AutoGap extends Module implements TickListener, ItemUseListener {
    private static final List<Class<? extends Module>> INTERFERING_MODULES = List.of(
            AnchorMacro.class,
            AutoCrystal.class,
            AutoHitCrystal.class,
            TriggerBot.class,
            AutoPot.class,
            DoubleAnchor.class,
            Clutch.class,
            Scaffold.class,
            AutoXP.class
    );

    private final BooleanSetting allowEnchantedGap = new BooleanSetting(
            EncryptedString.of("Allow Enchanted Gap"), true);
    private final BooleanSetting alwaysEat = new BooleanSetting(
            EncryptedString.of("Always Eat"), false);
    private final BooleanSetting pauseCombatModules = new BooleanSetting(
            EncryptedString.of("Pause Combat Modules"), true)
            .setDescription(EncryptedString.of("Temporarily pauses modules that attack, place, or use items"));
    private final BooleanSetting beforeExpiry = new BooleanSetting(
            EncryptedString.of("Before Expiry"), false);
    private final NumberSetting expiryThreshold = new NumberSetting(
            EncryptedString.of("Expiry Threshold"), 0, 200, 60, 1)
            .visibleWhen(beforeExpiry::getValue);
    private final BooleanSetting regeneration = new BooleanSetting(
            EncryptedString.of("Regeneration"), false);
    private final BooleanSetting fireResistance = new BooleanSetting(
            EncryptedString.of("Fire Resistance"), true)
            .setDescription(EncryptedString.of("Requires an enchanted golden apple"));
    private final BooleanSetting absorption = new BooleanSetting(
            EncryptedString.of("Absorption"), false)
            .setDescription(EncryptedString.of("Requires an enchanted golden apple"));
    private final BooleanSetting healthEnabled = new BooleanSetting(
            EncryptedString.of("Health Enabled"), true);
    private final NumberSetting healthThreshold = new NumberSetting(
            EncryptedString.of("Health Threshold"), 0, 40, 20, 0.5)
            .visibleWhen(healthEnabled::getValue);

    private final List<Module> pausedModules = new ArrayList<>();
    private boolean requiresEnchantedGap;
    private boolean preparing;
    private boolean eating;
    private int appleSlot = -1;
    private int previousSlot = -1;
    private LocalPlayer eatingPlayer;

    public AutoGap() {
        super(EncryptedString.of("Auto Gap"),
                EncryptedString.of("Automatically eats golden apples when needed"),
                -1,
                Category.COMBAT);
        addSettings(allowEnchantedGap, alwaysEat, pauseCombatModules, beforeExpiry, expiryThreshold,
                regeneration, fireResistance, absorption, healthEnabled, healthThreshold);
    }

    @Override
    public void onEnable() {
        resetState();
        eventManager.add(TickListener.class, this, 1000);
        eventManager.add(ItemUseListener.class, this, 1000);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        stopEating();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null || !mc.player.isAlive()
                || mc.gui.screen() != null) {
            stopEating();
            return;
        }

        if ((eating || preparing) && eatingPlayer != mc.player) {
            stopEating();
            return;
        }

        if (eating || preparing) {
            enforcePausedModules();
            if (!shouldEat()) {
                stopEating();
                return;
            }

            int validSlot = isValidApple(appleSlot) ? appleSlot : findAppleSlot();
            if (validSlot < 0) {
                stopEating();
                return;
            }

            if (preparing) {
                beginUsing(validSlot);
                return;
            }

            appleSlot = validSlot;
            if (mc.player.getInventory().getSelectedSlot() != appleSlot) {
                InventoryUtils.setInvSlot(appleSlot);
            }
            mc.options.keyUse.setDown(true);
            if (!mc.player.isUsingItem()) useSelectedApple();
            return;
        }

        if (mc.player.isUsingItem() || !shouldEat()) return;

        int foundSlot = findAppleSlot();
        if (foundSlot < 0) return;

        eatingPlayer = mc.player;
        previousSlot = mc.player.getInventory().getSelectedSlot();
        appleSlot = foundSlot;

        if (pauseCombatModules.getValue() && pauseInterferingModules()) {
            // Paused listeners can still exist in the current event snapshot. Wait one
            // tick before using the apple so they cannot overwrite the selected slot.
            preparing = true;
            return;
        }

        beginUsing(foundSlot);
    }

    @Override
    public void onItemUse(ItemUseEvent event) {
        // AutoGap uses the direct item-use path. Suppress Minecraft's crosshair-based
        // repeat while the synthetic use key is held, avoiding block/entity interaction.
        if (eating) event.cancel();
    }

    public boolean isEating() {
        return isEnabled() && eating;
    }

    public static void onWorldChanged() {
        if (Argon.INSTANCE == null || Argon.INSTANCE.getModuleManager() == null) return;
        AutoGap autoGap = Argon.INSTANCE.getModuleManager().getModule(AutoGap.class);
        if (autoGap != null) autoGap.stopEating();
    }

    private void beginUsing(int slot) {
        preparing = false;
        eating = true;
        appleSlot = slot;
        InventoryUtils.setInvSlot(slot);
        mc.options.keyUse.setDown(true);
        useSelectedApple();
    }

    private void useSelectedApple() {
        InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (!result.consumesAction()) stopEating();
    }

    private boolean shouldEat() {
        requiresEnchantedGap = false;
        if (alwaysEat.getValue()) return true;

        boolean needsRegeneration = regeneration.getValue() && effectNeedsRefresh(MobEffects.REGENERATION);
        boolean needsFireResistance = fireResistance.getValue() && effectNeedsRefresh(MobEffects.FIRE_RESISTANCE);
        boolean needsAbsorption = absorption.getValue() && effectNeedsRefresh(MobEffects.ABSORPTION);
        if (needsFireResistance || needsAbsorption) requiresEnchantedGap = true;

        if (needsRegeneration || needsFireResistance || needsAbsorption) return true;
        return healthEnabled.getValue()
                && Math.round(mc.player.getHealth() + mc.player.getAbsorptionAmount()) < healthThreshold.getValue();
    }

    private boolean effectNeedsRefresh(Holder<MobEffect> effect) {
        MobEffectInstance instance = mc.player.getEffect(effect);
        return instance == null
                || beforeExpiry.getValue() && instance.getDuration() <= expiryThreshold.getValueInt();
    }

    private int findAppleSlot() {
        for (int index = 0; index < 9; index++) {
            ItemStack stack = mc.player.getInventory().getItem(index);
            if (stack.is(Items.ENCHANTED_GOLDEN_APPLE) && allowEnchantedGap.getValue()) return index;
            if (stack.is(Items.GOLDEN_APPLE) && !requiresEnchantedGap) return index;
        }
        return -1;
    }

    private boolean isValidApple(int slot) {
        if (slot < 0 || slot > 8) return false;
        ItemStack stack = mc.player.getInventory().getItem(slot);
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return allowEnchantedGap.getValue();
        return stack.is(Items.GOLDEN_APPLE) && !requiresEnchantedGap;
    }

    private boolean pauseInterferingModules() {
        for (Class<? extends Module> moduleClass : INTERFERING_MODULES) {
            Module module = Argon.INSTANCE.getModuleManager().getModule(moduleClass);
            if (module == null || !module.isEnabled()) continue;
            pausedModules.add(module);
            module.setEnabled(false);
        }
        return !pausedModules.isEmpty();
    }

    private void enforcePausedModules() {
        for (Module module : pausedModules) {
            if (module.isEnabled()) module.setEnabled(false);
        }
    }

    private void stopEating() {
        LocalPlayer player = mc.player;
        if (eating && player != null && player == eatingPlayer && player.isUsingItem() && mc.gameMode != null) {
            mc.gameMode.releaseUsingItem(player);
        }
        if (mc.options != null) ((IKeyBinding) mc.options.keyUse).resetPressed();

        if (player != null && player == eatingPlayer && previousSlot >= 0 && previousSlot < 9) {
            InventoryUtils.setInvSlot(previousSlot);
        }

        List<Module> modulesToRestore = new ArrayList<>(pausedModules);
        pausedModules.clear();
        for (Module module : modulesToRestore) {
            if (!module.isEnabled()) module.setEnabled(true);
        }
        resetState();
    }

    private void resetState() {
        requiresEnchantedGap = false;
        preparing = false;
        eating = false;
        appleSlot = -1;
        previousSlot = -1;
        eatingPlayer = null;
        pausedModules.clear();
    }
}
