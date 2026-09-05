package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ColorSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.RenderUtils;
import java.awt.Color;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Predicts the path of the bow currently being drawn by the local player. */
public final class Trajectories extends Module implements GameRenderListener {
	private static final double STEP_TIME = 0.1D;
	private static final double DRAG_PER_STEP = 0.999D;
	private static final double ARROW_GRAVITY = 0.05D;

	private final NumberSetting thickness = new NumberSetting(
			EncryptedString.of("Thickness"), 1, 10, 2, 1);
	private final ColorSetting color = new ColorSetting(
			EncryptedString.of("Color"), new Color(0, 255, 0, 191));
	private final BooleanSetting throughWalls = new BooleanSetting(
			EncryptedString.of("Through Walls"), false);
	private final NumberSetting maxSteps = new NumberSetting(
			EncryptedString.of("Max Steps"), 50, 1000, 400, 25);

	public Trajectories() {
		super(EncryptedString.of("Trajectories"),
				EncryptedString.of("Shows the predicted path of projectiles"),
				-1,
				Category.RENDER);
		addSettings(thickness, color, throughWalls, maxSteps);
	}

	@Override
	public void onEnable() {
		eventManager.add(GameRenderListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(GameRenderListener.class, this);
		super.onDisable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.player == null || mc.level == null || mc.gui.screen() != null || !mc.player.isUsingItem()) return;
		ItemStack bow = mc.player.getUseItem();
		if (!bow.is(Items.BOW)) return;

		int chargeTicks = mc.player.getTicksUsingItem();
		float power = BowItem.getPowerForTime(chargeTicks);
		if (power <= 0.0F) return;

		float yaw = (float) Math.toRadians(mc.player.getYRot());
		Vec3 position = mc.player.getPosition(event.delta).add(
				-Math.cos(yaw) * 0.16D,
				mc.player.getEyeHeight() - 0.1D,
				-Math.sin(yaw) * 0.16D);
		Vec3 motion = mc.player.getViewVector(event.delta).normalize().scale(power * 3.0D);
		Color lineColor = color.getColor();
		float lineWidth = thickness.getValueFloat();

		for (int step = 0; step < maxSteps.getValueInt(); step++) {
			Vec3 next = position.add(motion.scale(STEP_TIME));
			HitResult hit = mc.level.clip(new ClipContext(
					position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
			if (hit.getType() != HitResult.Type.MISS) next = hit.getLocation();

			RenderUtils.renderLine(lineColor, position, next, lineWidth, throughWalls.getValue());
			position = next;
			if (hit.getType() != HitResult.Type.MISS) break;

			motion = motion.scale(DRAG_PER_STEP).add(0.0D, -ARROW_GRAVITY * STEP_TIME, 0.0D);
		}
	}
}
