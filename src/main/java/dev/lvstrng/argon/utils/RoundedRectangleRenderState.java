package dev.lvstrng.argon.utils;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * One backend-neutral GUI render-state element for a complete rounded shape.
 *
 * Minecraft 26.2 turns every GuiGraphicsExtractor#fill call into a separate
 * state node. Submitting the entire rounded shape here keeps smooth edges while
 * avoiding hundreds of state objects and overlap-tree searches per component.
 */
public final class RoundedRectangleRenderState implements GuiElementRenderState {
	/*
	 * This is a half-width: the complete alpha fringe is twice this value.
	 * Keeping it in framebuffer pixels makes the edge equally crisp at every
	 * GUI scale instead of making scale 3 controls three times blurrier.
	 */
	private static final float AA_HALF_WIDTH_PIXELS = 0.75F;
	private static final TextureSetup NO_TEXTURE = TextureSetup.noTexture();
	private static final int MAX_SEGMENTS_PER_CORNER = 16;
	private static final int[] SEGMENT_LEVELS = {4, 6, 8, 10, 12, 16};
	private static final float[][] COSINES = new float[SEGMENT_LEVELS.length][];
	private static final float[][] SINES = new float[SEGMENT_LEVELS.length][];

	static {
		for (int level = 0; level < SEGMENT_LEVELS.length; level++) {
			int segments = SEGMENT_LEVELS[level];
			int points = 4 * segments;
			COSINES[level] = new float[points];
			SINES[level] = new float[points];
			for (int corner = 0; corner < 4; corner++) {
				for (int step = 0; step < segments; step++) {
					int index = corner * segments + step;
					double angle = Math.PI + corner * Math.PI * 0.5D
							+ step * Math.PI * 0.5D / segments;
					COSINES[level][index] = (float) Math.cos(angle);
					SINES[level][index] = (float) Math.sin(angle);
				}
			}
		}
	}

	private final Matrix3x2fc pose;
	private final float left;
	private final float top;
	private final float right;
	private final float bottom;
	private final float topLeft;
	private final float topRight;
	private final float bottomLeft;
	private final float bottomRight;
	private final float outlineWidth;
	private final int color;
	private final int transparentColor;
	private final int level;
	private final float antialiasWidth;
	private final ScreenRectangle scissorArea;
	private final ScreenRectangle bounds;

	private RoundedRectangleRenderState(GuiGraphicsExtractor context,
			float left, float top, float right, float bottom,
			float topLeft, float topRight, float bottomLeft, float bottomRight,
			float outlineWidth, int color, int requestedSegments) {
		this.pose = new Matrix3x2f(context.pose());
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		float maximumRadius = Math.min((right - left) * 0.5F, (bottom - top) * 0.5F);
		this.topLeft = clampRadius(topLeft, maximumRadius);
		this.topRight = clampRadius(topRight, maximumRadius);
		this.bottomLeft = clampRadius(bottomLeft, maximumRadius);
		this.bottomRight = clampRadius(bottomRight, maximumRadius);
		this.outlineWidth = outlineWidth;
		this.color = color;
		this.transparentColor = color & 0x00FFFFFF;
		float largestRadius = Math.max(Math.max(this.topLeft, this.topRight),
				Math.max(this.bottomLeft, this.bottomRight));
		float framebufferScale = framebufferScale(this.pose);
		this.level = segmentLevel(requestedSegments, largestRadius * framebufferScale);
		this.antialiasWidth = AA_HALF_WIDTH_PIXELS / framebufferScale;
		this.scissorArea = context.scissorStack.peek();

		int boundsLeft = (int) Math.floor(left - antialiasWidth);
		int boundsTop = (int) Math.floor(top - antialiasWidth);
		int boundsRight = (int) Math.ceil(right + antialiasWidth);
		int boundsBottom = (int) Math.ceil(bottom + antialiasWidth);
		ScreenRectangle transformed = new ScreenRectangle(boundsLeft, boundsTop,
				Math.max(1, boundsRight - boundsLeft), Math.max(1, boundsBottom - boundsTop))
				.transformMaxBounds(this.pose);
		this.bounds = scissorArea == null ? transformed : scissorArea.intersection(transformed);
	}

	public static void submitFill(GuiGraphicsExtractor context,
			double x, double y, double x2, double y2,
			double topLeft, double topRight, double bottomLeft, double bottomRight,
			double samples, int color) {
		float left = (float) Math.min(x, x2);
		float top = (float) Math.min(y, y2);
		float right = (float) Math.max(x, x2);
		float bottom = (float) Math.max(y, y2);
		if (right <= left || bottom <= top || color >>> 24 == 0) return;
		context.guiRenderState.addGuiElement(new RoundedRectangleRenderState(context,
				left, top, right, bottom, (float) topLeft, (float) topRight,
				(float) bottomLeft, (float) bottomRight, 0.0F, color,
				normalizeRequestedSegments(samples)));
	}

	public static void submitOutline(GuiGraphicsExtractor context,
			double x, double y, double x2, double y2,
			double topLeft, double topRight, double bottomLeft, double bottomRight,
			double width, double samples, int color) {
		float left = (float) Math.min(x, x2);
		float top = (float) Math.min(y, y2);
		float right = (float) Math.max(x, x2);
		float bottom = (float) Math.max(y, y2);
		float thickness = (float) Math.max(0.5D, width);
		if (right <= left || bottom <= top || color >>> 24 == 0) return;
		if (thickness * 2.0F >= right - left || thickness * 2.0F >= bottom - top) {
			submitFill(context, left, top, right, bottom,
					topLeft, topRight, bottomLeft, bottomRight, samples, color);
			return;
		}
		context.guiRenderState.addGuiElement(new RoundedRectangleRenderState(context,
				left, top, right, bottom, (float) topLeft, (float) topRight,
				(float) bottomLeft, (float) bottomRight, thickness, color,
				normalizeRequestedSegments(samples)));
	}

