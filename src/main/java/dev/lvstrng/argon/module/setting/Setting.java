package dev.lvstrng.argon.module.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting<T extends Setting<T>> {
	private CharSequence name;
	public CharSequence description;
	private BooleanSupplier visibility = () -> true;

	public Setting(CharSequence name) {
		this.name = name;
	}

	public void setName(CharSequence name) {
		this.name = name;
	}

	public CharSequence getName() {
		return name;
	}

	public CharSequence getDescription() {
		return description;
	}

	public T setDescription(CharSequence desc) {
		this.description = desc;
		//noinspection unchecked
		return (T) this;
	}

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	public T visibleWhen(BooleanSupplier visibility) {
		this.visibility = visibility == null ? () -> true : visibility;
		//noinspection unchecked
		return (T) this;
	}
}
