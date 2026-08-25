package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;

public final class MapTooltip extends Module {
	public MapTooltip() {
		super(EncryptedString.of("Map Tooltip"),
				EncryptedString.of("Shows map previews inside item tooltips"),
				-1,
				Category.RENDER);
	}
}
