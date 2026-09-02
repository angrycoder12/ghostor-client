package dev.lvstrng.argon.module.modules.render.blockesp;

import java.awt.Color;

/** Serializable per-block overrides. Values remain stored when a block is deselected. */
public final class BlockEspBlockData {
	public boolean useCustomSettings;
	public BlockEspShapeMode shapeMode = BlockEspShapeMode.Both;
	public int lineColor = new Color(62, 220, 235, 255).getRGB();
	public int sideColor = new Color(62, 220, 235, 55).getRGB();
	public boolean tracer;
	public int tracerColor = new Color(62, 220, 235, 210).getRGB();

	public BlockEspBlockData() {
	}

	public BlockEspBlockData(BlockEspShapeMode shapeMode, Color lineColor, Color sideColor,
			boolean tracer, Color tracerColor) {
		this.shapeMode = shapeMode;
		this.lineColor = lineColor.getRGB();
		this.sideColor = sideColor.getRGB();
		this.tracer = tracer;
		this.tracerColor = tracerColor.getRGB();
	}

	public void validate() {
		if (shapeMode == null) shapeMode = BlockEspShapeMode.Both;
	}

	public Color lineColor() {
		return new Color(lineColor, true);
	}

	public Color sideColor() {
		return new Color(sideColor, true);
	}

	public Color tracerColor() {
		return new Color(tracerColor, true);
	}

	public void setColorChannel(ColorTarget target, int channel, int value) {
		int packed = switch (target) {
			case Line -> lineColor;
			case Side -> sideColor;
			case Tracer -> tracerColor;
		};
		Color color = new Color(packed, true);
		int clamped = Math.max(0, Math.min(255, value));
		Color updated = switch (channel) {
			case 0 -> new Color(clamped, color.getGreen(), color.getBlue(), color.getAlpha());
			case 1 -> new Color(color.getRed(), clamped, color.getBlue(), color.getAlpha());
			case 2 -> new Color(color.getRed(), color.getGreen(), clamped, color.getAlpha());
			default -> new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
		};
		switch (target) {
			case Line -> lineColor = updated.getRGB();
			case Side -> sideColor = updated.getRGB();
			case Tracer -> tracerColor = updated.getRGB();
		}
	}

	public enum ColorTarget {
		Line,
		Side,
		Tracer
	}
}
