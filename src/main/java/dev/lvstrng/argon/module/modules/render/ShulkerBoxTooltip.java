package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.utils.EncryptedString;

public final class ShulkerBoxTooltip extends Module {
	private final BooleanSetting compactMode = new BooleanSetting(
			EncryptedString.of("Compact Mode"), false);
	private final BooleanSetting showEmptySlots = new BooleanSetting(
			EncryptedString.of("Show Empty Slots"), true);

	public ShulkerBoxTooltip() {
		super(EncryptedString.of("Shulker Box Tooltip"),
				EncryptedString.of("Shows shulker box contents inside item tooltips"),
				-1,
				Category.RENDER);
		addSettings(compactMode, showEmptySlots);
	}

	public boolean compactMode() {
		return compactMode.getValue();
	}

	public boolean showEmptySlots() {
		return showEmptySlots.getValue();
	}
}
