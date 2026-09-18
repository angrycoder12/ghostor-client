package dev.lvstrng.argon.module.modules.render.mobesp;

import dev.lvstrng.argon.module.modules.render.blockesp.BlockEspShapeMode;
import java.awt.Color;

/** Serializable per-mob overrides; global settings remain the fallback. */
public final class MobEspData {
	public boolean useCustomSettings;
	public BlockEspShapeMode shapeMode = BlockEspShapeMode.Both;
	public int lineColor = new Color(116, 92, 255, 255).getRGB();
	public int sideColor = new Color(116, 92, 255, 55).getRGB();
	public boolean tracer;
	public int tracerColor = new Color(145, 125, 255, 210).getRGB();

	public MobEspData() {}
	public MobEspData(BlockEspShapeMode shapeMode, Color line, Color side, boolean tracer, Color tracerColor) {
		this.shapeMode = shapeMode;
		this.lineColor = line.getRGB();
		this.sideColor = side.getRGB();
		this.tracer = tracer;
		this.tracerColor = tracerColor.getRGB();
	}
	public void validate() { if (shapeMode == null) shapeMode = BlockEspShapeMode.Both; }
	public Color lineColor() { return new Color(lineColor, true); }
	public Color sideColor() { return new Color(sideColor, true); }
	public Color tracerColor() { return new Color(tracerColor, true); }

	public void setColorChannel(ColorTarget target, int channel, int value) {
		int packed = switch (target) { case Line -> lineColor; case Side -> sideColor; case Tracer -> tracerColor; };
		Color color = new Color(packed, true);
		int v = Math.max(0, Math.min(255, value));
		Color updated = switch (channel) {
			case 0 -> new Color(v, color.getGreen(), color.getBlue(), color.getAlpha());
			case 1 -> new Color(color.getRed(), v, color.getBlue(), color.getAlpha());
			case 2 -> new Color(color.getRed(), color.getGreen(), v, color.getAlpha());
			default -> new Color(color.getRed(), color.getGreen(), color.getBlue(), v);
		};
		int valueArgb = updated.getRGB();
		switch (target) { case Line -> lineColor = valueArgb; case Side -> sideColor = valueArgb; case Tracer -> tracerColor = valueArgb; }
	}

	public enum ColorTarget { Line, Side, Tracer }
}
