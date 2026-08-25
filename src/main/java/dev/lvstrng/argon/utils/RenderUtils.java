package dev.lvstrng.argon.utils;
import java.awt.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static dev.lvstrng.argon.Argon.mc;

public final class RenderUtils {
	public static boolean rendering3D = true;
	private static int projectionDepth;

	public static Vec3 getCameraPos() {
		return mc.gameRenderer.mainCamera().position();
	}

	public static float tickProgress() {
		return mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
	}

	public static float frameDelta() {
		return mc.getDeltaTracker().getGameTimeDeltaTicks();
	}

	public static double deltaTime() {
		return mc.getFps() > 0 ? (1.0000 / mc.getFps()) : 1;
	}

	public static float fast(float end, float start, float multiple) {
		return (1 - Mth.clamp((float) (deltaTime() * multiple), 0, 1)) * end + Mth.clamp((float) (deltaTime() * multiple), 0, 1) * start;
	}

	public static Vec3 getPlayerLookVec(Player player) {
		float f = 0.017453292F;
		float pi = 3.1415927F;
		float f1 = Mth.cos(-player.getYRot() * f - pi);
		float f2 = Mth.sin(-player.getYRot() * f - pi);
		float f3 = -Mth.cos(-player.getXRot() * f);
		float f4 = Mth.sin(-player.getXRot() * f);
		return (new Vec3((f2 * f3), f4, (f1 * f3))).normalize();
	}

	public static void unscaledProjection(GuiGraphicsExtractor context) {
		if (projectionDepth++ == 0) {
			float scaleFactor = (float) Minecraft.getInstance().getWindow().getGuiScale();
			context.pose().pushMatrix();
			context.pose().scale(1.0F / scaleFactor, 1.0F / scaleFactor);
		}
		rendering3D = false;
	}

	public static void scaledProjection(GuiGraphicsExtractor context) {
		if (projectionDepth == 0) {
			rendering3D = true;
			return;
		}

		if (--projectionDepth == 0) {
			context.pose().popMatrix();
		}
		rendering3D = true;
	}

	public static void renderRoundedQuad(GuiGraphicsExtractor context, Color color, double x, double y, double x2, double y2, double corner1, double corner2, double corner3, double corner4, double samples) {
		int left = (int) Math.floor(Math.min(x, x2));
		int top = (int) Math.floor(Math.min(y, y2));
		int right = (int) Math.ceil(Math.max(x, x2));
		int bottom = (int) Math.ceil(Math.max(y, y2));

		int width = right - left;
		int height = bottom - top;
		if (width <= 0 || height <= 0) {
			return;
		}

		int radiusTopLeft = clampRadius(corner1, width, height);
		int radiusTopRight = clampRadius(corner2, width, height);
		int radiusBottomLeft = clampRadius(corner3, width, height);
		int radiusBottomRight = clampRadius(corner4, width, height);
		int packedColor = color.getRGB();

		int topCornerRows = Math.max(radiusTopLeft, radiusTopRight);
		int bottomCornerRows = Math.max(radiusBottomLeft, radiusBottomRight);
		if (topCornerRows + bottomCornerRows > height) {
			topCornerRows = height;
			bottomCornerRows = 0;
		}

		for (int row = 0; row < topCornerRows; row++) {
			int leftInset = getLeftInsetForRow(row, height, radiusTopLeft, radiusBottomLeft);
			int rightInset = getRightInsetForRow(row, height, radiusTopRight, radiusBottomRight);
			fillRow(context, left + leftInset, right - rightInset, top + row, packedColor);
		}

		int middleTop = top + topCornerRows;
		int middleBottom = bottom - bottomCornerRows;
		if (middleBottom > middleTop) {
			context.fill(left, middleTop, right, middleBottom, packedColor);
		}

		for (int row = height - bottomCornerRows; row < height; row++) {
			int leftInset = getLeftInsetForRow(row, height, radiusTopLeft, radiusBottomLeft);
			int rightInset = getRightInsetForRow(row, height, radiusTopRight, radiusBottomRight);
			fillRow(context, left + leftInset, right - rightInset, top + row, packedColor);
		}
	}

