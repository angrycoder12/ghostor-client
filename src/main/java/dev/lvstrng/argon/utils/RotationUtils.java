package dev.lvstrng.argon.utils;

import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static dev.lvstrng.argon.Argon.mc;

public final class RotationUtils {

	public static Vec3 getEyesPos(Player player) {
		return player.getEyePosition(RenderUtils.tickProgress());
	}

	public static BlockPos getCameraBlockPos() {
		return mc.gameRenderer.mainCamera().blockPosition();
	}

	public static BlockPos getEyesBlockPos() {
		return new BlockPos((int) getEyesPos(mc.player).x, (int) getEyesPos(mc.player).y, (int) getEyesPos(mc.player).z);
	}

	public static Vec3 getPlayerLookVec(float yaw, float pitch) {
		float f = pitch * 0.017453292F;
		float g = -yaw * 0.017453292F;

		float h = Mth.cos(g);
		float i = Mth.sin(g);
		float j = Mth.cos(f);
		float k = Mth.sin(f);

		return new Vec3((i * j), (-k), (h * j));
	}

	public static Vec3 getPlayerLookVec(Player player) {
		return getPlayerLookVec(player.getYRot(), player.getXRot());
	}

	public static Rotation getDiff(Rotation rotation1, Rotation rotation2) {
		double yaw = Math.abs(Math.max(rotation1.yaw(), rotation2.yaw()) - Math.min(rotation1.yaw(), rotation2.yaw()));
		double pitch = Math.abs(Math.max(rotation1.pitch(), rotation2.pitch()) - Math.min(rotation1.pitch(), rotation2.pitch()));

		return new Rotation(yaw, pitch);
	}

	public static Rotation getSmoothRotation(Rotation from, Rotation to, double speed) {
		return new Rotation(
				Mth.rotLerp((float) speed, (float) from.yaw(), (float) to.yaw()),
				Mth.rotLerp((float) speed, (float) from.pitch(), (float) to.pitch())
		);
	}

	public static double getTotalDiff(Rotation rotation1, Rotation rotation2) {
		Rotation diff = getDiff(rotation1, rotation2);

		return diff.yaw() + diff.pitch();
	}

	public static Vec3 getClientLookVec() {
		return getPlayerLookVec(mc.player);
	}

	public static Rotation getDirection(Entity entity, Vec3 vec) {
		double dx = vec.x - entity.getX(),
				dy = vec.y - entity.getY(),
				dz = vec.z - entity.getZ(),
				dist = Mth.sqrt((float) (dx * dx + dz * dz));

		return new Rotation(Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0), -Mth.wrapDegrees(Math.toDegrees(Math.atan2(dy, dist))));
	}

	public static double getAngleToRotation(Rotation rotation) {
		double currentYaw = Mth.wrapDegrees(mc.player.getYRot());
		double currentPitch = Mth.wrapDegrees(mc.player.getXRot());

		double diffYaw = Mth.wrapDegrees(currentYaw - rotation.yaw());
		double diffPitch = Mth.wrapDegrees(currentPitch - rotation.pitch());

		return Math.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
	}
}
