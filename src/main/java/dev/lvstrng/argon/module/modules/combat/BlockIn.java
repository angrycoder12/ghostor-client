package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.MovementPacketListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Crow-style automatic self-box placement, adapted to the 26.2 interaction protocol. */
public final class BlockIn extends Module implements MovementPacketListener {
	private static final int[][] WALL_OFFSETS = {
			{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
			{1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}
	};
	private static final int[][] TOP_OFFSET = {{0, 2, 0}};
	private static final int[][] BOTTOM_OFFSET = {{0, -1, 0}};
	private static final float MIN_RANDOM_OFFSET = 0.05F;

	public enum RaycastMode {
		Normal,
		Strict,
		Grim
	}

	private static BlockIn instance;

	private final NumberSetting placeRange = new NumberSetting(
			EncryptedString.of("Place range"), 3.0D, 6.0D, 4.5D, 0.1D);
	private final NumberSetting placeDelay = new NumberSetting(
			EncryptedString.of("Place delay"), 0.0D, 8.0D, 0.0D, 1.0D);
	private final NumberSetting rotationSpeed = new NumberSetting(
			EncryptedString.of("Rot speed"), 2.0D, 60.0D, 32.0D, 0.5D);
	private final BooleanSetting topCap = new BooleanSetting(EncryptedString.of("Top cap"), true);
	private final BooleanSetting bottomCap = new BooleanSetting(EncryptedString.of("Bottom cap"), false);
	private final BooleanSetting swing = new BooleanSetting(EncryptedString.of("Swing"), true);
	private final BooleanSetting autoDisable = new BooleanSetting(EncryptedString.of("Auto off"), true);
	private final BooleanSetting grimBypass = new BooleanSetting(EncryptedString.of("Grim bypass"), true);
	private final BooleanSetting silent = new BooleanSetting(EncryptedString.of("Silent"), false);
	private final NumberSetting randomOffsetStrength = new NumberSetting(
			EncryptedString.of("Rand offset"), 0.05D, 1.5D, 0.45D, 0.05D);
	private final ModeSetting<RaycastMode> raycastMode = new ModeSetting<>(
			EncryptedString.of("Raycast"), RaycastMode.Grim, RaycastMode.class);

	private final List<BlockPos> placeQueue = new ArrayList<>();
	private int previousSlot = -1;
	private boolean active;
	private BlockPos anchorPos;
	private PlaceInfo currentTarget;
	private int delayTicks;

	private float serverYaw;
	private float serverPitch;
	private float yawVelocity;
	private float pitchVelocity;
	private boolean rotationSeeded;

	private boolean temporaryRotation;
	private float savedClientYaw;
	private float savedClientPitch;

	public BlockIn() {
		super(EncryptedString.of("BlockIn"),
				EncryptedString.of("Automatically places a protective block box around you"),
				-1,
				Category.COMBAT);
		addSettings(placeRange, placeDelay, rotationSpeed, topCap, bottomCap, swing,
				autoDisable, grimBypass, silent, randomOffsetStrength, raycastMode);
		instance = this;
	}

	@Override
	public void onEnable() {
		resetRuntime(false);
		eventManager.add(MovementPacketListener.class, this, -1000);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(MovementPacketListener.class, this);
		resetRuntime(true);
		super.onDisable();
	}

	@Override
	public void onSendMovementPackets() {
		restoreTemporaryRotation();
		if (mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) {
			resetRuntime(true);
			return;
		}

		if (!active) tryStart();
		if (!active) return;

		if (!isHoldingPlaceableBlock()) {
			int blockSlot = findBestBlockSlot();
			if (blockSlot == -1) {
				finish();
				return;
			}
			InventoryUtils.setInvSlot(blockSlot);
		}

		if (delayTicks > 0) delayTicks--;
		if (currentTarget == null || !isReplaceable(currentTarget.targetPos)) acquireNextTarget();
		if (currentTarget == null) {
			finish();
			return;
		}

		float[] targetRotation = computePlacementAngles(currentTarget.hitVec);
		if (!grimBypass.getValue()) {
			targetRotation = applyPlacementRandomization(
					targetRotation[0], targetRotation[1], currentTarget.targetPos);
		}
		targetRotation = applyUniquenessOffset(
				targetRotation[0], targetRotation[1], currentTarget.targetPos);
		currentTarget.rotationYaw = targetRotation[0];
		currentTarget.rotationPitch = targetRotation[1];

		stepAim(targetRotation[0], targetRotation[1]);
		if (!silent.getValue()) {
			mc.player.setYRot(serverYaw);
			mc.player.setXRot(Mth.clamp(serverPitch, -89.5F, 89.5F));
		}

		if (delayTicks <= 0 && rotationReady()) attemptPlace();
	}

	/** Temporarily exposes the silent server rotation while LocalPlayer builds its movement packet. */
	public static void prepareMovementPacket() {
		BlockIn module = instance;
		if (module == null || !module.isEnabled() || module.isClientDisabled()
				|| !module.active || !module.silent.getValue() || module.mc.player == null
				|| module.currentTarget == null) {
			return;
		}
		module.restoreTemporaryRotation();
		module.savedClientYaw = module.mc.player.getYRot();
		module.savedClientPitch = module.mc.player.getXRot();
		module.mc.player.setYRot(module.serverYaw);
		module.mc.player.setXRot(module.serverPitch);
		module.temporaryRotation = true;
	}

	/** Restores the user's view after the movement packet has captured a silent rotation. */
	public static void finishMovementPacket() {
		BlockIn module = instance;
		if (module != null) module.restoreTemporaryRotation();
	}

	private void tryStart() {
		anchorPos = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
		buildPlaceQueue();
		if (placeQueue.isEmpty()) {
			if (autoDisable.getValue()) setEnabled(false);
			return;
		}

		int blockSlot = findBestBlockSlot();
		if (blockSlot == -1) {
			if (autoDisable.getValue()) setEnabled(false);
			return;
		}

		if (previousSlot == -1) previousSlot = mc.player.getInventory().getSelectedSlot();
		InventoryUtils.setInvSlot(blockSlot);
		active = true;
		delayTicks = 0;
		seedRotation();
		acquireNextTarget();
	}

	private void attemptPlace() {
		if (currentTarget == null) return;
		if (!isReplaceable(currentTarget.targetPos)) {
			currentTarget = null;
			return;
		}
		if (!passesRaycast(currentTarget)) {
			placeQueue.add(currentTarget.targetPos);
			currentTarget = null;
			return;
		}
		if (doPlace(currentTarget)) delayTicks = nextDelayTicks();
		currentTarget = null;
		if (placeQueue.isEmpty()) {
			acquireNextTarget();
			if (currentTarget == null && autoDisable.getValue()) finish();
		}
	}

	private void acquireNextTarget() {
		currentTarget = null;
		int requeued = 0;
		int maxRetries = placeQueue.size();
		while (!placeQueue.isEmpty() && requeued <= maxRetries) {
			BlockPos target = placeQueue.removeFirst();
			if (!isReplaceable(target)) {
				requeued = 0;
				continue;
			}
			PlaceInfo found = findPlacement(target);
			if (found == null) {
				placeQueue.add(target);
				requeued++;
				continue;
			}
			float[] rotation = computePlacementAngles(found.hitVec);
			if (!grimBypass.getValue()) {
				rotation = applyPlacementRandomization(rotation[0], rotation[1], target);
			}
			rotation = applyUniquenessOffset(rotation[0], rotation[1], target);
			found.rotationYaw = rotation[0];
			found.rotationPitch = rotation[1];
			currentTarget = found;
			return;
		}
	}

	private void buildPlaceQueue() {
		placeQueue.clear();
		if (anchorPos == null) return;
		addOffsets(WALL_OFFSETS);
		if (topCap.getValue()) addOffsets(TOP_OFFSET);
		if (bottomCap.getValue()) addOffsets(BOTTOM_OFFSET);

		Vec3 eyes = mc.player.getEyePosition();
		double maxDistance = placeRange.getValue();
		placeQueue.removeIf(pos -> !isReplaceable(pos)
				|| eyes.distanceTo(Vec3.atCenterOf(pos)) > maxDistance + 2.0D);
		int anchorX = anchorPos.getX();
		int anchorZ = anchorPos.getZ();
		placeQueue.sort(Comparator.<BlockPos>comparingLong(BlockPos::getY)
				.thenComparingDouble(pos -> Math.atan2(pos.getZ() - anchorZ, pos.getX() - anchorX)));
	}

	private void addOffsets(int[][] offsets) {
		for (int[] offset : offsets) {
			placeQueue.add(anchorPos.offset(offset[0], offset[1], offset[2]));
		}
	}

	private PlaceInfo findPlacement(BlockPos target) {
		Vec3 eyes = mc.player.getEyePosition();
		double maxDistance = placeRange.getValue();
		PlaceInfo best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = target.relative(direction);
			if (!isSolid(neighbor)) continue;
			Direction clickFace = direction.getOpposite();
			Vec3 hit = Vec3.atCenterOf(neighbor).add(
					clickFace.getStepX() * 0.48D,
					clickFace.getStepY() * 0.48D,
					clickFace.getStepZ() * 0.48D);
			double distance = eyes.distanceTo(hit);
			if (distance <= maxDistance && distance < bestDistance) {
				bestDistance = distance;
				best = new PlaceInfo(target, neighbor, clickFace, hit);
			}
		}
		return best;
	}

