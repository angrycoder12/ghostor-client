package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.render.trajectory.ProjectilePathSimulator;
import dev.lvstrng.argon.module.modules.render.trajectory.ProjectilePathSimulator.Path;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ColorSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.RenderUtils;
import java.awt.Color;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Projectile-specific prediction based on Meteor's simulator behavior. */
public final class Trajectories extends Module implements GameRenderListener {
	private final NumberSetting thickness = new NumberSetting("Line Thickness", 1, 10, 2, 1);
	private final ColorSetting lineColor = new ColorSetting("Line Color", new Color(116, 92, 255, 210));
	private final BooleanSetting impactBox = new BooleanSetting("Impact Box", true);
	private final ColorSetting impactColor = new ColorSetting("Impact Box Color", new Color(145, 125, 255, 110));
	private final BooleanSetting allProjectiles = new BooleanSetting("All Projectiles", false);
	private final BooleanSetting throughWalls = new BooleanSetting("Through Walls", false);
	private final NumberSetting maxSteps = new NumberSetting("Max Steps", 50, 600, 250, 10);
	private final ProjectilePathSimulator simulator = new ProjectilePathSimulator();

	public Trajectories() {
		super(EncryptedString.of("Trajectories"),
				EncryptedString.of("Shows projectile paths and predicted impact points"), -1, Category.RENDER);
		addSettings(thickness, lineColor, impactBox, impactColor, allProjectiles, throughWalls, maxSteps);
	}

	@Override public void onEnable() { eventManager.add(GameRenderListener.class, this); super.onEnable(); }
	@Override public void onDisable() { eventManager.remove(GameRenderListener.class, this); super.onDisable(); }

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
		Path path = simulator.simulate(mc.player, event.delta, allProjectiles.getValue(), maxSteps.getValueInt());
		if (path == null || path.points().size() < 2) return;
		for (int i = 1; i < path.points().size(); i++) {
			RenderUtils.renderLine(lineColor.getColor(), path.points().get(i - 1), path.points().get(i),
					thickness.getValueFloat(), throughWalls.getValue());
		}
		if (impactBox.getValue() && path.hit() != null && path.hit().getType() != HitResult.Type.MISS) {
			AABB marker = impactMarker(path.hit());
			Color fill = impactColor.getColor();
			Color outline = new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), Math.max(150, fill.getAlpha()));
			RenderUtils.renderFilledBox(marker, fill, throughWalls.getValue());
			RenderUtils.renderBoxOutline(marker, outline, Math.max(1.0F, thickness.getValueFloat()), throughWalls.getValue());
		}
	}

	private static AABB impactMarker(HitResult hit) {
		Vec3 point = hit.getLocation();
		double half = 0.22D;
		double thin = 0.015D;
		if (hit instanceof BlockHitResult block) {
			Direction.Axis axis = block.getDirection().getAxis();
			return switch (axis) {
				case X -> new AABB(point.x - thin, point.y - half, point.z - half,
						point.x + thin, point.y + half, point.z + half);
				case Y -> new AABB(point.x - half, point.y - thin, point.z - half,
						point.x + half, point.y + thin, point.z + half);
				case Z -> new AABB(point.x - half, point.y - half, point.z - thin,
						point.x + half, point.y + half, point.z + thin);
			};
		}
		return new AABB(point.x - half, point.y - half, point.z - half,
				point.x + half, point.y + half, point.z + half);
	}
}
