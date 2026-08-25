package dev.lvstrng.argon.utils;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiFunction;

import static dev.lvstrng.argon.Argon.mc;


public class DamageUtils {
	private DamageUtils() {
	}

	// Explosion damage

	/**
	 * It is recommended to use this {@link RaycastFactory} unless you implement custom behaviour, as soon:tm: it will be the
	 * target of optimizations to make it more performant.
	 * @see BlockGetter#clip(ClipContext)
	 */
	public static final RaycastFactory HIT_FACTORY = (context, blockPos) -> {
		BlockState blockState = mc.level.getBlockState(blockPos);
		if (blockState.getBlock().getExplosionResistance() < 600) return null;

		return blockState.getCollisionShape(mc.level, blockPos).clip(context.start(), context.end(), blockPos);
	};

	public static float crystalDamage(LivingEntity target, Vec3 targetPos, AABB targetBox, Vec3 explosionPos, RaycastFactory raycastFactory) {
		return explosionDamage(target, targetPos, targetBox, explosionPos, 12f, raycastFactory);
	}

	public static float bedDamage(LivingEntity target, Vec3 targetPos, AABB targetBox, Vec3 explosionPos, RaycastFactory raycastFactory) {
		return explosionDamage(target, targetPos, targetBox, explosionPos, 10f, raycastFactory);
	}

	public static float anchorDamage(LivingEntity target, Vec3 targetPos, AABB targetBox, Vec3 explosionPos, RaycastFactory raycastFactory) {
		return explosionDamage(target, targetPos, targetBox, explosionPos, 10f, raycastFactory);
	}

	/**
	 * Low level control of parameters without having to reimplement everything, for addon authors who wish to use their
	 * own predictions or other systems.
	 * @see net.minecraft.world.level.ExplosionDamageCalculator#getEntityDamageAmount(Explosion, Entity)
	 */
	public static float explosionDamage(LivingEntity target, Vec3 targetPos, AABB targetBox, Vec3 explosionPos, float power, RaycastFactory raycastFactory) {
		double modDistance = distance(targetPos.x, targetPos.y, targetPos.z, explosionPos.x, explosionPos.y, explosionPos.z);
		if (modDistance > power) return 0f;

		double exposure = getExposure(explosionPos, targetBox, raycastFactory);
		double impact = (1 - (modDistance / power)) * exposure;
		float damage = (int) ((impact * impact + impact) / 2 * 7 * 12 + 1);

		return calculateReductions(damage, target, mc.level.damageSources().explosion(null));
	}

