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
		RoundedRectangleRenderState.submitFill(context, x, y, x2, y2,
				corner1, corner2, corner3, corner4, samples, color.getRGB());
	}

	public static void renderRoundedQuad(GuiGraphicsExtractor context, Color color, double x, double y, double x1, double y1, double rad, double samples) {
		renderRoundedQuad(context, color, x, y, x1, y1, rad, rad, rad, rad, samples);
	}

	public static void renderCircle(GuiGraphicsExtractor context, Color color, double originX, double originY, double radius, int segments) {
		if (radius <= 0.0D) return;
		renderRoundedQuad(context, color, originX - radius, originY - radius,
				originX + radius, originY + radius, radius, 16);
	}

	public static void renderRoundedOutline(GuiGraphicsExtractor poses, Color c, double fromX, double fromY, double toX, double toY, double rad1, double rad2, double rad3, double rad4, double width, double samples) {
		RoundedRectangleRenderState.submitOutline(poses, fromX, fromY, toX, toY,
				rad1, rad2, rad3, rad4, width, samples, c.getRGB());
	}

	public static void renderFilledBox(AABB box, Color color) {
		renderFilledBox(box, color, true);
	}

	public static void renderFilledBox(AABB box, Color color, boolean throughWalls) {
		var cuboid = Gizmos.cuboid(box, GizmoStyle.fill(color.getRGB()));
		if (throughWalls) cuboid.setAlwaysOnTop();
	}

	public static void renderBoxOutline(AABB box, Color color, float lineWidth) {
		renderBoxOutline(box, color, lineWidth, true);
	}

	public static void renderBoxOutline(AABB box, Color color, float lineWidth, boolean throughWalls) {
		var cuboid = Gizmos.cuboid(box, GizmoStyle.stroke(color.getRGB(), lineWidth));
		if (throughWalls) cuboid.setAlwaysOnTop();
	}

	public static void renderLine(Color color, Vec3 start, Vec3 end) {
		Gizmos.line(start, end, color.getRGB()).setAlwaysOnTop();
	}

	public static void renderLine(Color color, Vec3 start, Vec3 end, float width, boolean throughWalls) {
		var line = Gizmos.line(start, end, color.getRGB(), Math.max(1.0F, width));
		if (throughWalls) line.setAlwaysOnTop();
	}

}
