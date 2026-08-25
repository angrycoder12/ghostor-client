package dev.lvstrng.argon;

import com.mojang.logging.LogUtils;
import dev.lvstrng.argon.gui.components.MapTooltipComponent;
import dev.lvstrng.argon.gui.components.ShulkerBoxTooltipComponent;
import java.io.IOException;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

public final class Main implements ClientModInitializer {
	private static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void onInitializeClient() {
		try {
			new Argon();
			MapTooltipComponent.register();
			ShulkerBoxTooltipComponent.register();
			LOGGER.info("Ghostor Client initialized");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			LOGGER.error("Ghostor Client initialization was interrupted", exception);
		} catch (IOException exception) {
			LOGGER.error("Ghostor Client failed to initialize", exception);
		}
	}
}