	public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
		return Math.sqrt(squaredDistance(x1, y1, z1, x2, y2, z2));
	}

	public static double distanceTo(Entity entity) {
		return distanceTo(entity.getX(), entity.getY(), entity.getZ());
	}

	public static double distanceTo(BlockPos blockPos) {
		return distanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());
	}

	public static double distanceTo(Vec3 vec3d) {
		return distanceTo(vec3d.x(), vec3d.y(), vec3d.z());
	}

	public static double distanceTo(double x, double y, double z) {
		return Math.sqrt(squaredDistanceTo(x, y, z));
	}

	public static double squaredDistanceTo(Entity entity) {
		return squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ());
	}

	public static double squaredDistanceTo(BlockPos blockPos) {
		return squaredDistanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());
	}

	public static double squaredDistanceTo(double x, double y, double z) {
		return squaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ(), x, y, z);
	}

	public static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
		double f = x1 - x2;
		double g = y1 - y2;
		double h = z1 - z2;
		return org.joml.Math.fma(f, f, org.joml.Math.fma(g, g, h * h));
	}

	/** Meteor Client implementations */

	public static float crystalDamage(LivingEntity target, Vec3 crystal, boolean predictMovement, BlockPos obsidianPos) {
		return overridingExplosionDamage(target, crystal, 12f, predictMovement, obsidianPos, Blocks.OBSIDIAN.defaultBlockState());
	}

	public static float crystalDamage(LivingEntity target, Vec3 crystal) {
		return explosionDamage(target, crystal, 12f, false);
	}

	public static float bedDamage(LivingEntity target, Vec3 bed) {
		return explosionDamage(target, bed, 10f, false);
	}

	public static float anchorDamage(LivingEntity target, Vec3 anchor) {
		return overridingExplosionDamage(target, anchor, 10f, false, BlockPos.containing(anchor), Blocks.AIR.defaultBlockState());
	}

	private static float overridingExplosionDamage(LivingEntity target, Vec3 explosionPos, float power, boolean predictMovement, BlockPos overridePos, BlockState overrideState) {
		return explosionDamage(target, explosionPos, power, predictMovement, getOverridingHitFactory(overridePos, overrideState));
	}

	private static float explosionDamage(LivingEntity target, Vec3 explosionPos, float power, boolean predictMovement) {
		return explosionDamage(target, explosionPos, power, predictMovement, HIT_FACTORY);
	}

	public static GameType getGameMode(Player player) {
		PlayerInfo playerListEntry = mc.getConnection().getPlayerInfo(player.getUUID());
		if (playerListEntry == null) return GameType.SPECTATOR;
		return playerListEntry.getGameMode();
	}

	private static float explosionDamage(LivingEntity target, Vec3 explosionPos, float power, boolean predictMovement, RaycastFactory raycastFactory) {
		if (target == null) return 0f;
		if (target instanceof Player player && getGameMode(player) == GameType.CREATIVE) return 0f;

		Vec3 position = predictMovement ? target.position().add(target.getDeltaMovement()) : target.position();

		AABB box = target.getBoundingBox();
		if (predictMovement) box = box.move(target.getDeltaMovement());

		return explosionDamage(target, position, box, explosionPos, power, raycastFactory);
	}

	public static RaycastFactory getOverridingHitFactory(BlockPos overridePos, BlockState overrideState) {
		return (context, blockPos) -> {
			BlockState blockState;
			if (blockPos.equals(overridePos)) blockState = overrideState;
			else {
				blockState = mc.level.getBlockState(blockPos);
				if (blockState.getBlock().getExplosionResistance() < 600) return null;
			}

			return blockState.getCollisionShape(mc.level, blockPos).clip(context.start(), context.end(), blockPos);
		};
	}

	// Sword damage

	/**
	 * @see Player#attack(Entity)
	 */
	public static float getAttackDamage(LivingEntity attacker, LivingEntity target) {
		float itemDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
		DamageSource damageSource = attacker instanceof Player player ? mc.level.damageSources().playerAttack(player) : mc.level.damageSources().mobAttack(attacker);

		// Get enchant damage
		ItemStack stack = attacker.getWeaponItem();
		float enchantDamage = /*fixme EnchantmentHelper.getDamage(attacker.getWorld() instanceof ServerWorld serverWorld ? serverWorld : null, stack, target, damageSource, itemDamage) - itemDamage*/ 0;

		// Factor charge
		if (attacker instanceof Player playerEntity) {
			float charge = playerEntity.getAttackStrengthScale(0.5f);
			itemDamage *= 0.2f + charge * charge * 0.8f;
			enchantDamage *= charge;

			// Factor critical hit
			if (charge > 0.9f && attacker.fallDistance > 0f && !attacker.onGround() && !attacker.onClimbable() && !attacker.isInWater() && !attacker.hasEffect(MobEffects.BLINDNESS) && !attacker.isPassenger()) {
				itemDamage *= 1.5f;
			}
		}

		float damage = itemDamage + enchantDamage;

		damage = calculateReductions(damage, target, damageSource);

		return damage;
	}

	// Fall Damage
	public static float fallDamage(LivingEntity entity) {
		if (entity instanceof Player player && player.getAbilities().flying) return 0f;
		if (entity.hasEffect(MobEffects.SLOW_FALLING) || entity.hasEffect(MobEffects.LEVITATION)) return 0f;

		// Fast path - Above the surface
		int surface = mc.level.getChunkAt(entity.blockPosition()).getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING).getFirstAvailable(entity.getBlockX() & 15, entity.getBlockZ() & 15);
		if (entity.getBlockY() >= surface) return fallDamageReductions(entity, surface);

		// Under the surface
		BlockHitResult raycastResult = mc.level.clip(new ClipContext(entity.position(), new Vec3(entity.getX(), mc.level.getMinY(), entity.getZ()), ClipContext.Block.COLLIDER, ClipContext.Fluid.WATER, entity));
		if (raycastResult.getType() == HitResult.Type.MISS) return 0;

		return fallDamageReductions(entity, raycastResult.getBlockPos().getY());
	}

	private static float fallDamageReductions(LivingEntity entity, int surface) {
		int fallHeight = (int) (entity.getY() - surface + entity.fallDistance - 3d);
		@Nullable MobEffectInstance jumpBoostInstance = entity.getEffect(MobEffects.JUMP_BOOST);
		if (jumpBoostInstance != null) fallHeight -= jumpBoostInstance.getAmplifier() + 1;

		return calculateReductions(fallHeight, entity, mc.level.damageSources().fall());
	}

	// Utils
	public static float calculateReductions(float damage, LivingEntity entity, DamageSource damageSource) {
		if (damageSource.scalesWithDifficulty()) {
			switch (mc.level.getDifficulty()) {
				case EASY     -> damage = Math.min(damage / 2 + 1, damage);
				case HARD     -> damage *= 1.5f;
			}
		}

		// Armor reduction
		damage = CombatRules.getDamageAfterAbsorb(entity, damage, damageSource, getArmor(entity), (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));

		// Resistance reduction
		damage = resistanceReduction(entity, damage);

		// Protection reduction
		damage = protectionReduction(entity, damage, damageSource);

		return Math.max(damage, 0);
	}

	private static float getArmor(LivingEntity entity) {
		return (float) Math.floor(entity.getAttributeValue(Attributes.ARMOR));
	}


	private static float protectionReduction(LivingEntity player, float damage, DamageSource source) {
		//fixme float protLevel = EnchantmentHelper.getProtectionAmount(player.getWorld() instanceof ServerWorld serverWorld ? serverWorld : null, player, source);
		return CombatRules.getDamageAfterMagicAbsorb(damage, /*protLevel*/ 0);
	}


	private static float resistanceReduction(LivingEntity player, float damage) {
		MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
		if (resistance != null) {
			int lvl = resistance.getAmplifier() + 1;
			damage *= (1 - (lvl * 0.2f));
		}

		return Math.max(damage, 0);
	}

	/**
	 * @see Explosion#getExposure(Vec3d, Entity)
	 */
	private static float getExposure(Vec3 source, AABB box, RaycastFactory raycastFactory) {
		double xDiff = box.maxX - box.minX;
		double yDiff = box.maxY - box.minY;
		double zDiff = box.maxZ - box.minZ;

		double xStep = 1 / (xDiff * 2 + 1);
		double yStep = 1 / (yDiff * 2 + 1);
		double zStep = 1 / (zDiff * 2 + 1);

		if (xStep > 0 && yStep > 0 && zStep > 0) {
			int misses = 0;
			int hits = 0;

			double xOffset = (1 - Math.floor(1 / xStep) * xStep) * 0.5;
			double zOffset = (1 - Math.floor(1 / zStep) * zStep) * 0.5;

			xStep = xStep * xDiff;
			yStep = yStep * yDiff;
			zStep = zStep * zDiff;

			double startX = box.minX + xOffset;
			double startY = box.minY;
			double startZ = box.minZ + zOffset;
			double endX = box.maxX + xOffset;
			double endY = box.maxY;
			double endZ = box.maxZ + zOffset;

			for (double x = startX; x <= endX; x += xStep) {
				for (double y = startY; y <= endY; y += yStep) {
					for (double z = startZ; z <= endZ; z += zStep) {
						Vec3 position = new Vec3(x, y, z);

						if (raycast(new ExposureRaycastContext(position, source), raycastFactory) == null) misses++;

						hits++;
					}
				}
			}

			return (float) misses / hits;
		}

		return 0f;
	}

	/* Raycasts */

	private static BlockHitResult raycast(ExposureRaycastContext context, RaycastFactory raycastFactory) {
		return BlockGetter.traverseBlocks(context.start, context.end, context, raycastFactory, ctx -> null);
	}

	public record ExposureRaycastContext(Vec3 start, Vec3 end) {}

	@FunctionalInterface
	public interface RaycastFactory extends BiFunction<ExposureRaycastContext, BlockPos, BlockHitResult> {}
}
