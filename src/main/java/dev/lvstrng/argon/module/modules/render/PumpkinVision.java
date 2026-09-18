package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;

/** Suppresses only the carved-pumpkin first-person camera overlay. */
public final class PumpkinVision extends Module {
	private static PumpkinVision instance;

	public PumpkinVision() {
		super(EncryptedString.of("Pumpkin Vision"),
				EncryptedString.of("Removes the pumpkin overlay while wearing a carved pumpkin"),
				-1, Category.RENDER);
		instance = this;
	}

	public static boolean isHidingOverlay() {
		return instance != null && instance.isEnabled() && !instance.isClientDisabled();
	}
}