	public static void renderRoundedQuad(GuiGraphicsExtractor context, Color color, double x, double y, double x1, double y1, double rad, double samples) {
		renderRoundedQuad(context, color, x, y, x1, y1, rad, rad, rad, rad, samples);
	}

	public static void renderCircle(GuiGraphicsExtractor context, Color color, double originX, double originY, double radius, int segments) {
		int top = (int) Math.floor(originY - radius);
		int bottom = (int) Math.ceil(originY + radius);
		int packedColor = color.getRGB();

		for (int y = top; y < bottom; y++) {
			double distanceY = (y + 0.5D) - originY;
			double radiusSquared = radius * radius;
			double inner = radiusSquared - (distanceY * distanceY);
			if (inner <= 0.0D) {
				continue;
			}

			double distanceX = Math.sqrt(inner);
			int left = (int) Math.floor(originX - distanceX);
			int right = (int) Math.ceil(originX + distanceX);
			fillRow(context, left, right, y, packedColor);
		}
	}

	public static void renderRoundedOutline(GuiGraphicsExtractor poses, Color c, double fromX, double fromY, double toX, double toY, double rad1, double rad2, double rad3, double rad4, double width, double samples) {
		int left = (int) Math.floor(Math.min(fromX, toX));
		int top = (int) Math.floor(Math.min(fromY, toY));
		int right = (int) Math.ceil(Math.max(fromX, toX));
		int bottom = (int) Math.ceil(Math.max(fromY, toY));

		int outerWidth = right - left;
		int outerHeight = bottom - top;
		if (outerWidth <= 0 || outerHeight <= 0) {
			return;
		}

		int thickness = Math.max(1, (int) Math.round(width));
		if (thickness * 2 >= outerWidth || thickness * 2 >= outerHeight) {
			renderRoundedQuad(poses, c, fromX, fromY, toX, toY, rad1, rad2, rad3, rad4, samples);
			return;
		}

		int radiusTopLeft = clampRadius(rad1, outerWidth, outerHeight);
		int radiusTopRight = clampRadius(rad2, outerWidth, outerHeight);
		int radiusBottomLeft = clampRadius(rad3, outerWidth, outerHeight);
		int radiusBottomRight = clampRadius(rad4, outerWidth, outerHeight);

		int innerLeft = left + thickness;
		int innerTop = top + thickness;
		int innerRight = right - thickness;
		int innerBottom = bottom - thickness;
		int innerWidth = innerRight - innerLeft;
		int innerHeight = innerBottom - innerTop;

		int innerRadiusTopLeft = Math.max(0, radiusTopLeft - thickness);
		int innerRadiusTopRight = Math.max(0, radiusTopRight - thickness);
		int innerRadiusBottomLeft = Math.max(0, radiusBottomLeft - thickness);
		int innerRadiusBottomRight = Math.max(0, radiusBottomRight - thickness);
		int packedColor = c.getRGB();

		int topCornerRows = Math.max(thickness, Math.max(radiusTopLeft, radiusTopRight));
		int bottomCornerRows = Math.max(thickness, Math.max(radiusBottomLeft, radiusBottomRight));
		if (topCornerRows + bottomCornerRows > outerHeight) {
			topCornerRows = outerHeight;
			bottomCornerRows = 0;
		}

		for (int row = 0; row < topCornerRows; row++) {
			renderOutlineRow(poses, row, left, top, right, outerHeight, innerLeft, innerTop, innerRight,
					innerBottom, innerHeight, radiusTopLeft, radiusTopRight, radiusBottomLeft,
					radiusBottomRight, innerRadiusTopLeft, innerRadiusTopRight, innerRadiusBottomLeft,
					innerRadiusBottomRight, packedColor);
		}

		int middleTop = top + topCornerRows;
		int middleBottom = bottom - bottomCornerRows;
		if (middleBottom > middleTop) {
			poses.fill(left, middleTop, innerLeft, middleBottom, packedColor);
			poses.fill(innerRight, middleTop, right, middleBottom, packedColor);
		}

		for (int row = outerHeight - bottomCornerRows; row < outerHeight; row++) {
			renderOutlineRow(poses, row, left, top, right, outerHeight, innerLeft, innerTop, innerRight,
					innerBottom, innerHeight, radiusTopLeft, radiusTopRight, radiusBottomLeft,
					radiusBottomRight, innerRadiusTopLeft, innerRadiusTopRight, innerRadiusBottomLeft,
					innerRadiusBottomRight, packedColor);
		}
	}

