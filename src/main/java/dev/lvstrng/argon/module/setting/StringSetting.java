package dev.lvstrng.argon.module.setting;

import dev.lvstrng.argon.config.ConfigManager;
import java.util.Objects;

public class StringSetting extends Setting<StringSetting> {
    public String value;
	private final String originalValue;

    public StringSetting(CharSequence name, String defaultValue) {
        super(name);
        this.value = defaultValue;
		this.originalValue = defaultValue;
    }

    public void setValue(String value) {
		if (Objects.equals(this.value, value)) return;
        this.value = value;
		ConfigManager.notifyChanged();
    }

    public String getValue() {
        return value;
    }

	public String getOriginalValue() {
		return originalValue;
	}
}