	@Override
	public void buildVertices(VertexConsumer vertices) {
		if (outlineWidth <= 0.0F) buildFill(vertices);
		else buildOutline(vertices);
	}

	private void buildFill(VertexConsumer vertices) {
		float solidInset = antialiasWidth;
		int points = pointCount();
		float centerX = (left + right) * 0.5F;
		float centerY = (top + bottom) * 0.5F;
		for (int index = 0; index < points; index++) {
			int next = (index + 1) % points;
			vertex(vertices, centerX, centerY, color);
			vertex(vertices, pointX(next, solidInset), pointY(next, solidInset), color);
			vertex(vertices, pointX(index, solidInset), pointY(index, solidInset), color);
			vertex(vertices, centerX, centerY, color);
		}
		buildRing(vertices, solidInset, -antialiasWidth, color, transparentColor);
	}

	private void buildOutline(VertexConsumer vertices) {
		float outerSolid = antialiasWidth;
		float innerSolid = Math.max(outerSolid, outlineWidth - antialiasWidth);
		float innerTransparent = outlineWidth + antialiasWidth;
		buildRing(vertices, -antialiasWidth, outerSolid, transparentColor, color);
		if (innerSolid > outerSolid) buildRing(vertices, outerSolid, innerSolid, color, color);
		buildRing(vertices, innerSolid, innerTransparent, color, transparentColor);
	}

	private void buildRing(VertexConsumer vertices, float outerInset, float innerInset,
			int outerColor, int innerColor) {
		int points = pointCount();
		for (int index = 0; index < points; index++) {
			int next = (index + 1) % points;
			vertex(vertices, pointX(index, outerInset), pointY(index, outerInset), outerColor);
			vertex(vertices, pointX(index, innerInset), pointY(index, innerInset), innerColor);
			vertex(vertices, pointX(next, innerInset), pointY(next, innerInset), innerColor);
			vertex(vertices, pointX(next, outerInset), pointY(next, outerInset), outerColor);
		}
	}

	private float pointX(int index, float inset) {
		int segments = SEGMENT_LEVELS[level];
		int corner = index / segments;
		float radius = contourRadius(corner, inset);
		float contourLeft = left + inset;
		float contourRight = right - inset;
		float center = switch (corner) {
			case 0, 3 -> contourLeft + radius;
			default -> contourRight - radius;
		};
		return center + COSINES[level][index] * radius;
	}

	private float pointY(int index, float inset) {
		int segments = SEGMENT_LEVELS[level];
		int corner = index / segments;
		float radius = contourRadius(corner, inset);
		float contourTop = top + inset;
		float contourBottom = bottom - inset;
		float center = switch (corner) {
			case 0, 1 -> contourTop + radius;
			default -> contourBottom - radius;
		};
		return center + SINES[level][index] * radius;
	}

	private float contourRadius(int corner, float inset) {
		float original = switch (corner) {
			case 0 -> topLeft;
			case 1 -> topRight;
			case 2 -> bottomRight;
			default -> bottomLeft;
		};
		// A deliberately square corner must stay square on the AA fringe. Without
		// this guard the outward contour turns radius 0 into a tiny rounded corner.
		if (original <= 0.0F) return 0.0F;
		float maximum = Math.max(0.0F,
				Math.min((right - left - inset * 2.0F) * 0.5F,
						(bottom - top - inset * 2.0F) * 0.5F));
		return Math.max(0.0F, Math.min(original - inset, maximum));
	}

	private int pointCount() {
		return 4 * SEGMENT_LEVELS[level];
	}

	private void vertex(VertexConsumer vertices, float x, float y, int argb) {
		vertices.addVertexWith2DPose(pose, x, y).setColor(argb);
	}

	private static float clampRadius(float radius, float maximum) {
		return Math.max(0.0F, Math.min(radius, maximum));
	}

	private static int normalizeRequestedSegments(double samples) {
		if (!Double.isFinite(samples)) return SEGMENT_LEVELS[0];
		return Math.max(SEGMENT_LEVELS[0],
				Math.min(MAX_SEGMENTS_PER_CORNER, (int) Math.ceil(samples)));
	}

	private static int segmentLevel(int requestedSegments, float physicalRadius) {
		int radiusMinimum = physicalRadius <= 2.0F ? 4
				: physicalRadius <= 4.0F ? 6
				: physicalRadius <= 7.0F ? 8
				: physicalRadius <= 12.0F ? 10
				: physicalRadius <= 20.0F ? 12 : MAX_SEGMENTS_PER_CORNER;
		int target = Math.max(requestedSegments, radiusMinimum);
		for (int level = 0; level < SEGMENT_LEVELS.length; level++) {
			if (SEGMENT_LEVELS[level] >= target) return level;
		}
		return SEGMENT_LEVELS.length - 1;
	}

	private static float framebufferScale(Matrix3x2fc pose) {
		float poseScaleX = (float) Math.hypot(pose.m00(), pose.m01());
		float poseScaleY = (float) Math.hypot(pose.m10(), pose.m11());
		float poseScale = Math.max(poseScaleX, poseScaleY);
		float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
		return Math.max(0.25F, poseScale * Math.max(1.0F, guiScale));
	}

	@Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
	@Override public TextureSetup textureSetup() { return NO_TEXTURE; }
	@Override public ScreenRectangle scissorArea() { return scissorArea; }
	@Override public ScreenRectangle bounds() { return bounds; }
}
