package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.mixin.MinecraftClientAccessor;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Applies the reference FastPlace delay rules to Minecraft's current right-use cooldown. */
public final class FastPlace extends Module implements TickListener {
	private final NumberSetting delay = new NumberSetting(
			EncryptedString.of("Delay"), 0, 4, 0, 1);
	private final BooleanSetting blocksOnly = new BooleanSetting(
			EncryptedString.of("Blocks only"), true);
	private final BooleanSetting separateProjectileDelay = new BooleanSetting(
			EncryptedString.of("Separate Projectile Delay"), true);
	private final NumberSetting projectileDelay = new NumberSetting(
			EncryptedString.of("Projectile Delay"), 0, 4, 2, 1)
			.visibleWhen(separateProjectileDelay::getValue);
	private boolean modifiedCooldown;

	public FastPlace() {
		super(EncryptedString.of("FastPlace"),
				EncryptedString.of("Reduces the delay between item placement and use actions"),
				-1,
				Category.MISC);
		addSettings(delay, blocksOnly, separateProjectileDelay, projectileDelay);
	}

	@Override
	public void onEnable() {
		modifiedCooldown = false;
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		if (modifiedCooldown) {
			((MinecraftClientAccessor) mc).setItemUseCooldown(4);
		}
		modifiedCooldown = false;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gui.screen() != null) {
			return;
		}

		ItemStack held = mc.player.getMainHandItem();
		int requestedDelay;
		if (!blocksOnly.getValue()) {
			requestedDelay = delay.getValueInt();
		} else if (held.getItem() instanceof BlockItem) {
			requestedDelay = delay.getValueInt();
		} else if (separateProjectileDelay.getValue()
				&& (held.is(Items.SNOWBALL) || held.is(Items.EGG))) {
			requestedDelay = projectileDelay.getValueInt();
		} else {
			return;
		}

		if (requestedDelay == 4) {
			return;
		}
		MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
		int currentDelay = accessor.getItemUseCooldown();
		if (requestedDelay == 0 || currentDelay == 4) {
			accessor.setItemUseCooldown(requestedDelay);
			modifiedCooldown = true;
		}
	}
}
