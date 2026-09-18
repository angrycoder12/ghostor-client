package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Crow's automatic MLG-water helper, preserving its look-down and void-check conditions. */
public final class WaterBucket extends Module implements TickListener {
	private final NumberSetting fallDistance = new NumberSetting(
			EncryptedString.of("Fall dist"), 1.0D, 10.0D, 3.0D, 0.1D);
	private boolean handling;

	public WaterBucket() {
		super(EncryptedString.of("Water bucket"),
				EncryptedString.of("Auto MLG water. Disabled in the Nether."),
				-1,
				Category.MISC);
		addSettings(fallDistance);
	}

	@Override
	public void onEnable() {
		handling = false;
		if (isInNether()) {
			setEnabled(false);
			return;
		}
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		handling = false;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gameMode == null || mc.isPaused()) {
			handling = false;
			return;
		}
		if (isInNether()) {
			setEnabled(false);
			return;
		}

		if (isInTriggerPosition() && holdWaterBucket()) handling = true;
		if (!handling) return;

		performMlg();
		if (mc.player.onGround() || mc.player.getDeltaMovement().y > 0.0D) resetAfterFall();
	}

	private boolean isInTriggerPosition() {
		if (mc.player.getDeltaMovement().y >= -0.6D || mc.player.onGround()
				|| mc.player.getAbilities().flying || mc.player.getAbilities().instabuild
				|| handling || mc.player.fallDistance <= fallDistance.getValueFloat()) {
			return false;
		}

		BlockPos playerPos = mc.player.blockPosition();
		for (int distance = 1; distance < 3; distance++) {
			BlockPos below = playerPos.below(distance);
			if (mc.level.getBlockState(below).isFaceSturdy(mc.level, below, Direction.UP)) return false;
		}
		return true;
	}

	private boolean holdWaterBucket() {
		if (contains(mc.player.getMainHandItem(), Items.WATER_BUCKET)) return true;
		for (int slot = 0; slot < 9; slot++) {
			if (contains(mc.player.getInventory().getItem(slot), Items.WATER_BUCKET)) {
				InventoryUtils.setInvSlot(slot);
				return true;
			}
		}
		return false;
	}

	private void performMlg() {
		if (!contains(mc.player.getMainHandItem(), Items.WATER_BUCKET)
				|| mc.player.getXRot() < 70.0F) {
			return;
		}
		if (mc.hitResult instanceof BlockHitResult hit && hit.getDirection() == Direction.UP) {
			mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
		}
	}

	private void resetAfterFall() {
		if (contains(mc.player.getMainHandItem(), Items.BUCKET)) {
			mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
		}
		handling = false;
	}

	private boolean isInNether() {
		return mc.level != null && mc.level.dimension() == Level.NETHER;
	}

	private static boolean contains(ItemStack stack, Item item) {
		return stack != null && !stack.isEmpty() && stack.is(item);
	}
}
