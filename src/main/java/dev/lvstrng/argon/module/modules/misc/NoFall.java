package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.PacketSendListener;
import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * Recreates the supplied NoFall behavior through Ghostor's module, setting,
 * tick, and packet systems. The individual modes intentionally retain their
 * reference thresholds and movement patterns.
 */
public final class NoFall extends Module implements PlayerTickListener, PacketSendListener {
    private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.SPOOF, Mode.class);
    private final BooleanSetting onlyOnDamage = new BooleanSetting(EncryptedString.of("Only On Damage"), false);
    private final BooleanSetting smartMode = new BooleanSetting(EncryptedString.of("Smart Mode"), true);
    private final BooleanSetting debugMode = new BooleanSetting(EncryptedString.of("Debug Mode"), false);
    private final NumberSetting fallDistance = new NumberSetting(EncryptedString.of("Fall Distance"), 1, 10, 2.5, .5);
    private final NumberSetting maxDamage = new NumberSetting(EncryptedString.of("Max Damage"), 1, 20, 4, 1);
    private final NumberSetting packetDelay = new NumberSetting(EncryptedString.of("Packet Delay"), 0, 500, 100, 10);

    private int spoofTicks;
    private double lastSpoofDistance;
    private boolean spoofing;
    private boolean sendingSpoofPacket;
    private long lastPacketAt;
    private long lastDebugAt;
    private LocalPlayer trackedPlayer;

    public NoFall() {
        super(EncryptedString.of("NoFall"), EncryptedString.of("Prevents fall damage; some modes may flag"), -1, Category.MISC);
        addSettings(mode, onlyOnDamage, smartMode, debugMode, fallDistance, maxDamage, packetDelay);
    }

    @Override
    public void onEnable() {
        resetRuntimeState();
        trackedPlayer = mc.player;
        eventManager.add(PlayerTickListener.class, this);
        eventManager.add(PacketSendListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PlayerTickListener.class, this);
        eventManager.remove(PacketSendListener.class, this);
        resetRuntimeState();
        trackedPlayer = null;
        super.onDisable();
    }

    @Override
    public void onPlayerTick() {
        if (mc.player == null || mc.level == null) {
            if (trackedPlayer != null) {
                resetRuntimeState();
                trackedPlayer = null;
            }
            return;
        }

        if (trackedPlayer != mc.player) {
            resetRuntimeState();
            trackedPlayer = mc.player;
        }

        if (mc.player.isDeadOrDying()) {
            resetRuntimeState();
            return;
        }

        if (mc.player.onGround()) {
            spoofing = false;
            spoofTicks = 0;
            lastSpoofDistance = 0;
        } else {
            switch (mode.getMode()) {
                case SPOOF -> spoof();
                case HYPIXEL_SPOOF -> hypixelSpoof();
                case VERUS -> verus();
                case PACKET -> packet();
                case MATRIX -> matrix();
                case SPARTAN -> spartan();
                case AAC -> aac();
                case VULCAN -> vulcan();
            }

            if (smartMode.getValue() && mc.player.getDeltaMovement().y < 0 && predictedDamage() > maxDamage.getValue()) {
                activateNoFall();
            }
        }

        debugInfo();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (sendingSpoofPacket || mc.player == null || mc.level == null || mc.player.isDeadOrDying()) return;
        if (mode.getMode() != Mode.PACKET || !(event.packet instanceof ServerboundMovePlayerPacket) || !shouldModifyPacket()) return;

        // The event cannot replace a packet in-place, so cancel the original
        // and send one guarded replacement. The guard prevents re-entry.
        event.cancel();
        sendGroundPacket(false);
    }

    private void spoof() {
        if (mc.player.fallDistance > fallDistance.getValue()
                && (!onlyOnDamage.getValue() || predictedDamage() > 0)) {
            mc.player.setOnGround(true);
        }
    }

    private void hypixelSpoof() {
        if (mc.player.fallDistance <= fallDistance.getValue()) return;
        if (spoofing) {
            mc.player.setOnGround(true);
            if (++spoofTicks >= 2) {
                spoofing = false;
                spoofTicks = 0;
                lastSpoofDistance = mc.player.fallDistance;
            }
        } else if (mc.player.fallDistance - lastSpoofDistance > 2) {
            spoofing = true;
        }
    }

    private void verus() {
        if (mc.player.fallDistance <= fallDistance.getValue()) return;
        if (spoofing) {
            mc.player.setOnGround(true);
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0, mc.player.getDeltaMovement().z);
            spoofing = false;
            lastSpoofDistance = mc.player.fallDistance;
        } else if (mc.player.fallDistance - lastSpoofDistance > 2) {
            spoofing = true;
        }
    }

    private void packet() {
        if (shouldModifyPacket()) sendGroundPacket(true);
    }

    private void matrix() {
        if (mc.player.fallDistance <= 3) return;
        if (!spoofing) {
            spoofing = true;
            setVerticalVelocity(Math.max(mc.player.getDeltaMovement().y, -.5));
        }
        if (mc.player.fallDistance > 5) {
            mc.player.setOnGround(true);
            spoofing = false;
            lastSpoofDistance = mc.player.fallDistance;
        }
    }

    private void spartan() {
        if (mc.player.fallDistance > 2.5 && mc.player.tickCount % 3 == 0) {
            mc.player.setOnGround(true);
        }
    }

    private void aac() {
        if (mc.player.fallDistance <= 3) return;
        spoofing = true;
        setVerticalVelocity(Math.max(mc.player.getDeltaMovement().y, -.3));
        if (mc.player.fallDistance > 8) {
            mc.player.setOnGround(true);
            spoofing = false;
        }
    }

    private void vulcan() {
        if (mc.player.fallDistance <= 2) return;
        spoofing = true;
        setVerticalVelocity(mc.player.getDeltaMovement().y * .8);
        if (mc.player.fallDistance - lastSpoofDistance > 3) {
            mc.player.setOnGround(true);
            spoofing = false;
            lastSpoofDistance = mc.player.fallDistance;
        }
    }

    private void activateNoFall() {
        switch (mode.getMode()) {
            case PACKET -> sendGroundPacket(false);
            case SPOOF, HYPIXEL_SPOOF, VERUS, MATRIX, SPARTAN, AAC, VULCAN -> mc.player.setOnGround(true);
        }
    }

    private void setVerticalVelocity(double y) {
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, y, mc.player.getDeltaMovement().z);
    }

    private boolean shouldModifyPacket() {
        return mc.player.fallDistance > fallDistance.getValue()
                && (!onlyOnDamage.getValue() || predictedDamage() > 0);
    }

    private void sendGroundPacket(boolean applyDelay) {
        if (mc.player == null || mc.getConnection() == null || sendingSpoofPacket) return;
        long now = System.currentTimeMillis();
        if (applyDelay && now - lastPacketAt < packetDelay.getValueLong()) return;

        lastPacketAt = now;
        sendingSpoofPacket = true;
        try {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));
        } finally {
            sendingSpoofPacket = false;
        }
    }

    private double predictedDamage() {
        if (mc.player == null || mc.player.onGround() || mc.player.fallDistance <= 3) return 0;
        double damage = (mc.player.fallDistance - 3) * .5;
        damage *= 1 - mc.player.getArmorValue() * .04;
        return Math.max(0, damage);
    }

    private void debugInfo() {
        if (!debugMode.getValue() || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastDebugAt < 250) return;
        lastDebugAt = now;
        String message = String.format(Locale.ROOT, "Fall: %.2f  Ground: %s  Y: %.3f  Spoof: %s",
                mc.player.fallDistance, mc.player.onGround(), mc.player.getDeltaMovement().y, spoofing);
        mc.player.sendOverlayMessage(Component.literal(message));
    }

    private void resetRuntimeState() {
        spoofTicks = 0;
        lastSpoofDistance = 0;
        spoofing = false;
        sendingSpoofPacket = false;
        lastPacketAt = 0;
        lastDebugAt = 0;
    }

    public enum Mode {
        SPOOF, HYPIXEL_SPOOF, VERUS, PACKET, MATRIX, SPARTAN, AAC, VULCAN
    }
}
