package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.imixin.IKeyBinding;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.PlacementUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class Clutch extends Module implements TickListener {
	private final BooleanSetting onlySword = new BooleanSetting(
			EncryptedString.of("Only sword"), true);

	private boolean holdingUse;
	private long lastHitAt;
	private long lastPlaceAt;
	private float lastHealth = 20.0F;
	private LocalPlayer observedPlayer;

	public Clutch() {
		super(EncryptedString.of("Clutch"),
				EncryptedString.of("Attempts to save you from falling by placing blocks or using items"),
				-1,
				Category.COMBAT);
		addSettings(onlySword);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		resetForPlayer();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		releaseUse();
		lastHitAt = 0L;
		lastPlaceAt = 0L;
		observedPlayer = null;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gui.screen() != null) {
			releaseUse();
			observedPlayer = null;
			lastHitAt = 0L;
			return;
		}

		if (observedPlayer != mc.player) {
			resetForPlayer();
		}

		float health = mc.player.getHealth();
		if (health < lastHealth) {
			lastHitAt = System.currentTimeMillis();
		}
		lastHealth = health;

		if (!shouldClutch()) {
			releaseUse();
			return;
		}

		if (!onlySword.getValue() || WorldUtils.isSword(mc.player.getMainHandItem())) {
			holdUse();
		} else {
			releaseUse();
		}
		placeBlockUnder();
	}

	private boolean shouldClutch() {
		return mc.player.isAlive()
				&& System.currentTimeMillis() - lastHitAt <= 2000L
				&& !mc.player.onGround()
				&& mc.player.getDeltaMovement().y < -0.05D;
	}

	private void holdUse() {
		if (!holdingUse) {
			mc.options.keyUse.setDown(true);
			holdingUse = true;
		}
	}

	private void releaseUse() {
		if (holdingUse) {
			((IKeyBinding) mc.options.keyUse).resetPressed();
			holdingUse = false;
		}
	}

	private void placeBlockUnder() {
		long now = System.currentTimeMillis();
		if (now - lastPlaceAt < 100L) {
			return;
		}

		int slot = PlacementUtils.findBlockSlot();
		if (slot < 0) {
			return;
		}

		BlockPos under = BlockPos.containing(mc.player.getX(), mc.player.getY() - 1.0D, mc.player.getZ());
		PlacementUtils.PlacementTarget target = PlacementUtils.findSupport(under);
		if (target == null) {
			return;
		}

		float[] rotations = PlacementUtils.rotations(target);
		if (PlacementUtils.place(target, slot, rotations[0], rotations[1])) {
			lastPlaceAt = now;
		}
	}

	private void resetForPlayer() {
		observedPlayer = mc.player;
		lastHealth = mc.player == null ? 20.0F : mc.player.getHealth();
		lastHitAt = 0L;
		lastPlaceAt = 0L;
		releaseUse();
	}
}
