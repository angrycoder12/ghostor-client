package dev.lvstrng.argon.module;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.config.ConfigManager;
import dev.lvstrng.argon.event.EventManager;
import dev.lvstrng.argon.module.setting.Setting;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;

public abstract class Module implements Serializable {
	private final List<Setting<?>> settings = new ArrayList<>();
	public final EventManager eventManager = Argon.INSTANCE.eventManager;
	protected Minecraft mc = Minecraft.getInstance();
	private CharSequence name;
	private CharSequence description;
	private boolean enabled;
	private boolean defaultEnabled;
	private boolean clientDisabled;
	private int key;
	private Category category;
	private final Category originalCategory;

	public Module(CharSequence name, CharSequence description, int key, Category category) {
		this.name = name;
		this.description = description;
		this.enabled = false;
		this.key = key;
		this.category = category;
		this.originalCategory = category;
	}

	public void toggle() {
		if (clientDisabled) return;
		setEnabled(!enabled);
	}

	public CharSequence getName() {
		return name;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public CharSequence getDescription() {
		return description;
	}

	public int getKey() {
		return key;
	}

	public Category getCategory() {
		return category;
	}

	public Category getOriginalCategory() {
		return originalCategory;
	}

	public boolean isClientDisabled() {
		return clientDisabled;
	}

	public boolean isDefaultEnabled() {
		return defaultEnabled;
	}

	public void captureDefaultState() {
		defaultEnabled = enabled;
	}

	public boolean setClientDisabled(boolean disabled) {
		if (originalCategory == Category.CLIENT) return false;
		if (clientDisabled == disabled) return true;
		if (disabled && enabled) setEnabled(false);
		clientDisabled = disabled;
		category = disabled ? Category.DISABLED : originalCategory;
		// Re-enabled modules deliberately remain toggled off.
		if (!disabled) enabled = false;
		ConfigManager.notifyChanged();
		return true;
	}

	public void setCategory(Category category) {
		if (category == Category.DISABLED) setClientDisabled(true);
		else if (category == originalCategory) setClientDisabled(false);
	}

	public void setName(CharSequence name) {
		this.name = name;
	}

	public void setDescription(CharSequence description) {
		this.description = description;
	}

	public void setKey(int key) {
		if (this.key == key) return;
		this.key = key;
		ConfigManager.notifyChanged();
	}

	public List<Setting<?>> getSettings() {
		return settings;
	}

	public void onEnable() {}

	public void onDisable() {}

	public void addSetting(Setting<?> setting) {
		this.settings.add(setting);
	}

	public void addSettings(Setting<?>... settings) {
		this.settings.addAll(Arrays.asList(settings));
	}

	public void setEnabled(boolean enabled) {
		if (enabled && clientDisabled) return;
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled)
			onEnable();
		else onDisable();
		ConfigManager.notifyChanged();
	}

	public void setEnabledStatus(boolean enabled) {
		if (enabled && clientDisabled) return;
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		ConfigManager.notifyChanged();
	}

}
