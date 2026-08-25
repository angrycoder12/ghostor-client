package dev.lvstrng.argon.utils;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import static dev.lvstrng.argon.Argon.mc;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

public final class ProjectionUtils {
	private ProjectionUtils() {
	}

	@Nullable
	public static ProjectedPoint project(Vec3 worldPos) {
		if (mc.level == null || mc.gameRenderer == null) {
			return null;
		}

		Camera camera = mc.gameRenderer.mainCamera();
		if (camera == null) {
			return null;
		}

		int width = mc.getWindow().getGuiScaledWidth();
		int height = mc.getWindow().getGuiScaledHeight();
		if (width <= 0 || height <= 0) {
			return null;
		}

		Vec3 cameraPos = camera.position();
		Vector4f clip = new Vector4f(
				(float) (worldPos.x - cameraPos.x),
				(float) (worldPos.y - cameraPos.y),
				(float) (worldPos.z - cameraPos.z),
				1.0F
		);

		Matrix4f view = new Matrix4f()
				.rotateX((float) Math.toRadians(camera.xRot()))
				.rotateY((float) Math.toRadians(camera.yRot() + 180.0F));
		Matrix4f projection = new Matrix4f().setPerspective(
				(float) Math.toRadians(mc.options.fov().get()),
				(float) width / (float) height,
				0.05F,
				mc.options.renderDistance().get() * 16.0F
		);

		view.transform(clip);
		projection.transform(clip);

		if (clip.w <= 0.0F) {
			return null;
		}

		float ndcX = clip.x / clip.w;
		float ndcY = clip.y / clip.w;
		float ndcZ = clip.z / clip.w;
		if (ndcZ < -1.0F || ndcZ > 1.0F) {
			return null;
		}

		float screenX = (ndcX * 0.5F + 0.5F) * width;
		float screenY = (1.0F - (ndcY * 0.5F + 0.5F)) * height;
		return new ProjectedPoint(screenX, screenY, ndcZ);
	}

	public record ProjectedPoint(float x, float y, float depth) {
	}
}
