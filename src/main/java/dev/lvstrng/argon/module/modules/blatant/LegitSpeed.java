package dev.lvstrng.argon.module.modules.blatant;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Crow's move-input speed adjustment, applied at Entity.moveRelative timing. */
public final class LegitSpeed extends Module {
	private final BooleanSetting boost = new BooleanSetting(EncryptedString.of("Boost"), true);
	private final NumberSetting speed = new NumberSetting(
			EncryptedString.of("Speed"), 1.0D, 1.4D, 1.12D, 0.01D);
	private final BooleanSetting fastFall = new BooleanSetting(EncryptedString.of("Fast fall"), false);
	private final BooleanSetting legitStrafe = new BooleanSetting(EncryptedString.of("Legit strafe"), false);
	private final BooleanSetting hypixel = new BooleanSetting(EncryptedString.of("Hypixel"), false);

	public LegitSpeed() {
		super(EncryptedString.of("LegitSpeed"),
				EncryptedString.of("Applies Crow-style legitimate movement acceleration"),
				-1,
				Category.BLATENT);
		addSettings(boost, speed, fastFall, legitStrafe, hypixel);
	}

	/**
	 * Replaces vanilla's local-player moveRelative calculation only while this module is active.
	 * Returning false leaves vanilla completely untouched.
	 */
	public static boolean applyMovement(Entity entity, float friction, Vec3 movementInput) {
		if (Argon.INSTANCE == null || Argon.INSTANCE.getModuleManager() == null
				|| Argon.mc == null || entity != Argon.mc.player || Argon.mc.level == null) {
			return false;
		}
		LegitSpeed module = Argon.INSTANCE.getModuleManager().getModule(LegitSpeed.class);
		if (module == null || !module.isEnabled() || module.isClientDisabled()) return false;

		if (module.fastFall.getValue() && entity.fallDistance > 1.5F) {
			Vec3 velocity = entity.getDeltaMovement();
			entity.setDeltaMovement(velocity.x, velocity.y * 1.075D, velocity.z);
		}

		float adjustedFriction = friction;
		if (module.boost.getValue() && (!module.hypixel.getValue() || entity.onGround())) {
			adjustedFriction *= module.speed.getValueFloat();
		}

		Vec3 adjustedInput = movementInput;
		float movementYaw = entity.getYRot();
		float forward = (float) movementInput.z;
		float strafe = (float) movementInput.x;
		if (module.legitStrafe.getValue() && !entity.onGround()
				&& (forward != 0.0F || strafe != 0.0F)) {
			movementYaw = strafeYaw(entity.getYRot(), forward, strafe);
			adjustedInput = new Vec3(0.0D, movementInput.y, 1.0D);
		}

		Vec3 acceleration = inputVector(adjustedInput, adjustedFriction, movementYaw);
		entity.setDeltaMovement(entity.getDeltaMovement().add(acceleration));
		return true;
	}

	private static float strafeYaw(float yaw, float forward, float strafe) {
		if (forward == 0.0F && strafe == 0.0F) return yaw;
		boolean reversed = forward < 0.0F;
		float strafingYaw = 90.0F * (forward > 0.0F ? 0.5F : reversed ? -0.5F : 1.0F);
		if (reversed) yaw += 180.0F;
		if (strafe > 0.0F) yaw -= strafingYaw;
		else if (strafe < 0.0F) yaw += strafingYaw;
		return yaw;
	}

	/** Exact 26.2 Entity.getInputVector transformation, with Crow's adjusted inputs. */
	private static Vec3 inputVector(Vec3 input, float friction, float yaw) {
		double lengthSquared = input.lengthSqr();
		if (lengthSquared < 1.0E-7D) return Vec3.ZERO;
		Vec3 scaled = (lengthSquared > 1.0D ? input.normalize() : input).scale(friction);
		float sin = (float) Math.sin(yaw * (Math.PI / 180.0D));
		float cos = (float) Math.cos(yaw * (Math.PI / 180.0D));
		return new Vec3(scaled.x * cos - scaled.z * sin, scaled.y,
				scaled.z * cos + scaled.x * sin);
	}
}
