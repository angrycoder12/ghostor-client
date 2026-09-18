package dev.lvstrng.argon.module.modules.render.trajectory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Stateless, allocation-bounded projectile simulation using vanilla 26.2 constants. */
public final class ProjectilePathSimulator {
	private enum UpdateOrder { Arrow, Throwable, Fishing }
	private record Profile(double speed, double roll, double gravity, double airDrag, double waterDrag,
			UpdateOrder order, double radius) {}
	public record Path(List<Vec3> points, HitResult hit) {}

	public Path simulate(Player player, float delta, boolean allProjectiles, int maxSteps) {
		ItemStack stack = activeStack(player);
		Profile profile = profile(player, stack, allProjectiles);
		if (profile == null) return null;
		Vec3 position = startPosition(player, delta);
		Vec3 motion = initialMotion(player, delta, profile);
		if (motion.lengthSqr() < 1.0E-7D) return null;
		ArrayList<Vec3> points = new ArrayList<>(Math.min(maxSteps + 1, 601));
		points.add(position);
		HitResult impact = null;

		for (int step = 0; step < maxSteps && position.y > player.level().getMinY() - 16; step++) {
			if (profile.order != UpdateOrder.Arrow) motion = motion.add(0.0D, -profile.gravity, 0.0D);
			boolean inWater = player.level().getFluidState(BlockPos.containing(position)).is(FluidTags.WATER);
			if (profile.order == UpdateOrder.Throwable) motion = motion.scale(inWater ? profile.waterDrag : profile.airDrag);
			Vec3 next = position.add(motion);
			HitResult block = player.level().clip(new ClipContext(position, next,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (block.getType() != HitResult.Type.MISS) next = block.getLocation();
			EntityHitResult entity = ProjectileUtil.getEntityHitResult(player.level(), player, position, next,
					new AABB(position, next).inflate(profile.radius), ProjectilePathSimulator::canHit, 256.0F);
			if (entity != null && (block.getType() == HitResult.Type.MISS
					|| position.distanceToSqr(entity.getLocation()) < position.distanceToSqr(block.getLocation()))) {
				impact = entity;
				next = entity.getLocation();
			} else if (block.getType() != HitResult.Type.MISS) impact = block;
			points.add(next);
			position = next;
			if (impact != null) break;
			if (profile.order != UpdateOrder.Throwable) motion = motion.scale(inWater ? profile.waterDrag : profile.airDrag);
			if (profile.order == UpdateOrder.Arrow) motion = motion.add(0.0D, -profile.gravity, 0.0D);
		}
		return new Path(points, impact);
	}

	private static boolean canHit(Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator() && entity.isPickable();
	}

	private static ItemStack activeStack(Player player) {
		if (player.isUsingItem()) return player.getUseItem();
		ItemStack main = player.getMainHandItem();
		if (supported(main)) return main;
		return player.getOffhandItem();
	}

	private static boolean supported(ItemStack stack) {
		Item item = stack.getItem();
		return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT
				|| item == Items.SNOWBALL || item == Items.EGG || item == Items.ENDER_PEARL
				|| item == Items.EXPERIENCE_BOTTLE || item == Items.SPLASH_POTION
				|| item == Items.LINGERING_POTION || item == Items.FISHING_ROD || item == Items.WIND_CHARGE;
	}

	private static Profile profile(Player player, ItemStack stack, boolean all) {
		Item item = stack.getItem();
		if (item == Items.BOW) {
			if (!player.isUsingItem()) return null;
			float charge = BowItem.getPowerForTime(player.getTicksUsingItem());
			return charge <= 0.0F ? null : new Profile(charge * 3.0D, 0, .05, .99, .6, UpdateOrder.Arrow, .3);
		}
		if (!all) return null;
		if (item == Items.CROSSBOW) {
			if (!CrossbowItem.isCharged(stack)) return null;
			ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
			boolean firework = charged != null && charged.contains(Items.FIREWORK_ROCKET);
			return new Profile(firework ? 1.6D : 3.15D, 0, firework ? 0 : .05,
					firework ? 1.0D : .99D, firework ? 1.0D : .6D,
					firework ? UpdateOrder.Throwable : UpdateOrder.Arrow, .3);
		}
		if (item == Items.TRIDENT) {
			if (!player.isUsingItem()) return null;
			return new Profile(2.5, 0, .05, .99, .99, UpdateOrder.Arrow, .3);
		}
		if (item == Items.EXPERIENCE_BOTTLE) return new Profile(.7, -20, .07, .99, .8, UpdateOrder.Throwable, .25);
		if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION)
			return new Profile(.5, -20, .05, .99, .8, UpdateOrder.Throwable, .25);
		if (item == Items.WIND_CHARGE) return new Profile(1.5, 0, 0, 1, 1, UpdateOrder.Throwable, .3);
		if (item == Items.FISHING_ROD) {
			if (player.fishing != null) return null;
			return new Profile(1.5, 0, .03, .92, 0, UpdateOrder.Fishing, .25);
		}
		if (item == Items.SNOWBALL || item == Items.EGG || item == Items.ENDER_PEARL)
			return new Profile(1.5, 0, .03, .99, .8, UpdateOrder.Throwable, .25);
		return null;
	}

	private static Vec3 startPosition(Player player, float delta) {
		double yaw = Math.toRadians(player.getViewYRot(delta));
		return player.getPosition(delta).add(-Math.cos(yaw) * .16D,
				player.getEyeHeight() - .1D, -Math.sin(yaw) * .16D);
	}

	private static Vec3 initialMotion(Player player, float delta, Profile profile) {
		float pitchRad = (player.getViewXRot(delta) + (float) profile.roll) * Mth.DEG_TO_RAD;
		float yawRad = player.getViewYRot(delta) * Mth.DEG_TO_RAD;
		Vec3 direction = new Vec3(-Mth.sin(yawRad) * Mth.cos(pitchRad),
				-Mth.sin(pitchRad), Mth.cos(yawRad) * Mth.cos(pitchRad)).normalize();
		Vec3 motion = direction.scale(profile.speed);
		Vec3 playerMotion = player.getDeltaMovement();
		return motion.add(playerMotion.x, player.onGround() ? 0.0D : playerMotion.y, playerMotion.z);
	}
}
