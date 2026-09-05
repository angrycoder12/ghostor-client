package dev.lvstrng.argon.module.modules.blatant;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Direct client flight with independent horizontal/vertical speeds. */
public final class Fly extends Module implements TickListener {
	private static Fly instance;

	private final NumberSetting horizontalSpeed = new NumberSetting(
			EncryptedString.of("Horizontal Speed"), 0.1, 5.0, 1.0, 0.05);
	private final NumberSetting verticalSpeed = new NumberSetting(
			EncryptedString.of("Vertical Speed"), 0.1, 3.0, 0.5, 0.05);
	private final BooleanSetting antiKick = new BooleanSetting(
			EncryptedString.of("Anti Kick"), false);
	private final NumberSetting antiKickInterval = new NumberSetting(
			EncryptedString.of("Anti Kick Interval"), 5, 80, 70, 1)
			.visibleWhen(antiKick::getValue);
	private final NumberSetting antiKickDistance = new NumberSetting(
			EncryptedString.of("Anti Kick Distance"), 0.01, 0.2, 0.035, 0.005)
			.visibleWhen(antiKick::getValue);

	private LocalPlayer controlledPlayer;
	private boolean previousNoGravity;
	private int antiKickTicks;
	private Object observedLevel;

	public Fly() {
		super(EncryptedString.of("Fly"),
				EncryptedString.of("Allows free movement through the air"),
				-1,
				Category.BLATENT);
		addSettings(horizontalSpeed, verticalSpeed, antiKick, antiKickInterval, antiKickDistance);
		instance = this;
	}

	@Override
	public void onEnable() {
		antiKickTicks = 0;
		observedLevel = mc.level;
		capturePlayer(mc.player);
		eventManager.add(TickListener.class, this, -500);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		releasePlayer();
		observedLevel = null;
		antiKickTicks = 0;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			releasePlayer();
			observedLevel = mc.level;
			return;
		}

		if (controlledPlayer != mc.player || observedLevel != mc.level) {
			releasePlayer();
			observedLevel = mc.level;
			capturePlayer(mc.player);
		}

		LocalPlayer player = mc.player;
		if (player.isPassenger() || player.isDeadOrDying()) {
			player.setNoGravity(previousNoGravity);
			return;
		}

		player.setNoGravity(true);
		player.fallDistance = 0.0;

		double forward = keyDown(mc.options.keyUp) - keyDown(mc.options.keyDown);
		double right = keyDown(mc.options.keyRight) - keyDown(mc.options.keyLeft);
		Vec3 horizontal = horizontalMotion(player.getYRot(), forward, right, horizontalSpeed.getValue());

		double vertical = 0.0;
		boolean ascending = mc.gui.screen() == null && mc.options.keyJump.isDown();
		boolean descending = mc.gui.screen() == null && mc.options.keyShift.isDown();
		if (ascending != descending) {
			vertical = ascending ? verticalSpeed.getValue() : -verticalSpeed.getValue();
			antiKickTicks = 0;
		} else if (antiKick.getValue() && ++antiKickTicks >= antiKickInterval.getValueInt()) {
			vertical = -antiKickDistance.getValue();
			antiKickTicks = 0;
		}

		player.setDeltaMovement(horizontal.x, vertical, horizontal.z);
	}

	public static void onWorldChanged() {
		if (instance != null) {
			instance.releasePlayer();
			instance.observedLevel = null;
			instance.antiKickTicks = 0;
		}
	}

	private void capturePlayer(LocalPlayer player) {
		if (player == null) return;
		controlledPlayer = player;
		previousNoGravity = player.isNoGravity();
	}

	private void releasePlayer() {
		if (controlledPlayer != null) {
			controlledPlayer.setNoGravity(previousNoGravity);
			if (!previousNoGravity) {
				Vec3 motion = controlledPlayer.getDeltaMovement();
				controlledPlayer.setDeltaMovement(motion.x, 0.0, motion.z);
			}
		}
		controlledPlayer = null;
	}

	private int keyDown(net.minecraft.client.KeyMapping key) {
		return mc.gui.screen() == null && key.isDown() ? 1 : 0;
	}

	private static Vec3 horizontalMotion(float yaw, double forward, double right, double speed) {
		if (forward == 0.0 && right == 0.0) return Vec3.ZERO;
		double radians = yaw * Mth.DEG_TO_RAD;
		Vec3 forwardVector = new Vec3(-Mth.sin((float) radians), 0.0, Mth.cos((float) radians));
		Vec3 rightVector = new Vec3(-Mth.cos((float) radians), 0.0, -Mth.sin((float) radians));
		return forwardVector.scale(forward).add(rightVector.scale(right)).normalize().scale(speed);
	}
}
