package dev.lvstrng.argon.module.setting;

import dev.lvstrng.argon.config.ConfigManager;

public final class KeybindSetting extends Setting<KeybindSetting> {
	private int keyCode;
	private boolean listening;
	private final boolean moduleKey;
	private final int originalKey;

	public KeybindSetting(CharSequence name, int key, boolean moduleKey) {
		super(name);
		this.keyCode = key;
		this.originalKey = key;
		this.moduleKey = moduleKey;
	}

	public boolean isModuleKey() {
		return moduleKey;
	}

	public boolean isListening() {
		return listening;
	}

	public int getOriginalKey() {
		return originalKey;
	}

	public void setListening(boolean listening) {
		this.listening = listening;
	}

	public int getKey() {
		return keyCode;
	}

	public void setKey(int key) {
		if (this.keyCode == key) return;
		this.keyCode = key;
		ConfigManager.notifyChanged();
	}

    public void toggleListening() {
		this.listening = !listening;
	}
}