	private boolean passesRaycast(PlaceInfo info) {
		if (raycastMode.isMode(RaycastMode.Normal)) return true;
		Vec3 eyes = mc.player.getEyePosition();
		Vec3 end = eyes.add(WorldUtils.getPlayerLookVec(serverYaw, serverPitch)
				.scale(placeRange.getValue() + 0.75D));
		HitResult result = mc.level.clip(new ClipContext(
				eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
		return result instanceof BlockHitResult hit
				&& info.neighbor.equals(hit.getBlockPos())
				&& info.face == hit.getDirection();
	}

	private boolean doPlace(PlaceInfo info) {
		ItemStack held = mc.player.getMainHandItem();
		if (!(held.getItem() instanceof BlockItem)) return false;

		// 26.2 sends interaction and rotation separately. Send the settled rotation first
		// so the server evaluates the same face that Crow's UpdateEvent raycast selected.
		mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
				serverYaw, serverPitch, mc.player.onGround(), mc.player.horizontalCollision));
		BlockHitResult hit = new BlockHitResult(info.hitVec, info.face, info.neighbor, false);
		InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
		if (result.consumesAction() && swing.getValue()) mc.player.swing(InteractionHand.MAIN_HAND);
		return result.consumesAction();
	}

	private int findBestBlockSlot() {
		int woolSlot = -1;
		int bestSlot = -1;
		int bestCount = 0;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = mc.player.getInventory().getItem(slot);
			if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)
					|| !isFullBlock(blockItem.getBlock())) {
				continue;
			}
			if (stack.is(ItemTags.WOOL) && woolSlot == -1) woolSlot = slot;
			if (stack.getCount() > bestCount) {
				bestCount = stack.getCount();
				bestSlot = slot;
			}
		}
		return woolSlot != -1 ? woolSlot : bestSlot;
	}

	private boolean isHoldingPlaceableBlock() {
		ItemStack held = mc.player.getMainHandItem();
		return !held.isEmpty() && held.getItem() instanceof BlockItem blockItem
				&& isFullBlock(blockItem.getBlock());
	}

	private boolean isFullBlock(Block block) {
		BlockPos sample = anchorPos != null ? anchorPos : mc.player.blockPosition();
		return block != Blocks.AIR
				&& block.defaultBlockState().isCollisionShapeFullBlock(mc.level, sample);
	}

	private boolean isReplaceable(BlockPos pos) {
		return mc.level.getBlockState(pos).canBeReplaced();
	}

	private boolean isSolid(BlockPos pos) {
		BlockState state = mc.level.getBlockState(pos);
		return !state.isAir() && state.getBlock() != Blocks.WATER && state.getBlock() != Blocks.LAVA;
	}

	private float[] computePlacementAngles(Vec3 target) {
		Vec3 delta = target.subtract(mc.player.getEyePosition());
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
		return new float[]{yaw, Mth.clamp(pitch, -89.5F, 89.5F)};
	}

	private float[] applyPlacementRandomization(float yaw, float pitch, BlockPos targetPos) {
		long seed = Double.doubleToLongBits(targetPos.getX())
				^ Double.doubleToLongBits(targetPos.getY()) * 0x9E3779B97F4A7C15L
				^ Double.doubleToLongBits(targetPos.getZ()) * 0xBF58476D1CE4E5B9L;
		float yawFactor = ((seed & 0xFFFF) / 65535.0F) - 0.5F;
		float pitchFactor = (((seed >>> 16) & 0xFFFF) / 65535.0F) - 0.5F;
		float strength = Math.max(MIN_RANDOM_OFFSET, randomOffsetStrength.getValueFloat());
		float yawAmplitude = Math.min(0.45F, strength * 0.6F);
		float pitchAmplitude = Math.min(0.30F, strength * 0.45F);
		return new float[]{
				yaw + yawFactor * 2.0F * yawAmplitude,
				Mth.clamp(pitch + pitchFactor * 2.0F * pitchAmplitude, -89.5F, 89.5F)
		};
	}

	private float[] applyUniquenessOffset(float yaw, float pitch, BlockPos targetPos) {
		long seed = Double.doubleToLongBits(targetPos.getX()) * 0xC2B2AE3D27D4EB4FL
				^ Double.doubleToLongBits(targetPos.getY()) * 0x165667B19E3779F9L
				^ Double.doubleToLongBits(targetPos.getZ()) * 0xD6E8FEB86659FD93L;
		float yawFactor = ((seed & 0xFFFF) / 65535.0F) - 0.5F;
		float pitchFactor = (((seed >>> 16) & 0xFFFF) / 65535.0F) - 0.5F;
		return new float[]{
				yaw + yawFactor * 0.16F,
				Mth.clamp(pitch + pitchFactor * 0.10F, -89.5F, 89.5F)
		};
	}

	private void seedRotation() {
		serverYaw = mc.player.getYRot();
		serverPitch = mc.player.getXRot();
		yawVelocity = 0.0F;
		pitchVelocity = 0.0F;
		rotationSeeded = true;
	}

	private void stepAim(float targetYaw, float targetPitch) {
		if (!rotationSeeded) seedRotation();
		float speed = rotationSpeed.getValueFloat();
		float progress = Mth.clamp((speed - 2.0F) / 58.0F, 0.0F, 1.0F);
		float stiffness = 0.18F + progress * 0.52F;
		float yawError = Mth.wrapDegrees(targetYaw - serverYaw);
		float pitchError = targetPitch - serverPitch;
		float yawStep = stepAxis(yawError, speed, 0.8F, stiffness, 0.86F, true);
		float pitchStep = stepAxis(pitchError, speed * 0.85F, 0.8F, stiffness, 0.86F, false);
		yawStep = snapStepToMouseGcd(yawStep, yawError);
		pitchStep = snapStepToMouseGcd(pitchStep, pitchError);
		serverYaw += yawStep;
		serverPitch = Mth.clamp(serverPitch + pitchStep, -89.5F, 89.5F);

		// Crow's PLACE profile keeps the rendered head/body following the server
		// rotation even when the first-person camera is silent.
		mc.player.yHeadRotO = mc.player.yHeadRot;
		mc.player.setYHeadRot(serverYaw);
		mc.player.yBodyRotO = mc.player.yBodyRot;
		mc.player.setYBodyRot(mc.player.yBodyRot
				+ Mth.wrapDegrees(serverYaw - mc.player.yBodyRot) * 0.4F);
	}

	private float stepAxis(float error, float cap, float minimumSpeed, float stiffness,
			float damping, boolean yawAxis) {
		float velocity = yawAxis ? yawVelocity : pitchVelocity;
		float distanceScale = (float) Math.min(1.0D,
				Math.log(1.0D + Math.abs(error) / 6.0D) / Math.log(11.0D));
		float effectiveCap = Math.max(minimumSpeed, cap * (0.20F + 0.80F * distanceScale));
		velocity += stiffness * error - damping * velocity;
		velocity = Mth.clamp(velocity, -effectiveCap, effectiveCap);
		velocity += (float) ((ThreadLocalRandom.current().nextDouble()
				- ThreadLocalRandom.current().nextDouble()) * effectiveCap * 0.02D);
		if (yawAxis) yawVelocity = velocity;
		else pitchVelocity = velocity;
		return velocity;
	}

	private float snapStepToMouseGcd(float rawStep, float remaining) {
		double sensitivity = mc.options.sensitivity().get() * 0.6000000238418579D
				+ 0.20000000298023224D;
		double gcd = sensitivity * sensitivity * sensitivity * 8.0D * 0.15D;
		if (gcd <= 0.0D) return rawStep;
		long multiples = Math.round(rawStep / gcd);
		double snapped = multiples * gcd;
		if (Math.signum(snapped) == Math.signum(remaining)
				&& Math.abs(snapped) > Math.abs(remaining)) {
			long maximum = (long) Math.floor(Math.abs(remaining) / gcd);
			snapped = Math.copySign(maximum * gcd, remaining);
		}
		return (float) snapped;
	}

	private boolean rotationReady() {
		if (currentTarget == null) return false;
		float yawDifference = Math.abs(Mth.wrapDegrees(currentTarget.rotationYaw - serverYaw));
		float pitchDifference = Math.abs(currentTarget.rotationPitch - serverPitch);
		float threshold = grimBypass.getValue() ? 0.6F : 2.0F;
		return yawDifference <= threshold && pitchDifference <= threshold;
	}

	private int nextDelayTicks() {
		return placeDelay.getValueInt() + ThreadLocalRandom.current().nextInt(0, 2);
	}

	private void finish() {
		resetRuntime(true);
		if (autoDisable.getValue()) setEnabled(false);
	}

	private void resetRuntime(boolean restoreSlot) {
		restoreTemporaryRotation();
		if (restoreSlot && previousSlot != -1 && mc.player != null) {
			InventoryUtils.setInvSlot(previousSlot);
		}
		placeQueue.clear();
		if (restoreSlot) previousSlot = -1;
		active = false;
		anchorPos = null;
		currentTarget = null;
		delayTicks = 0;
		rotationSeeded = false;
		yawVelocity = 0.0F;
		pitchVelocity = 0.0F;
	}

	private void restoreTemporaryRotation() {
		if (!temporaryRotation) return;
		if (mc.player != null) {
			mc.player.setYRot(savedClientYaw);
			mc.player.setXRot(savedClientPitch);
		}
		temporaryRotation = false;
	}

	private static final class PlaceInfo {
		private final BlockPos targetPos;
		private final BlockPos neighbor;
		private final Direction face;
		private final Vec3 hitVec;
		private float rotationYaw;
		private float rotationPitch;

		private PlaceInfo(BlockPos targetPos, BlockPos neighbor, Direction face, Vec3 hitVec) {
			this.targetPos = targetPos;
			this.neighbor = neighbor;
			this.face = face;
			this.hitVec = hitVec;
		}
	}
}
