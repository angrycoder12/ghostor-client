package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.event.events.MouseUpdateListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.imixin.IKeyBinding;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import dev.lvstrng.argon.utils.PlacementUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class Scaffold extends Module implements TickListener, HudListener, GameRenderListener, MouseUpdateListener {
	/* Keep this setting order stable: profiles save module settings by index. */
	private final BooleanSetting shift = new BooleanSetting(EncryptedString.of("Shift"), true);
	private final NumberSetting rotationSpeed = new NumberSetting(
			EncryptedString.of("Rotation speed"), 120, 720, 360, 1);
	private final BooleanSetting render = new BooleanSetting(EncryptedString.of("Render"), true);
	private final NumberSetting renderRed = new NumberSetting(
			EncryptedString.of("RenderColor-R"), 0, 255, 255, 1);
	private final NumberSetting renderGreen = new NumberSetting(
			EncryptedString.of("RenderColor-G"), 0, 255, 0, 1);
	private final NumberSetting renderBlue = new NumberSetting(
			EncryptedString.of("RenderColor-B"), 0, 255, 0, 1);
	private final NumberSetting renderAlpha = new NumberSetting(
			EncryptedString.of("RenderAlpha"), 0, 255, 100, 1);
	/* Appended so existing index-based Scaffold profile values retain their meaning. */
	private final BooleanSetting forwardFacingPov = new BooleanSetting(
			EncryptedString.of("Forward facing POV"), true);

	private static final double EDGE_START_DISTANCE = 0.085D;
	private static final double CRITICAL_EDGE_DISTANCE = 0.025D;
	private static final long CROUCH_DELAY_MIN_MS = 8L;
	private static final long CROUCH_DELAY_MAX_MS = 25L;
	private static final long RELEASE_DELAY_MIN_MS = 35L;
	private static final long RELEASE_DELAY_MAX_MS = 75L;
	private static final long PLACE_DELAY_MIN_MS = 25L;
	private static final long PLACE_DELAY_MAX_MS = 70L;
	private static final long SWITCH_DELAY_MIN_MS = 20L;
	private static final long SWITCH_DELAY_MAX_MS = 65L;
	private static final long POST_PLACE_RELEASE_MIN_MS = 20L;
	private static final long POST_PLACE_RELEASE_MAX_MS = 50L;
	private static final long UNSHIFT_GRACE_MIN_MS = 25L;
	private static final long UNSHIFT_GRACE_MAX_MS = 55L;
	private static final long AIM_SPEED_CHANGE_MIN_MS = 180L;
	private static final long AIM_SPEED_CHANGE_MAX_MS = 320L;
	private static final long FORWARD_AIM_CONFIRM_MIN_MS = 50L;
	private static final long FORWARD_AIM_CONFIRM_MAX_MS = 70L;
	private static final long FORWARD_BRIDGE_CONTINUITY_MS = 400L;
	private static final long FORWARD_LAND_CONFIRM_MS = 120L;
	/* Keep S-bridging on the player's chosen heading instead of turning toward a not-yet-visible face. */
	private static final float MAX_HORIZONTAL_AIM_CORRECTION = 6.0F;
	private static final float VISIBLE_FACE_YAW_LIMIT = 75.0F;
	private static final float FORWARD_MOVEMENT_YAW_TOLERANCE = 12.0F;

	private int blockCount;
	private int originalSlot = -1;
	private int moduleSelectedSlot = -1;
	private BlockPos activeSupport;
	private BlockPos targetBlock;
	private Direction activeFace;
	private Direction bridgeFace;
	private BlockPos jumpSupport;
	private BlockPos jumpTarget;
	private Direction jumpFace;
	private BlockPos lastPlacedBlock;
	private boolean forcingSneak;
	private boolean blockingBackward;
	private boolean forwardMovementRemapped;
	private boolean edgeEngaged;
	private boolean jumpGuard;
	private boolean forwardPovActive;
	private boolean forwardPovReturning;
	private boolean wasGrounded;
	private long crouchAt;
	private long releaseAt;
	private long placeAt;
	private long switchAt;
	private long postPlaceReleaseAt;
	private long suppressCrouchUntil;
	private long nextAimSpeedAt;
	private long forwardAimConfirmedAt;
	private long forwardPovContinuityUntil;
	private long forwardPovLandCandidateAt;
	private int pendingSwitchSlot = -1;
	private float horizontalAimSpeed;
	private float verticalAimSpeed;
	private float bridgeHeadingYaw;
	private float forwardPovViewYaw;
	private float forwardPovViewPitch;
	private float forwardPovRealYaw;
	private float forwardPovRealPitch;
	private BlockPos forwardAimConfirmedTarget;
	private final Set<BlockPos> forwardPovPlacedBlocks = new HashSet<>();
	private boolean bridgeHeadingSet;
	private LocalPlayer observedPlayer;
	private ClientLevel observedLevel;
	private static Scaffold instance;

	public Scaffold() {
		super(EncryptedString.of("Scaffold"),
				EncryptedString.of("Automatically places blocks while moving"),
				-1,
				Category.MISC);
		addSettings(shift, rotationSpeed, render, renderRed, renderGreen, renderBlue, renderAlpha,
				forwardFacingPov);
		instance = this;
	}

	@Override
	public void onEnable() {
		/* Run after other tick modules so Scaffold's edge-safety input wins while active. */
		eventManager.add(TickListener.class, this, -1000);
		/* Aim after other HUD listeners so combat Aim Assist cannot override bridge aiming. */
		eventManager.add(HudListener.class, this, -1000);
		eventManager.add(GameRenderListener.class, this);
		/* Restore the real bridge aim after vanilla mouse input while the camera is detached. */
		eventManager.add(MouseUpdateListener.class, this, -1000);
		resetState();
		resetAimSpeeds();
		if (mc.player != null) {
			originalSlot = mc.player.getInventory().getSelectedSlot();
			wasGrounded = mc.player.onGround();
		}
		observedPlayer = mc.player;
		observedLevel = mc.level;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(HudListener.class, this);
		eventManager.remove(GameRenderListener.class, this);
		eventManager.remove(MouseUpdateListener.class, this);
		stopForwardPov(true);
		restoreForcedInput();
		restoreOriginalSlot();
		resetState();
		super.onDisable();
	}

	@Override
	public void onTick() {
		synchronizeWorldContext();
		if (!canRun()) {
			clearTransientSafety();
			wasGrounded = mc.player != null && mc.player.onGround();
			return;
		}
		if (!forwardFacingPov.getValue() && isForwardPovEngaged()) {
			stopForwardPov(true);
		}
		if (isForwardPovEngaged()) {
			applyForwardPovMovement();
			applyForwardPovRealRotation();
		}

		blockCount = PlacementUtils.countHotbarBlocks();
		boolean attacking = ((IKeyBinding) mc.options.keyAttack).isActuallyPressed();
		boolean holdingBlocks = isUsableBlock(mc.player.getMainHandItem());
		if (attacking) {
			clearPendingSwitch();
		} else if (!holdingBlocks) {
			holdingBlocks = ensureBlockSelected();
		} else {
			clearPendingSwitch();
		}

		boolean grounded = mc.player.onGround();
		if (wasGrounded && !grounded && edgeEngaged && activeSupport != null && activeFace != null) {
			rememberBridgeHeading();
			jumpGuard = true;
			jumpSupport = activeSupport;
			jumpTarget = activeSupport.above();
			jumpFace = Direction.UP;
			placeAt = System.currentTimeMillis() + randomDelay(PLACE_DELAY_MIN_MS, PLACE_DELAY_MAX_MS);
		}

		if (jumpGuard) {
			forceSneakNow();
			setBackwardBlocked(true);
			if (isValidJumpTarget()) {
				activeSupport = jumpSupport;
				targetBlock = jumpTarget;
				activeFace = jumpFace;
			} else {
				clearPlacementTarget();
			}

			if (holdingBlocks && !attacking && activeSupport != null
					&& isPlayerOverPlacementTarget(targetBlock)) {
				placeThroughRealCrosshair();
			}

			if (grounded) {
				jumpGuard = false;
				setBackwardBlocked(false);
				clearJumpTarget();
			}
		} else {
			setBackwardBlocked(false);
			long now = System.currentTimeMillis();
			boolean releasingAfterPlacement = updatePostPlacementRelease(now, grounded);

			if (!edgeEngaged && !forcingSneak && !releasingAfterPlacement) {
				/* While safely on a block, the player remains free to choose a new bridge heading. */
				bridgeFace = resolveIntendedBridgeFace();
				if (!isForwardPovEngaged()) {
					bridgeHeadingYaw = mc.player.getYRot();
					bridgeHeadingSet = true;
				}
			}

			EdgeTarget edge = grounded ? findEdgeTarget() : null;
			if (edge != null) {
				if (shouldStartForwardPov()) {
					startForwardPov();
				}
				if (forwardPovActive) {
					forwardPovLandCandidateAt = 0L;
				}
				setPlacementTarget(edge);
				edgeEngaged = true;
				double effectiveGap = Math.min(edge.gap(), edge.predictedGap());
				if (now < suppressCrouchUntil && effectiveGap > CRITICAL_EDGE_DISTANCE) {
					setSneak(false);
				} else if (!releasingAfterPlacement || effectiveGap <= CRITICAL_EDGE_DISTANCE) {
					/* Prediction forces the safety action before a fast step crosses the edge. */
					updateSneakForEdge(effectiveGap);
				}
			} else {
				edgeEngaged = false;
				clearPlacementTarget();
				if (!releasingAfterPlacement) {
					scheduleSafeRelease();
					if (forwardPovActive && isForwardPovLandConfirmed(now)) {
						beginForwardPovReturn();
					}
				}
			}

			if (holdingBlocks && !attacking && activeSupport != null && activeFace != null
					&& targetBlock != null && (forcingSneak || isSneakPhysicallyHeld() || edgeEngaged)) {
				placeThroughRealCrosshair();
			}
		}

		wasGrounded = grounded;
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.player == null || mc.level == null || mc.gui.screen() != null) {
			return;
		}
		if (canRun() && forwardPovReturning) {
			updateForwardPovReturn();
		} else if (canRun() && shouldAim()) {
			aimAtSupportFaceLikeAimAssist();
		}
		String text = blockCount + " blocks";
		int x = event.context.guiWidth() / 2 + 8 - mc.font.width(text) / 2;
		int y = event.context.guiHeight() / 2 - 4;
		event.context.text(mc.font, text, x, y, blockCount <= 16 ? 0xFFFF0000 : 0xFFFFFFFF, false);
	}

	@Override
	public void onMouseUpdate() {
		/*
		 * Vanilla applies mouse input to LocalPlayer. During forward POV the first-person
		 * camera is intentionally separate, so immediately restore the real scaffold aim.
		 * This prevents a mouse sample between render and tick from leaking a false rotation
		 * into F5 or the next ordinary movement packet.
		 */
		if (isForwardPovEngaged() && canRun()) {
			applyForwardPovRealRotation();
		}
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (!render.getValue() || targetBlock == null || mc.player == null || mc.level == null) {
			return;
		}

		Color fill = new Color(renderRed.getValueInt(), renderGreen.getValueInt(),
				renderBlue.getValueInt(), renderAlpha.getValueInt());
		Color outline = new Color(renderRed.getValueInt(), renderGreen.getValueInt(),
				renderBlue.getValueInt(), 255);
		AABB box = new AABB(targetBlock);
		RenderUtils.renderFilledBox(box, fill);
		RenderUtils.renderBoxOutline(box, outline, 2.0F);
	}

	private boolean canRun() {
		return mc.player != null && mc.level != null && mc.gameMode != null
				&& mc.gui.screen() == null && !mc.player.isSpectator() && !mc.player.isDeadOrDying()
				&& !mc.player.getAbilities().flying && Freecam.enabledInstance() == null;
	}

	private void synchronizeWorldContext() {
		if (observedPlayer == mc.player && observedLevel == mc.level) {
			return;
		}

		clearTransientSafety();
		observedPlayer = mc.player;
		observedLevel = mc.level;
		moduleSelectedSlot = -1;
		originalSlot = mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot();
		wasGrounded = mc.player != null && mc.player.onGround();
	}

	private boolean ensureBlockSelected() {
		if (isUsableBlock(mc.player.getMainHandItem())) {
			clearPendingSwitch();
			return true;
		}

		Inventory inventory = mc.player.getInventory();
		int bestSlot = -1;
		int largestStack = 0;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isUsableBlock(stack) && stack.getCount() > largestStack) {
				bestSlot = slot;
				largestStack = stack.getCount();
			}
		}

		if (bestSlot < 0 || bestSlot == inventory.getSelectedSlot()) {
			clearPendingSwitch();
			return false;
		}

		long now = System.currentTimeMillis();
		if (pendingSwitchSlot != bestSlot) {
			pendingSwitchSlot = bestSlot;
			switchAt = now + randomDelay(SWITCH_DELAY_MIN_MS, SWITCH_DELAY_MAX_MS);
			return false;
		}
		if (now < switchAt) {
			return false;
		}

		InventoryUtils.setInvSlot(bestSlot);
		moduleSelectedSlot = bestSlot;
		clearPendingSwitch();
		return isUsableBlock(mc.player.getMainHandItem());
	}

	private void clearPendingSwitch() {
		pendingSwitchSlot = -1;
		switchAt = 0L;
	}

	private boolean isUsableBlock(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() != Blocks.AIR;
	}

	private EdgeTarget findEdgeTarget() {
		BlockPos support = findSupportBlock();
		if (support == null || bridgeFace == null) {
			return null;
		}

		AABB bounds = mc.player.getBoundingBox();
		double gap = gapToFace(bounds, support, bridgeFace);
		double predictedGap = gap - Math.max(0.0D, outwardSpeed(bridgeFace));
		if (gap > EDGE_START_DISTANCE && predictedGap > EDGE_START_DISTANCE) {
			return null;
		}

		/* An occupied next block means this bridge step is complete; never search sideways. */
		BlockPos target = support.relative(bridgeFace);
		if (!mc.level.getBlockState(target).canBeReplaced()) {
			return null;
		}

		return new EdgeTarget(support, target, bridgeFace, gap, predictedGap);
	}

	private Direction resolveIntendedBridgeFace() {
		Direction facing = Direction.fromYRot(mc.player.getYRot());
		var keys = mc.player.input.keyPresses;
		if (keys.backward() && !keys.forward()) {
			return facing.getOpposite();
		}
		if (keys.forward() && !keys.backward()) {
			return facing;
		}
		if (keys.left() && !keys.right()) {
			return facing.getCounterClockWise();
		}
		if (keys.right() && !keys.left()) {
			return facing.getClockWise();
		}
		return bridgeFace == null ? facing : bridgeFace;
	}

	private BlockPos findSupportBlock() {
		BlockPos support = mc.player.mainSupportingBlockPos.orElse(null);
		if (isSupport(support)) {
			return support;
		}

		BlockPos fallback = BlockPos.containing(
				mc.player.getX(), mc.player.getBoundingBox().minY - 0.08D, mc.player.getZ());
		return isSupport(fallback) ? fallback : null;
	}

	private boolean isSupport(BlockPos pos) {
		return pos != null && !mc.level.getBlockState(pos).canBeReplaced()
				&& !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
	}

	private double gapToFace(AABB bounds, BlockPos support, Direction face) {
		return switch (face) {
			case WEST -> bounds.minX - support.getX();
			case EAST -> support.getX() + 1.0D - bounds.maxX;
			case NORTH -> bounds.minZ - support.getZ();
			case SOUTH -> support.getZ() + 1.0D - bounds.maxZ;
			default -> Double.MAX_VALUE;
		};
	}

	private double outwardSpeed(Direction face) {
		Vec3 velocity = mc.player.getDeltaMovement();
		return switch (face) {
			case WEST -> -velocity.x;
			case EAST -> velocity.x;
			case NORTH -> -velocity.z;
			case SOUTH -> velocity.z;
			default -> 0.0D;
		};
	}

	private void setPlacementTarget(EdgeTarget edge) {
		rememberBridgeHeading();
		boolean changed = !edge.support().equals(activeSupport) || edge.face() != activeFace;
		activeSupport = edge.support();
		targetBlock = edge.target();
		activeFace = edge.face();
		if (changed) {
			clearForwardAimConfirmation();
			placeAt = System.currentTimeMillis() + randomDelay(PLACE_DELAY_MIN_MS, PLACE_DELAY_MAX_MS);
		}
	}

	private void updateSneakForEdge(double gap) {
		releaseAt = 0L;
		if (!shift.getValue()) {
			setSneak(false);
			return;
		}

		long now = System.currentTimeMillis();
		if (gap <= CRITICAL_EDGE_DISTANCE) {
			forceSneakNow();
			return;
		}

		if (!forcingSneak && crouchAt == 0L) {
			crouchAt = now + randomDelay(CROUCH_DELAY_MIN_MS, CROUCH_DELAY_MAX_MS);
		}
		if (forcingSneak || now >= crouchAt) {
			setSneak(true);
			crouchAt = 0L;
		}
	}

	private void forceSneakNow() {
		crouchAt = 0L;
		releaseAt = 0L;
		setSneak(true);
	}

	private void scheduleSafeRelease() {
		crouchAt = 0L;
		if (!forcingSneak) {
			return;
		}

		long now = System.currentTimeMillis();
		if (releaseAt == 0L) {
			releaseAt = now + randomDelay(RELEASE_DELAY_MIN_MS, RELEASE_DELAY_MAX_MS);
		}
		if (now >= releaseAt && hasSafeFooting()) {
			setSneak(false);
			releaseAt = 0L;
		}
	}

	private boolean updatePostPlacementRelease(long now, boolean grounded) {
		if (lastPlacedBlock == null) {
			return false;
		}
		if (!grounded || now < postPlaceReleaseAt || !isSolidPlacedBlock(lastPlacedBlock)) {
			return true;
		}

		setSneak(false);
		releaseAt = 0L;
		crouchAt = 0L;
		suppressCrouchUntil = now + randomDelay(UNSHIFT_GRACE_MIN_MS, UNSHIFT_GRACE_MAX_MS);
		lastPlacedBlock = null;
		postPlaceReleaseAt = 0L;
		return false;
	}

	private boolean isSolidPlacedBlock(BlockPos pos) {
		return !mc.level.getBlockState(pos).canBeReplaced()
				&& !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
	}

	private boolean hasSafeFooting() {
		return mc.player.onGround()
				&& !mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(0.0D, -0.08D, 0.0D));
	}

	private boolean isForwardPovLandConfirmed(long now) {
		if (!hasSafeFooting() || now < forwardPovContinuityUntil) {
			forwardPovLandCandidateAt = 0L;
			return false;
		}

		BlockPos support = findSupportBlock();
		boolean onSessionBridge = support != null && (forwardPovPlacedBlocks.contains(support)
				|| bridgeFace != null && forwardPovPlacedBlocks.contains(support.relative(bridgeFace)));
		if (support == null || onSessionBridge) {
			/* The placed block underfoot or immediately ahead is bridge, not land. */
			forwardPovLandCandidateAt = 0L;
			return false;
		}

		if (forwardPovLandCandidateAt == 0L) {
			forwardPovLandCandidateAt = now;
			return false;
		}
		return now - forwardPovLandCandidateAt >= FORWARD_LAND_CONFIRM_MS;
	}

	private boolean isValidJumpTarget() {
		return jumpSupport != null && jumpTarget != null && jumpFace != null
				&& isSupport(jumpSupport) && mc.level.getBlockState(jumpTarget).canBeReplaced()
				&& jumpSupport.relative(jumpFace).equals(jumpTarget);
	}

	private boolean isPlayerOverPlacementTarget(BlockPos pos) {
		if (pos == null) {
			return false;
		}
		AABB bounds = mc.player.getBoundingBox();
		return bounds.maxX > pos.getX() + 0.01D && bounds.minX < pos.getX() + 0.99D
				&& bounds.maxZ > pos.getZ() + 0.01D && bounds.minZ < pos.getZ() + 0.99D;
	}

	private void clearJumpTarget() {
		jumpSupport = null;
		jumpTarget = null;
		jumpFace = null;
	}

	private boolean shouldAim() {
		return activeSupport != null && activeFace != null && targetBlock != null
				&& isUsableBlock(mc.player.getMainHandItem()) && (edgeEngaged || jumpGuard)
				&& (forcingSneak || isSneakPhysicallyHeld() || jumpGuard);
	}

	private boolean shouldStartForwardPov() {
		return !forwardPovActive && forwardFacingPov.getValue()
				&& ((IKeyBinding) mc.options.keyUp).isActuallyPressed()
				&& !((IKeyBinding) mc.options.keyDown).isActuallyPressed();
	}

	private boolean isForwardPovEngaged() {
		return forwardPovActive || forwardPovReturning;
	}

	public static boolean shouldOverrideForwardCamera() {
		return instance != null && instance.isEnabled() && instance.isForwardPovEngaged()
				&& instance.mc.player != null && instance.mc.level != null;
	}

	public static float forwardCameraYaw() {
		return instance == null ? 0.0F : instance.forwardPovViewYaw;
	}

	public static float forwardCameraPitch() {
		return instance == null ? 0.0F : instance.forwardPovViewPitch;
	}

	private void startForwardPov() {
		if (!isForwardPovEngaged()) {
			clearForwardPovBridgeSession();
			forwardPovViewYaw = mc.player.getYRot();
			forwardPovViewPitch = mc.player.getXRot();
			forwardPovRealYaw = forwardPovViewYaw;
			forwardPovRealPitch = forwardPovViewPitch;
		}

		forwardPovActive = true;
		forwardPovReturning = false;
		forwardPovLandCandidateAt = 0L;
		bridgeHeadingYaw = forwardPovViewYaw + 180.0F;
		bridgeHeadingSet = true;
		applyForwardPovMovement();
		applyForwardPovRealRotation();
	}

	private void beginForwardPovReturn() {
		if (!forwardPovActive) {
			return;
		}
		forwardPovActive = false;
		forwardPovReturning = true;
		setBackwardBlocked(false);
		applyForwardPovMovement();
	}

	private void updateForwardPovReturn() {
		if (!forwardPovReturning || mc.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now >= nextAimSpeedAt) {
			resetAimSpeeds();
		}

		forwardPovRealYaw = aimAssistLerp(
				horizontalAimSpeed / 50.0F, forwardPovRealYaw, forwardPovViewYaw);
		forwardPovRealPitch = Mth.clamp(aimAssistLerp(
				verticalAimSpeed / 50.0F, forwardPovRealPitch, forwardPovViewPitch), -90.0F, 90.0F);
		applyForwardPovRealRotation();

		if (Math.abs(Mth.wrapDegrees(forwardPovViewYaw - forwardPovRealYaw)) <= 1.0F
				&& Math.abs(forwardPovViewPitch - forwardPovRealPitch) <= 1.0F) {
			forwardPovRealYaw = forwardPovViewYaw;
			forwardPovRealPitch = forwardPovViewPitch;
			applyForwardPovRealRotation();
			forwardPovReturning = false;
			bridgeHeadingSet = false;
			restoreForwardPovMovement();
			clearForwardPovBridgeSession();
		}
	}

	private void stopForwardPov(boolean synchronizePlayer) {
		if (!isForwardPovEngaged()) {
			restoreForwardPovMovement();
			clearForwardPovBridgeSession();
			return;
		}
		if (synchronizePlayer && mc.player != null) {
			forwardPovRealYaw = forwardPovViewYaw;
			forwardPovRealPitch = forwardPovViewPitch;
			applyForwardPovRealRotation();
		}
		forwardPovActive = false;
		forwardPovReturning = false;
		bridgeHeadingSet = false;
		setBackwardBlocked(false);
		restoreForwardPovMovement();
		clearForwardPovBridgeSession();
	}

	private void clearForwardPovBridgeSession() {
		forwardPovPlacedBlocks.clear();
		forwardPovContinuityUntil = 0L;
		forwardPovLandCandidateAt = 0L;
	}

	private void applyForwardPovRealRotation() {
		if (mc.player == null) {
			return;
		}
		mc.player.setYRot(forwardPovRealYaw);
		mc.player.setXRot(forwardPovRealPitch);
	}

	private void applyForwardPovMovement() {
		if (mc.player == null || !isForwardPovEngaged()) {
			return;
		}

		/*
		 * A vanilla bridger turns in place before walking backward. Hold horizontal movement
		 * while the real player performs that smooth turn (and while returning on safe land),
		 * otherwise a fixed key swap would make the player arc sideways during the rotation.
		 */
		boolean alignedForBackwardBridge = forwardPovActive
				&& Math.abs(Mth.wrapDegrees(bridgeHeadingYaw - forwardPovRealYaw))
				<= FORWARD_MOVEMENT_YAW_TOLERANCE;
		if (!alignedForBackwardBridge) {
			mc.options.keyUp.setDown(false);
			mc.options.keyDown.setDown(false);
			mc.options.keyLeft.setDown(false);
			mc.options.keyRight.setDown(false);
			forwardMovementRemapped = true;
			return;
		}

		/* A 180-degree view offset requires the same 180-degree input transform. */
		boolean physicalForward = ((IKeyBinding) mc.options.keyUp).isActuallyPressed();
		boolean physicalBackward = ((IKeyBinding) mc.options.keyDown).isActuallyPressed();
		boolean physicalLeft = ((IKeyBinding) mc.options.keyLeft).isActuallyPressed();
		boolean physicalRight = ((IKeyBinding) mc.options.keyRight).isActuallyPressed();
		mc.options.keyUp.setDown(physicalBackward);
		mc.options.keyDown.setDown(physicalForward && !blockingBackward);
		mc.options.keyLeft.setDown(physicalRight);
		mc.options.keyRight.setDown(physicalLeft);
		forwardMovementRemapped = true;
	}

	private void restoreForwardPovMovement() {
		if (!forwardMovementRemapped) {
			return;
		}
		((IKeyBinding) mc.options.keyUp).resetPressed();
		((IKeyBinding) mc.options.keyDown).resetPressed();
		((IKeyBinding) mc.options.keyLeft).resetPressed();
		((IKeyBinding) mc.options.keyRight).resetPressed();
		forwardMovementRemapped = false;
	}

	private void aimAtSupportFaceLikeAimAssist() {
		long now = System.currentTimeMillis();
		if (now >= nextAimSpeedAt) {
			resetAimSpeeds();
		}

		Vec3 aimPoint = faceAimPoint(activeSupport, activeFace);
		Vec3 delta = aimPoint.subtract(mc.player.getEyePosition());
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		rememberBridgeHeading();
		float geometricYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
		float geometricOffset = Mth.wrapDegrees(geometricYaw - bridgeHeadingYaw);
		/*
		 * Before the player overhangs the edge, the side-face point is geometrically behind
		 * their eyes. Treating that point as a yaw target causes the old 180-degree swing and
		 * reverses S-key movement. Keep the original heading until that face is visible; even
		 * then, limit assistance to a small natural correction. Yaw is irrelevant for the
		 * straight-down jump target, so it also retains the bridge heading.
		 */
		float desiredYaw = bridgeHeadingYaw;
		if (activeFace != Direction.UP && Math.abs(geometricOffset) <= VISIBLE_FACE_YAW_LIMIT) {
			desiredYaw += Mth.clamp(geometricOffset,
					-MAX_HORIZONTAL_AIM_CORRECTION, MAX_HORIZONTAL_AIM_CORRECTION);
		}
		float desiredPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
		float yawStrength = horizontalAimSpeed / 50.0F;
		float pitchStrength = verticalAimSpeed / 50.0F;
		float currentYaw = forwardPovActive ? forwardPovRealYaw : mc.player.getYRot();
		float currentPitch = forwardPovActive ? forwardPovRealPitch : mc.player.getXRot();

		/* Aim Assist's wrapped-degree interpolation, applied every rendered HUD frame. */
		float yaw = aimAssistLerp(yawStrength, currentYaw, desiredYaw);
		float pitch = Mth.clamp(aimAssistLerp(pitchStrength, currentPitch, desiredPitch), -90.0F, 90.0F);
		if (forwardPovActive) {
			/* This is the real entity rotation; only the first-person camera is detached. */
			forwardPovRealYaw = yaw;
			forwardPovRealPitch = pitch;
			applyForwardPovRealRotation();
		} else {
			float boundedYawOffset = Mth.clamp(Mth.wrapDegrees(yaw - bridgeHeadingYaw),
					-MAX_HORIZONTAL_AIM_CORRECTION, MAX_HORIZONTAL_AIM_CORRECTION);
			mc.player.setYRot(bridgeHeadingYaw + boundedYawOffset);
			mc.player.setXRot(pitch);
		}
	}

	private void rememberBridgeHeading() {
		if (!bridgeHeadingSet && mc.player != null) {
			bridgeHeadingYaw = mc.player.getYRot();
			bridgeHeadingSet = true;
		}
	}

	private void resetAimSpeeds() {
		float configured = Mth.clamp(rotationSpeed.getValueFloat() / 100.0F, 1.2F, 7.2F);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		horizontalAimSpeed = (float) (configured * random.nextDouble(0.88D, 1.13D));
		verticalAimSpeed = (float) (configured * random.nextDouble(0.82D, 1.08D));
		nextAimSpeedAt = System.currentTimeMillis()
				+ randomDelay(AIM_SPEED_CHANGE_MIN_MS, AIM_SPEED_CHANGE_MAX_MS);
	}

	private float aimAssistLerp(float delta, float start, float end) {
		return start + Mth.wrapDegrees(end - start) * delta;
	}

	private Vec3 faceAimPoint(BlockPos support, Direction face) {
		double x = support.getX() + 0.5D;
		double y = support.getY() + 0.82D;
		double z = support.getZ() + 0.5D;
		double inset = 0.12D;

		if (face == Direction.UP) {
			y = support.getY() + 1.0D;
			x = Mth.clamp(mc.player.getX(), support.getX() + inset, support.getX() + 1.0D - inset);
			z = Mth.clamp(mc.player.getZ(), support.getZ() + inset, support.getZ() + 1.0D - inset);
		} else if (face == Direction.WEST || face == Direction.EAST) {
			x = face == Direction.WEST ? support.getX() : support.getX() + 1.0D;
			z = Mth.clamp(mc.player.getZ(), support.getZ() + inset, support.getZ() + 1.0D - inset);
		} else {
			z = face == Direction.NORTH ? support.getZ() : support.getZ() + 1.0D;
			x = Mth.clamp(mc.player.getX(), support.getX() + inset, support.getX() + 1.0D - inset);
		}
		return new Vec3(x, y, z);
	}

	private void placeThroughRealCrosshair() {
		long now = System.currentTimeMillis();
		if (now < placeAt) {
			return;
		}
		BlockHitResult hit = getPlacementHit();
		if (!isUsableBlock(mc.player.getMainHandItem())
				|| !mc.level.getBlockState(targetBlock).canBeReplaced()
				|| hit == null
				|| hit.getType() != HitResult.Type.BLOCK
				|| !hit.getBlockPos().equals(activeSupport)
				|| hit.getDirection() != activeFace
				|| !hit.getBlockPos().relative(hit.getDirection()).equals(targetBlock)) {
			clearForwardAimConfirmation();
			return;
		}
		if (forwardPovActive) {
			if (!targetBlock.equals(forwardAimConfirmedTarget)) {
				forwardAimConfirmedTarget = targetBlock;
				forwardAimConfirmedAt = now
						+ randomDelay(FORWARD_AIM_CONFIRM_MIN_MS, FORWARD_AIM_CONFIRM_MAX_MS);
				return;
			}
			if (now < forwardAimConfirmedAt) {
				return;
			}
		} else {
			clearForwardAimConfirmation();
		}

		InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
		if (result.consumesAction()) {
			BlockPos placed = targetBlock;
			if (forwardPovActive) {
				forwardPovPlacedBlocks.add(placed);
				forwardPovContinuityUntil = now + FORWARD_BRIDGE_CONTINUITY_MS;
				forwardPovLandCandidateAt = 0L;
			}
			mc.player.swing(InteractionHand.MAIN_HAND);
			placeAt = now + randomDelay(PLACE_DELAY_MIN_MS, PLACE_DELAY_MAX_MS);
			lastPlacedBlock = placed;
			postPlaceReleaseAt = now
					+ randomDelay(POST_PLACE_RELEASE_MIN_MS, POST_PLACE_RELEASE_MAX_MS);
			edgeEngaged = false;
			clearPlacementTarget();
		}
	}

	private BlockHitResult getPlacementHit() {
		if (!forwardPovActive) {
			return mc.hitResult instanceof BlockHitResult hit ? hit : null;
		}

		/*
		 * The rendered camera deliberately faces forward. Raycast from the real player
		 * rotation so the interaction, ordinary movement packets, F5 model, and server all
		 * agree on the exact support face being targeted.
		 */
		Vec3 eye = mc.player.getEyePosition();
		Vec3 look = WorldUtils.getPlayerLookVec(mc.player.getYRot(), mc.player.getXRot());
		Vec3 end = eye.add(look.scale(mc.player.blockInteractionRange()));
		HitResult result = mc.level.clip(new ClipContext(
				eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
		return result instanceof BlockHitResult hit ? hit : null;
	}

	private void setSneak(boolean shouldSneak) {
		forcingSneak = shouldSneak;
		mc.options.keyShift.setDown(shouldSneak || isSneakPhysicallyHeld());
	}

	private boolean isSneakPhysicallyHeld() {
		return ((IKeyBinding) mc.options.keyShift).isActuallyPressed();
	}

	private void setBackwardBlocked(boolean blocked) {
		if (blocked) {
			/* Logical backward is keyDown in both modes; W is remapped to it in forward POV. */
			mc.options.keyDown.setDown(false);
			blockingBackward = true;
		} else {
			if (blockingBackward) {
				blockingBackward = false;
				if (isForwardPovEngaged()) {
					applyForwardPovMovement();
				} else {
					((IKeyBinding) mc.options.keyDown).resetPressed();
				}
			}
		}
	}

	private void restoreForcedInput() {
		if (forcingSneak) {
			((IKeyBinding) mc.options.keyShift).resetPressed();
			forcingSneak = false;
		}
		setBackwardBlocked(false);
	}

	private void restoreOriginalSlot() {
		if (mc.player != null && originalSlot >= 0 && originalSlot < 9
				&& moduleSelectedSlot >= 0
				&& mc.player.getInventory().getSelectedSlot() == moduleSelectedSlot) {
			InventoryUtils.setInvSlot(originalSlot);
		}
	}

	private void clearPlacementTarget() {
		activeSupport = null;
		targetBlock = null;
		activeFace = null;
		placeAt = 0L;
		clearForwardAimConfirmation();
	}

	private void clearForwardAimConfirmation() {
		forwardAimConfirmedTarget = null;
		forwardAimConfirmedAt = 0L;
	}

	private void clearTransientSafety() {
		stopForwardPov(observedPlayer == mc.player && observedLevel == mc.level);
		restoreForcedInput();
		clearPlacementTarget();
		clearJumpTarget();
		clearPendingSwitch();
		edgeEngaged = false;
		jumpGuard = false;
		lastPlacedBlock = null;
		crouchAt = 0L;
		releaseAt = 0L;
		postPlaceReleaseAt = 0L;
		suppressCrouchUntil = 0L;
		bridgeHeadingSet = false;
	}

	private void resetState() {
		blockCount = 0;
		originalSlot = -1;
		moduleSelectedSlot = -1;
		activeSupport = null;
		targetBlock = null;
		activeFace = null;
		bridgeFace = null;
		jumpSupport = null;
		jumpTarget = null;
		jumpFace = null;
		lastPlacedBlock = null;
		forcingSneak = false;
		blockingBackward = false;
		forwardMovementRemapped = false;
		edgeEngaged = false;
		jumpGuard = false;
		forwardPovActive = false;
		forwardPovReturning = false;
		wasGrounded = false;
		crouchAt = 0L;
		releaseAt = 0L;
		placeAt = 0L;
		switchAt = 0L;
		postPlaceReleaseAt = 0L;
		suppressCrouchUntil = 0L;
		nextAimSpeedAt = 0L;
		forwardAimConfirmedAt = 0L;
		pendingSwitchSlot = -1;
		horizontalAimSpeed = 0.0F;
		verticalAimSpeed = 0.0F;
		bridgeHeadingYaw = 0.0F;
		forwardPovViewYaw = 0.0F;
		forwardPovViewPitch = 0.0F;
		forwardPovRealYaw = 0.0F;
		forwardPovRealPitch = 0.0F;
		forwardAimConfirmedTarget = null;
		clearForwardPovBridgeSession();
		bridgeHeadingSet = false;
		observedPlayer = null;
		observedLevel = null;
	}

	private long randomDelay(long minimum, long maximum) {
		return ThreadLocalRandom.current().nextLong(minimum, maximum + 1L);
	}

	private record EdgeTarget(BlockPos support, BlockPos target, Direction face, double gap, double predictedGap) {
	}
}
