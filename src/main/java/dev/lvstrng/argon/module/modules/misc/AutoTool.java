package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.imixin.IKeyBinding;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class AutoTool extends Module implements TickListener {
	private final BooleanSetting hotkeyBack = new BooleanSetting(
			EncryptedString.of("Hotkey back"), true);
	private final MinMaxSetting mineDelay = new MinMaxSetting(
			EncryptedString.of("Mine delay"), 0, 2000, 1, 10, 50);

	private Block previousBlock;
	private boolean waiting;
	private boolean mining;
	private int previousSlot = -1;
	private long readyAt;

	public AutoTool() {
		super(EncryptedString.of("Auto-Tool"),
				EncryptedString.of("Automatically switches to the best tool for the block"),
				-1,
				Category.MISC);
		addSettings(hotkeyBack, mineDelay);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		resetTarget();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		finishMining();
		resetTarget();
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gui.screen() != null) {
			finishMining();
			resetTarget();
			return;
		}

		if (!((IKeyBinding) mc.options.keyAttack).isActuallyPressed()) {
			finishMining();
			resetTarget();
			return;
		}

		if (!(mc.hitResult instanceof BlockHitResult hit)
				|| hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos pos = hit.getBlockPos();
		BlockState state = mc.level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return;
		}

		Block block = state.getBlock();
		if (previousBlock != block) {
			previousBlock = block;
			waiting = mineDelay.getMaxValue() > 0.0D;
			readyAt = System.currentTimeMillis() + (long) mineDelay.getRandomValue();
			if (waiting) {
				return;
			}
		}

		if (waiting) {
			if (System.currentTimeMillis() < readyAt) {
				return;
			}
			waiting = false;
		}

		if (!mining) {
			previousSlot = mc.player.getInventory().getSelectedSlot();
			mining = true;
		}
		selectFastest(state);
	}

	private void selectFastest(BlockState state) {
		int bestSlot = -1;
		float bestSpeed = 1.0F;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = mc.player.getInventory().getItem(slot);
			if (stack.isEmpty() || (!WorldUtils.isTool(stack) && !stack.is(Items.SHEARS))) {
				continue;
			}

			float speed = stack.getDestroySpeed(state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				bestSlot = slot;
			}
		}

		if (bestSlot >= 0 && bestSpeed > 1.1F
				&& bestSlot != mc.player.getInventory().getSelectedSlot()) {
			InventoryUtils.setInvSlot(bestSlot);
		}
	}

	private void finishMining() {
		if (mining && hotkeyBack.getValue() && mc.player != null
				&& previousSlot >= 0 && previousSlot < 9
				&& previousSlot != mc.player.getInventory().getSelectedSlot()) {
			InventoryUtils.setInvSlot(previousSlot);
		}
		mining = false;
		previousSlot = -1;
	}

	private void resetTarget() {
		previousBlock = null;
		waiting = false;
		readyAt = 0L;
	}
}
