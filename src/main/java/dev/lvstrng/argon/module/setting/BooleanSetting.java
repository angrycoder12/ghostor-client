package dev.lvstrng.argon.module.setting;

import dev.lvstrng.argon.config.ConfigManager;

public final class BooleanSetting extends Setting<BooleanSetting> {
	private boolean value;
	private final boolean originalValue;

	public BooleanSetting(CharSequence name, boolean value) {
		super(name);
		this.value = value;
		this.originalValue = value;
	}

	public void toggle() {
		setValue(!value);
	}

	public void setValue(boolean value) {
		if (this.value == value) return;
		this.value = value;
		ConfigManager.notifyChanged();
	}

	public boolean getOriginalValue() {
		return originalValue;
	}

	public boolean getValue() {
		return value;
	}
}
