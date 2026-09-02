package dev.lvstrng.argon.module.setting;

import java.awt.Color;
import dev.lvstrng.argon.config.ConfigManager;

/** A packed ARGB color setting with an alpha channel. */
public final class ColorSetting extends Setting<ColorSetting> {
	private int argb;
	private final int originalArgb;

	public ColorSetting(CharSequence name, Color defaultColor) {
		super(name);
		this.argb = defaultColor.getRGB();
		this.originalArgb = this.argb;
	}

	public Color getColor() {
		return new Color(argb, true);
	}

	public int getArgb() {
		return argb;
	}

	public void setArgb(int argb) {
		if (this.argb == argb) return;
		this.argb = argb;
		ConfigManager.notifyChanged();
	}

	public void setColor(Color color) {
		setArgb(color.getRGB());
	}

	public void setChannel(int channel, int value) {
		Color color = getColor();
		int clamped = Math.max(0, Math.min(255, value));
		setColor(switch (channel) {
			case 0 -> new Color(clamped, color.getGreen(), color.getBlue(), color.getAlpha());
			case 1 -> new Color(color.getRed(), clamped, color.getBlue(), color.getAlpha());
			case 2 -> new Color(color.getRed(), color.getGreen(), clamped, color.getAlpha());
			default -> new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
		});
	}

	public void reset() {
		setArgb(originalArgb);
	}

	public int getOriginalArgb() {
		return originalArgb;
	}
}
