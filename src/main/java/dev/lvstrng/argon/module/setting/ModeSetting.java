package dev.lvstrng.argon.module.setting;

import java.util.Arrays;
import java.util.List;
import dev.lvstrng.argon.config.ConfigManager;

public final class ModeSetting<T extends Enum<T>> extends Setting<ModeSetting<T>> {
	public int index;
	private final List<T> possibleValues;
	private final int originalValue;

	public ModeSetting(CharSequence name, T defaultValue, Class<T> type) {
		super(name);
		T[] values = type.getEnumConstants();
		this.possibleValues = Arrays.asList(values);
		this.index = this.possibleValues.indexOf(defaultValue);
		this.originalValue = this.index;
	}

	public T getMode() {
		return possibleValues.get(index);
	}

	public void setMode(T mode) {
		setModeIndex(possibleValues.indexOf(mode));
	}

	public void setModeIndex(int mode) {
		int updated = mode >= 0 && mode < possibleValues.size() ? mode : originalValue;
		if (index == updated) return;
		index = updated;
		ConfigManager.notifyChanged();
	}

	public int getModeIndex() {
		return index;
	}

	public String getModeName() {
		return getMode().name();
	}

	public boolean setModeName(String name) {
		for (int value = 0; value < possibleValues.size(); value++) {
			if (possibleValues.get(value).name().equals(name)) {
				setModeIndex(value);
				return true;
			}
		}
		return false;
	}

	public boolean isValidModeName(String name) {
		return possibleValues.stream().anyMatch(value -> value.name().equals(name));
	}

	public String getOriginalModeName() {
		return possibleValues.get(originalValue).name();
	}

	public int getOriginalValue() {
		return originalValue;
	}

	public void cycle() {
		setModeIndex(index < possibleValues.size() - 1 ? index + 1 : 0);
	}

	public boolean isMode(T mode) {
		return index == possibleValues.indexOf(mode);
	}

}
