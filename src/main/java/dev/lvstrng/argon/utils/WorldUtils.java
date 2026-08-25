package dev.lvstrng.argon.utils;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.client.Friends;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;
import java.util.stream.Stream;

import static dev.lvstrng.argon.Argon.mc;

public final class WorldUtils {
	public static boolean isDeadBodyNearby() {
		return mc.level.players().parallelStream()
				.filter(e -> e != mc.player)
				.filter(e -> e.distanceToSqr(mc.player) <= 36)
				.anyMatch(LivingEntity::isDeadOrDying);
	}

	public static Entity findNearestEntity(Player toPlayer, float radius, boolean seeOnly) {
		float mr = Float.MAX_VALUE;
		Entity entity = null;

		assert mc.level != null;
		for (Entity e : mc.level.entitiesForRendering()) {
			float d = e.distanceTo(toPlayer);

			if (e != toPlayer && d <= radius && mc.player.hasLineOfSight(e) == seeOnly) {
				if (d < mr) {
					mr = d;
					entity = e;
				}
			}
		}
		return entity;
	}

	public static double distance(Vec3 fromVec, Vec3 toVec) {
		return Math.sqrt(Math.pow(toVec.x - fromVec.x, 2) + Math.pow(toVec.y - fromVec.y, 2) + Math.pow(toVec.z - fromVec.z, 2));
	}

	public static Player findNearestPlayer(Player toPlayer, float range, boolean seeOnly, boolean excludeFriends) {
		float minRange = Float.MAX_VALUE;
		Player minPlayer = null;

		for (Player player : mc.level.players()) {
			float distance = (float) distance(toPlayer.position(), player.position());

			if(excludeFriends && Argon.INSTANCE.getModuleManager().getModule(Friends.class).disableAimAssist.getValue() && Argon.INSTANCE.getFriendManager().isFriend(player))
				continue;

			if (player != toPlayer && distance <= range && player.hasLineOfSight(toPlayer) == seeOnly) {
				if (distance < minRange) {
					minRange = distance;
					minPlayer = player;
				}
			}
		}

		return minPlayer;
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

	public static HitResult getHitResult(double radius) {
		return getHitResult(mc.player, false, mc.player.getYRot(), mc.player.getXRot(), radius);
	}

	public static HitResult getHitResult(Player entity, boolean ignoreInvisibles, float yaw, float pitch, double distance) {
		if (entity == null || mc.level == null) return null;

		double d = distance;
		Vec3 cameraPosVec = entity.getEyePosition(RenderUtils.tickProgress());
		Vec3 rotationVec = getPlayerLookVec(yaw, pitch);
		Vec3 range = cameraPosVec.add(rotationVec.x * d, rotationVec.y * d, rotationVec.z * d);

		HitResult result = mc.level.clip(new ClipContext(cameraPosVec, range, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));

		double e = d * d;
		d = distance;

		if (result != null) {
			e = result.getLocation().distanceToSqr(cameraPosVec);
		}

		Vec3 vec3d3 = cameraPosVec.add(rotationVec.x * d, rotationVec.y * d, rotationVec.z * d);
		AABB box = entity.getBoundingBox().expandTowards(rotationVec.scale(d)).inflate(1.0, 1.0, 1.0);

		EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(entity, cameraPosVec, vec3d3, box, (entityx) ->
				!entityx.isSpectator() && entityx.isPickable() && (!entityx.isInvisible() || !ignoreInvisibles), e);

		if (entityHitResult != null) {
			Vec3 vec3d4 = entityHitResult.getLocation();
			double g = cameraPosVec.distanceToSqr(vec3d4);

			if ((distance > distance && g > Math.pow(distance, 2)) || (g < e || result == null)) {
				result = g > Math.pow(distance, 2)
						? BlockHitResult.miss(vec3d4, Direction.getApproximateNearest(rotationVec.x, rotationVec.y, rotationVec.z), BlockPos.containing(vec3d4))
						: entityHitResult;
			}
		}

		return result;
	}


	public static void placeBlock(BlockHitResult blockHit, boolean swingHand) {
		InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
		if (result.consumesAction() && swingHand) mc.player.swing(InteractionHand.MAIN_HAND);
	}

	public static Stream<LevelChunk> getLoadedChunks() {
		int radius = Math.max(2, mc.options.getEffectiveRenderDistance()) + 3;
		int diameter = radius * 2 + 1;

		ChunkPos center = mc.player.chunkPosition();
		ChunkPos min = new ChunkPos(center.x() - radius, center.z() - radius);
		ChunkPos max = new ChunkPos(center.x() + radius, center.z() + radius);

		return Stream.iterate(min, pos -> {
					int x = pos.x();
					int z = pos.z();
					x++;
					if (x > max.x()) {
						x = min.x();
						z++;
					}
					if (z > max.z())
						throw new IllegalStateException("Stream limit didn't work.");

					return new ChunkPos(x, z);

				}).limit((long) diameter * diameter)
				.filter(c -> mc.level.hasChunk(c.x(), c.z()))
				.map(c -> mc.level.getChunk(c.x(), c.z())).filter(Objects::nonNull);
	}

    /*
                NORTH

        WEST      +      EAST

                SOUTH
     */

	public static boolean isShieldFacingAway(Player player) {
		if (mc.player != null && player != null) {
			Vec3 playerPos = mc.player.position();
			Vec3 targetPos = player.position();

			Vec3 directionToPlayer = playerPos.subtract(targetPos).normalize();

			float yaw = player.getYRot();
			float pitch = player.getXRot();
			Vec3 facingDirection = new Vec3(
					-Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
					-Math.sin(Math.toRadians(pitch)),
					Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
			).normalize();

			double dotProduct = facingDirection.dot(directionToPlayer);

			return dotProduct < 0;
		}
		return false;
	}

	public static boolean isSword(ItemStack itemStack) {
		return itemStack.is(ItemTags.SWORDS);
	}

	public static boolean isAxe(ItemStack itemStack) {
		return itemStack.is(ItemTags.AXES);
	}

	public static boolean isWeapon(ItemStack itemStack) {
		return isSword(itemStack) || isAxe(itemStack);
	}

	public static boolean isTool(ItemStack itemStack) {
		return isWeapon(itemStack)
				|| itemStack.is(ItemTags.PICKAXES)
				|| itemStack.is(ItemTags.SHOVELS)
				|| itemStack.is(ItemTags.HOES);
	}

	public static boolean isCrit(Player player, Entity target) {
		return player.getAttackStrengthScale(0.5F) > 0.9F && player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isUnderWater() && !player.hasEffect(MobEffects.BLINDNESS) && target instanceof LivingEntity;
	}

	public static void hitEntity(Entity entity, boolean swingHand) {
		mc.gameMode.attack(mc.player, entity);

		if (swingHand)
			mc.player.swing(InteractionHand.MAIN_HAND);
	}
}