	private static void renderOutlineRow(GuiGraphicsExtractor context, int row, int left, int top, int right,
			int outerHeight, int innerLeft, int innerTop, int innerRight, int innerBottom, int innerHeight,
			int radiusTopLeft, int radiusTopRight, int radiusBottomLeft, int radiusBottomRight,
			int innerRadiusTopLeft, int innerRadiusTopRight, int innerRadiusBottomLeft,
			int innerRadiusBottomRight, int color) {
		int outerLeft = left + getLeftInsetForRow(row, outerHeight, radiusTopLeft, radiusBottomLeft);
		int outerRight = right - getRightInsetForRow(row, outerHeight, radiusTopRight, radiusBottomRight);
		int y = top + row;

		if (y < innerTop || y >= innerBottom) {
			fillRow(context, outerLeft, outerRight, y, color);
			return;
		}

		int innerRow = y - innerTop;
		int innerRowLeft = innerLeft + getLeftInsetForRow(innerRow, innerHeight, innerRadiusTopLeft, innerRadiusBottomLeft);
		int innerRowRight = innerRight - getRightInsetForRow(innerRow, innerHeight, innerRadiusTopRight, innerRadiusBottomRight);
		fillRow(context, outerLeft, innerRowLeft, y, color);
		fillRow(context, innerRowRight, outerRight, y, color);
	}

	private static int clampRadius(double radius, int width, int height) {
		return Math.max(0, Math.min((int) Math.round(radius), Math.min(width, height) / 2));
	}

	private static int getLeftInsetForRow(int row, int height, int radiusTop, int radiusBottom) {
		int inset = 0;
		if (radiusTop > 0 && row < radiusTop) {
			inset = Math.max(inset, getCornerInset(radiusTop, row));
		}
		if (radiusBottom > 0 && row >= height - radiusBottom) {
			inset = Math.max(inset, getCornerInset(radiusBottom, height - 1 - row));
		}
		return inset;
	}

	private static int getRightInsetForRow(int row, int height, int radiusTop, int radiusBottom) {
		int inset = 0;
		if (radiusTop > 0 && row < radiusTop) {
			inset = Math.max(inset, getCornerInset(radiusTop, row));
		}
		if (radiusBottom > 0 && row >= height - radiusBottom) {
			inset = Math.max(inset, getCornerInset(radiusBottom, height - 1 - row));
		}
		return inset;
	}

	private static int getCornerInset(int radius, int offsetFromEdge) {
		double distance = radius - (offsetFromEdge + 0.5D);
		double inside = (radius * radius) - (distance * distance);
		if (inside <= 0.0D) {
			return radius;
		}

		return Math.max(0, (int) Math.ceil(radius - Math.sqrt(inside)));
	}

	private static void fillRow(GuiGraphicsExtractor context, int left, int right, int y, int color) {
		if (right <= left) {
			return;
		}

		context.fill(left, y, right, y + 1, color);
	}

	public static void renderFilledBox(AABB box, Color color) {
		Gizmos.cuboid(box, GizmoStyle.fill(color.getRGB())).setAlwaysOnTop();
	}

	public static void renderBoxOutline(AABB box, Color color, float lineWidth) {
		Gizmos.cuboid(box, GizmoStyle.stroke(color.getRGB(), lineWidth)).setAlwaysOnTop();
	}

	public static void renderLine(Color color, Vec3 start, Vec3 end) {
		Gizmos.line(start, end, color.getRGB()).setAlwaysOnTop();
	}

}
