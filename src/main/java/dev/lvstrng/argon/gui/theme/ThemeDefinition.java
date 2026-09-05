package dev.lvstrng.argon.gui.theme;

import java.util.Objects;

/** Serializable palette used by the global Ghostor appearance manager. */
public final class ThemeDefinition {
	public String id;
	public String name;
	public boolean builtIn;
	public int primary;
	public int secondary;
	public int background;
	public int panel;
	public int border;
	public int enabledToggle;
	public int disabledToggle;
	public int text;
	public int secondaryText;
	public int danger;
	public int cornerRadius;
	public int glowIntensity;

	public ThemeDefinition() {
	}

	public ThemeDefinition(String id, String name, boolean builtIn, int primary, int secondary,
			int background, int panel, int border, int enabledToggle, int disabledToggle,
			int text, int secondaryText, int danger, int cornerRadius, int glowIntensity) {
		this.id = id;
		this.name = name;
		this.builtIn = builtIn;
		this.primary = primary;
		this.secondary = secondary;
		this.background = background;
		this.panel = panel;
		this.border = border;
		this.enabledToggle = enabledToggle;
		this.disabledToggle = disabledToggle;
		this.text = text;
		this.secondaryText = secondaryText;
		this.danger = danger;
		this.cornerRadius = cornerRadius;
		this.glowIntensity = glowIntensity;
	}

	public ThemeDefinition copy() {
		return new ThemeDefinition(id, name, builtIn, primary, secondary, background, panel, border,
				enabledToggle, disabledToggle, text, secondaryText, danger, cornerRadius, glowIntensity);
	}

	public void sanitize() {
		if (id == null) id = "";
		if (name == null || name.isBlank()) name = "Custom Theme";
		name = name.strip();
		if (name.length() > 32) name = name.substring(0, 32);
		cornerRadius = Math.max(0, Math.min(24, cornerRadius));
		glowIntensity = Math.max(0, Math.min(100, glowIntensity));
	}

	@Override
	public boolean equals(Object object) {
		return object instanceof ThemeDefinition other && Objects.equals(id, other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
