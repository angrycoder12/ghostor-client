package dev.lvstrng.argon.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.lvstrng.argon.Argon.mc;

public final class PlacementUtils {
	private PlacementUtils() {}

	public static int findBlockSlot() {
		if (mc.player == null) {
			return -1;
		}

		Inventory inventory = mc.player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
					&& blockItem.getBlock() != Blocks.AIR) {
				return slot;
			}
		}
		return -1;
	}

	public static int countHotbarBlocks() {
		if (mc.player == null) {
			return 0;
		}

		int count = 0;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = mc.player.getInventory().getItem(slot);
			if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
					&& blockItem.getBlock() != Blocks.AIR) {
				count += stack.getCount();
			}
		}
		return count;
	}

	public static PlacementTarget findSupport(BlockPos target) {
		if (mc.level == null || !mc.level.getBlockState(target).isAir()) {
			return null;
		}

		BlockPos[] neighbors = {
				target.below(), target.north(), target.south(), target.east(), target.west()
		};
		Direction[] faces = {
				Direction.UP, Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST
		};

		for (int index = 0; index < neighbors.length; index++) {
			if (!mc.level.getBlockState(neighbors[index]).isAir()) {
				return new PlacementTarget(target, neighbors[index], faces[index]);
			}
		}
		return null;
	}

	public static float[] rotations(PlacementTarget target) {
		if (mc.player == null) {
			return new float[] {0.0F, 0.0F};
		}

		Vec3 hit = Vec3.atCenterOf(target.support()).add(
				target.face().getStepX() * 0.5D,
				target.face().getStepY() * 0.5D,
				target.face().getStepZ() * 0.5D);
		Vec3 delta = hit.subtract(mc.player.getEyePosition());
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
		return new float[] {
				mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()),
				mc.player.getXRot() + Mth.wrapDegrees(pitch - mc.player.getXRot())
		};
	}

	public static boolean place(PlacementTarget target, int slot, float yaw, float pitch) {
		if (mc.player == null || mc.level == null || mc.gameMode == null
				|| slot < 0 || slot > 8 || target == null
				|| !mc.level.getBlockState(target.target()).isAir()) {
			return false;
		}

		int previousSlot = mc.player.getInventory().getSelectedSlot();
		try {
			if (slot != previousSlot) {
				InventoryUtils.setInvSlot(slot);
			}
			mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
					yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));

			BlockHitResult hit = new BlockHitResult(
					Vec3.atCenterOf(target.support()), target.face(), target.support(), false);
			InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
			if (result.consumesAction()) {
				mc.player.swing(InteractionHand.MAIN_HAND);
				return true;
			}
			return false;
		} finally {
			if (mc.player != null && previousSlot != mc.player.getInventory().getSelectedSlot()) {
				InventoryUtils.setInvSlot(previousSlot);
			}
		}
	}

	public record PlacementTarget(BlockPos target, BlockPos support, Direction face) {}
}
