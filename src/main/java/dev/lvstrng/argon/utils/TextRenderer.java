package dev.lvstrng.argon.utils;

import dev.lvstrng.argon.font.Fonts;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import static dev.lvstrng.argon.Argon.mc;


public final class TextRenderer {
	private static final float SMALL_SCALE = 0.82F;

	public static void drawString(CharSequence string, GuiGraphicsExtractor context, int x, int y, int color) {
		boolean custom = ClickGUI.customFont.getValue();
		if (custom)
			Fonts.QUICKSAND.drawString(context, string, x, y - 8, color);
		else drawMinecraftText(string, context, x, y, color);
	}

	public static int getWidth(CharSequence string) {
		boolean custom = ClickGUI.customFont.getValue();
		if (custom)
			return Fonts.QUICKSAND.getStringWidth(string);
		else return mc.font.width(string.toString()) * 2;
	}

	public static void drawSmallString(CharSequence string, GuiGraphicsExtractor context, int x, int y, int color) {
		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();
		if (ClickGUI.customFont.getValue()) {
			matrices.scale(SMALL_SCALE, SMALL_SCALE);
			Fonts.QUICKSAND.drawString(context, string, (int) (x / SMALL_SCALE), (int) (y / SMALL_SCALE) - 8, color);
		} else {
			float vanillaScale = 1.5F;
			matrices.scale(vanillaScale, vanillaScale);
			context.text(mc.font, string.toString(), (int) (x / vanillaScale), (int) (y / vanillaScale), color, false);
		}
		matrices.popMatrix();
	}

	public static int getSmallWidth(CharSequence string) {
		if (ClickGUI.customFont.getValue())
			return Math.round(Fonts.QUICKSAND.getStringWidth(string) * SMALL_SCALE);
		return Math.round(mc.font.width(string.toString()) * 1.5F);
	}

	public static void drawCenteredString(CharSequence string, GuiGraphicsExtractor context, int x, int y, int color) {
		boolean custom = ClickGUI.customFont.getValue();
		if (custom)
			Fonts.QUICKSAND.drawString(context, string, (x - (Fonts.QUICKSAND.getStringWidth(string) / 2)), y - 8, color);
		else drawCenteredMinecraftText(string, context, x, y, color);
	}

	public static void drawLargeString(CharSequence string, GuiGraphicsExtractor context, int x, int y, int color) {
		boolean custom = ClickGUI.customFont.getValue();
		if (custom) {
			Matrix3x2fStack matrices = context.pose();
			matrices.pushMatrix();

			matrices.scale(1.4f, 1.4f);
			Fonts.QUICKSAND.drawString(context, string, x, y - 8, color);

			matrices.popMatrix();
		} else
			drawLargerMinecraftText(string, context, x, y, color);
	}

	public static void drawMinecraftText(CharSequence string, GuiGraphicsExtractor context, int x, int y, int color) {
		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();

		matrices.scale(2f, 2f);
		context.text(mc.font, string.toString(), x / 2, y / 2, color, false);

		matrices.popMatrix();
	}

	public static void drawLargerMinecraftText(CharSequence string, GuiGraphicsExtractor context, int x, int y, int color) {
		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();

		matrices.scale(3f, 3f);
		context.text(mc.font, string.toString(), x / 3, y / 3, color, false);

		matrices.popMatrix();
	}

	public static void drawCenteredMinecraftText(CharSequence string, GuiGraphicsExtractor context, int x, int y, int color) {
		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();

		matrices.scale(2f, 2f);
		context.text(mc.font, string.toString(), (x / 2) - (mc.font.width(string.toString()) / 2), y / 2, color, false);

		matrices.popMatrix();
	}
}
