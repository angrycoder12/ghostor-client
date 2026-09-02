package dev.lvstrng.argon;

import dev.lvstrng.argon.event.EventManager;
import dev.lvstrng.argon.config.ConfigManager;
import dev.lvstrng.argon.gui.ClickGui;
import dev.lvstrng.argon.managers.FriendManager;
import dev.lvstrng.argon.module.ModuleManager;
import dev.lvstrng.argon.managers.ProfileManager;
import dev.lvstrng.argon.utils.rotation.RotatorManager;
import java.io.File;
import java.io.IOException;
import java.net.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

@SuppressWarnings("all")
public final class Argon {
	public RotatorManager rotatorManager;
	public ProfileManager profileManager;
	public ConfigManager configManager;
	public ModuleManager moduleManager;
	public EventManager eventManager;
	public FriendManager friendManager;
	public static Minecraft mc;
	public String version = " v1.0";
	public static boolean BETA; //this was for beta kids but ablue never made it a reality, and you basically paid extra 10 bucks for nothing while ablue spent it all on war thunder to buy pre-historic tanks and estrogen 🤡🤡🤡
	public static Argon INSTANCE;
	public boolean guiInitialized;
	public ClickGui clickGui;
	public Screen previousScreen = null;
	public long lastModified;
	public File argonJar;

	public Argon() throws InterruptedException, IOException {
		INSTANCE = this;
		mc = Minecraft.getInstance();
		this.eventManager = new EventManager();
		this.moduleManager = new ModuleManager();
		this.clickGui = new ClickGui();
		this.rotatorManager = new RotatorManager();
		this.configManager = new ConfigManager();
		this.profileManager = new ProfileManager();
		this.friendManager = new FriendManager();

		// Import the old single profile once, then let the versioned manager own state.
		if (!this.configManager.hasExistingStore()) this.getProfileManager().loadProfile();
		this.configManager.initialize();
		this.setLastModified();

		this.guiInitialized = false;
	}

	public ProfileManager getProfileManager() {
		return profileManager;
	}

	public ConfigManager getConfigManager() {
		return configManager;
	}

	public ModuleManager getModuleManager() {
		return moduleManager;
	}

	public FriendManager getFriendManager() {
		return friendManager;
	}

	public EventManager getEventManager() {
		return eventManager;
	}

	public ClickGui getClickGui() {
		return clickGui;
	}

	public void resetModifiedDate() {
		this.argonJar.setLastModified(lastModified);
	}

	public String getVersion() {
		return version;
	}

	public void setLastModified() {
		try {
			this.argonJar = new File(Argon.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			// Comment out when debugging
			this.lastModified = argonJar.lastModified();
		} catch (URISyntaxException ignored) {}
	}
}
