package dev.lvstrng.argon.module.modules.blatant;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.misc.Freecam;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Explicit movement-speed module for the Blatent category. */
public final class Speed extends Module implements TickListener {
	public enum Mode {
		Vanilla,
		Strafe
	}

	private static final double VANILLA_SPRINT_SPEED = 0.2873;
	private static final Identifier VANILLA_SPEED_ID = Identifier.fromNamespaceAndPath("ghostor", "speed");
	private final ModeSetting<Mode> mode = new ModeSetting<>(
			EncryptedString.of("Mode"), Mode.Vanilla, Mode.class);
	private final NumberSetting speed = new NumberSetting(
			EncryptedString.of("Speed"), 1.0, 4.0, 1.35, 0.05);
	private final BooleanSetting autoJump = new BooleanSetting(
			EncryptedString.of("Auto Jump"), true)
			.visibleWhen(() -> mode.isMode(Mode.Strafe));
	private final BooleanSetting onlyOnGround = new BooleanSetting(
			EncryptedString.of("Only On Ground"), false);
	private final BooleanSetting sprint = new BooleanSetting(
			EncryptedString.of("Sprint"), true);

	private LocalPlayer modifiedPlayer;

	public Speed() {
		super(EncryptedString.of("Speed"),
				EncryptedString.of("Increases player movement speed"),
				-1,
				Category.BLATENT);
		addSettings(mode, speed, autoJump, onlyOnGround, sprint);
	}

	@Override
	public void onEnable() {
		modifiedPlayer = null;
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		removeVanillaModifier();
		super.onDisable();
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gui.screen() != null) {
			removeVanillaModifier();
			return;
		}
		trackPlayer(player);

		Fly fly = Argon.INSTANCE.getModuleManager().getModule(Fly.class);
		Freecam freecam = Argon.INSTANCE.getModuleManager().getModule(Freecam.class);
		if (player.isPassenger() || player.getAbilities().flying || player.isFallFlying()
				|| player.isSwimming() || player.isInWater() || player.isInLava() || player.onClimbable()
				|| fly != null && fly.isEnabled() || freecam != null && freecam.isEnabled()
				|| onlyOnGround.getValue() && !player.onGround()) {
			removeVanillaModifier();
			return;
		}

		Vec2 input = player.input.getMoveVector();
		if (input.lengthSquared() < 1.0E-4F) {
			removeVanillaModifier();
			return;
		}

		if (sprint.getValue()) player.setSprinting(true);
		if (mode.isMode(Mode.Vanilla)) {
			applyVanillaModifier(player);
			return;
		}

		removeVanillaModifier();
		// Avoid overwriting a fresh server knockback event. Velocity owns that short window.
		if (player.hurtTime > 0) return;
		if (autoJump.getValue() && player.onGround()) player.jumpFromGround();

		Vec2 normalized = input.normalized();
		double yaw = Math.toRadians(player.getYRot());
		double movementSpeed = VANILLA_SPRINT_SPEED * speed.getValue();
		double x = -Math.sin(yaw) * normalized.y + Math.cos(yaw) * normalized.x;
		double z = Math.cos(yaw) * normalized.y + Math.sin(yaw) * normalized.x;
		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(x * movementSpeed, velocity.y, z * movementSpeed);
	}

	private void trackPlayer(LocalPlayer player) {
		if (modifiedPlayer == player) return;
		removeVanillaModifier();
		modifiedPlayer = player;
	}

	private void applyVanillaModifier(LocalPlayer player) {
		AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (movementSpeed == null) return;
		movementSpeed.addOrUpdateTransientModifier(new AttributeModifier(
				VANILLA_SPEED_ID,
				Math.max(0.0D, speed.getValue() - 1.0D),
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private void removeVanillaModifier() {
		if (modifiedPlayer != null) {
			AttributeInstance movementSpeed = modifiedPlayer.getAttribute(Attributes.MOVEMENT_SPEED);
			if (movementSpeed != null) movementSpeed.removeModifier(VANILLA_SPEED_ID);
		}
		modifiedPlayer = null;
	}
}
