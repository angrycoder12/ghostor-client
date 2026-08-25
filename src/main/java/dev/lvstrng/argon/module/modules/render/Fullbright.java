package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.utils.EncryptedString;

public final class Fullbright extends Module {
	private final ModeSetting<Mode> mode = new ModeSetting<>(
			EncryptedString.of("Mode"), Mode.GAMMA, Mode.class);

	public Fullbright() {
		super(EncryptedString.of("Fullbright"),
				EncryptedString.of("Brightens the world so darkness is easier to see"),
				-1,
				Category.RENDER);
		addSettings(mode);
	}

	public static Mode activeMode() {
		if (Argon.INSTANCE == null || Argon.INSTANCE.getModuleManager() == null) {
			return null;
		}
		Fullbright module = Argon.INSTANCE.getModuleManager().getModule(Fullbright.class);
		return module != null && module.isEnabled() ? module.mode.getMode() : null;
	}

	public enum Mode {
		GAMMA,
		NIGHTVISION
	}
}
