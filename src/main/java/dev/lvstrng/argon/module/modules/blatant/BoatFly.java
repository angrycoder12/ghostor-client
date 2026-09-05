package dev.lvstrng.argon.module.modules.blatant;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

/** Lets the locally controlled boat fly using the player's normal movement keys. */
public final class BoatFly extends Module implements TickListener {
	private static BoatFly instance;

	private final NumberSetting horizontalSpeed = new NumberSetting(
			EncryptedString.of("Horizontal Speed"), 0.1, 5.0, 1.0, 0.05);
	private final NumberSetting verticalSpeed = new NumberSetting(
			EncryptedString.of("Vertical Speed"), 0.1, 3.0, 0.5, 0.05);
	private final BooleanSetting hover = new BooleanSetting(
			EncryptedString.of("Hover"), true)
			.setDescription(EncryptedString.of("Stops the boat from falling when no vertical key is held"));

	private AbstractBoat controlledBoat;
	private boolean previousNoGravity;
	private Object observedLevel;

	public BoatFly() {
		super(EncryptedString.of("Boat Fly"),
				EncryptedString.of("Allows controlled boats to fly"),
				-1,
				Category.BLATENT);
		addSettings(horizontalSpeed, verticalSpeed, hover);
		instance = this;
	}

	@Override
	public void onEnable() {
		observedLevel = mc.level;
		eventManager.add(TickListener.class, this, -500);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		releaseBoat();
		observedLevel = null;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || observedLevel != mc.level) {
			releaseBoat();
			observedLevel = mc.level;
			return;
		}

		Entity vehicle = mc.player.getVehicle();
		if (!(vehicle instanceof AbstractBoat boat) || !boat.isLocalInstanceAuthoritative()) {
			releaseBoat();
			return;
		}

		if (controlledBoat != boat) {
			releaseBoat();
			controlledBoat = boat;
			previousNoGravity = boat.isNoGravity();
		}

		boolean acceptInput = mc.gui.screen() == null;
		double forward = acceptInput
				? keyDown(mc.options.keyUp) - keyDown(mc.options.keyDown)
				: 0.0;
		double right = acceptInput
				? keyDown(mc.options.keyRight) - keyDown(mc.options.keyLeft)
				: 0.0;
		Vec3 horizontal = horizontalMotion(mc.player.getYRot(), forward, right, horizontalSpeed.getValue());

		boolean ascending = acceptInput && mc.options.keyJump.isDown();
		boolean descending = acceptInput && mc.options.keyShift.isDown();
		double vertical;
		if (ascending != descending) {
			vertical = ascending ? verticalSpeed.getValue() : -verticalSpeed.getValue();
		} else {
			vertical = hover.getValue() ? 0.0 : boat.getDeltaMovement().y;
		}

		boat.setNoGravity(hover.getValue() || ascending || descending);
		boat.setYRot(mc.player.getYRot());
		boat.setDeltaMovement(horizontal.x, vertical, horizontal.z);
		boat.fallDistance = 0.0;
	}

	public static void onWorldChanged() {
		if (instance != null) {
			instance.releaseBoat();
			instance.observedLevel = null;
		}
	}

	private int keyDown(net.minecraft.client.KeyMapping key) {
		return key.isDown() ? 1 : 0;
	}

	private void releaseBoat() {
		if (controlledBoat != null) {
			controlledBoat.setNoGravity(previousNoGravity);
		}
		controlledBoat = null;
	}

	private static Vec3 horizontalMotion(float yaw, double forward, double right, double speed) {
		if (forward == 0.0 && right == 0.0) return Vec3.ZERO;
		double radians = yaw * Mth.DEG_TO_RAD;
		Vec3 forwardVector = new Vec3(-Mth.sin((float) radians), 0.0, Mth.cos((float) radians));
		Vec3 rightVector = new Vec3(-Mth.cos((float) radians), 0.0, -Mth.sin((float) radians));
		return forwardVector.scale(forward).add(rightVector.scale(right)).normalize().scale(speed);
	}
}
