package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.modules.combat.Backtrack;
import dev.lvstrng.argon.module.modules.misc.AntiBot;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ColorSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.ColorUtils;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.ProjectionUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import java.awt.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PlayerESP extends Module implements GameRenderListener, HudListener {
	public enum Mode {
		TwoD, ThreeD
	}

	public final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.ThreeD, Mode.class);
	private final NumberSetting alpha = new NumberSetting(EncryptedString.of("Alpha"), 0, 255, 100, 1);
	private final NumberSetting width = new NumberSetting(EncryptedString.of("Line width"), 1, 10, 1, 1);
	private final BooleanSetting tracers = new BooleanSetting(EncryptedString.of("Tracers"), false)
			.setDescription(EncryptedString.of("Draws a line from your player to the other"));
	private final ColorSetting tracerColor = new ColorSetting(
			EncryptedString.of("Tracer Color"), new Color(145, 125, 255, 210));
	private final BooleanSetting threeDOutline = new BooleanSetting(EncryptedString.of("3D box outline"), false);
	private final BooleanSetting twoDOutline = new BooleanSetting(EncryptedString.of("2D Outline"), false);

	public PlayerESP() {
		super(EncryptedString.of("Player ESP"),
				EncryptedString.of("Renders players through walls"),
				-1,
				Category.RENDER);
		addSettings(alpha, mode, threeDOutline, twoDOutline, width, tracers,
				tracerColor.visibleWhen(tracers::getValue));
	}

	@Override
	public void onEnable() {
		eventManager.add(GameRenderListener.class, this);
		eventManager.add(HudListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(GameRenderListener.class, this);
		eventManager.remove(HudListener.class, this);
		super.onDisable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.level == null || mc.player == null) {
			return;
		}

		for (Player player : mc.level.players()) {
			if (!shouldRender(player)) {
				continue;
			}

			AABB box = getRenderBox(player, event.delta).inflate(0.02);
			if (mode.isMode(Mode.ThreeD)) {
				RenderUtils.renderFilledBox(box, getColor(alpha.getValueInt()).brighter());
				if (threeDOutline.getValue())
					RenderUtils.renderBoxOutline(box, getColor(255), width.getValueInt());
			}

			if (tracers.getValue()) {
				Vec3 start = RenderUtils.getCameraPos().add(mc.player.getViewVector(event.delta).scale(0.12D));
				RenderUtils.renderLine(tracerColor.getColor(), start, box.getCenter(),
						Math.max(1.0F, width.getValueFloat()), true);
			}
		}
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (!mode.isMode(Mode.TwoD) || mc.level == null || mc.player == null) {
			return;
		}

		for (Player player : mc.level.players()) {
			if (!shouldRender(player)) {
				continue;
			}

			ScreenBounds bounds = projectBounds(getRenderBox(player, event.delta));
			if (bounds == null || !bounds.isValid()) {
				continue;
			}

			Color outlineColor = getColor(255);
			int thickness = Math.max(1, width.getValueInt());
			if (twoDOutline.getValue()) {
				Color borderColor = new Color(0, 0, 0, Math.min(255, outlineColor.getAlpha()));
				drawOutline(event.context, bounds, thickness + 1, borderColor);
			}
			drawOutline(event.context, bounds, thickness, outlineColor);
		}
	}

	private boolean shouldRender(Entity entity) {
		return entity instanceof Player player && player != mc.player && player.isAlive()
				&& !player.isRemoved() && !player.isSpectator() && !AntiBot.renderBot(player);
	}

	private AABB getRenderBox(Player player, float tickDelta) {
		// ESP shows the newest server-derived position while combat keeps using the
		// intentionally delayed entity box.
		return Backtrack.getRealBox(player, tickDelta);
	}

	private ScreenBounds projectBounds(AABB box) {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		int visibleCorners = 0;

		for (double x : new double[]{box.minX, box.maxX}) {
			for (double y : new double[]{box.minY, box.maxY}) {
				for (double z : new double[]{box.minZ, box.maxZ}) {
					ProjectionUtils.ProjectedPoint projected = ProjectionUtils.project(new Vec3(x, y, z));
					if (projected == null) {
						continue;
					}

					visibleCorners++;
					minX = Math.min(minX, projected.x());
					minY = Math.min(minY, projected.y());
					maxX = Math.max(maxX, projected.x());
					maxY = Math.max(maxY, projected.y());
				}
			}
		}

		if (visibleCorners == 0) {
			return null;
		}

		return new ScreenBounds((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY));
	}

	private void drawOutline(GuiGraphicsExtractor context, ScreenBounds bounds, int thickness, Color color) {
		int packedColor = color.getRGB();
		context.fill(bounds.minX, bounds.minY, bounds.maxX, bounds.minY + thickness, packedColor);
		context.fill(bounds.minX, bounds.maxY - thickness, bounds.maxX, bounds.maxY, packedColor);
		context.fill(bounds.minX, bounds.minY + thickness, bounds.minX + thickness, bounds.maxY - thickness, packedColor);
		context.fill(bounds.maxX - thickness, bounds.minY + thickness, bounds.maxX, bounds.maxY - thickness, packedColor);
	}

	private Color getColor(int alpha) {
		int red = ClickGUI.red.getValueInt();
		int green = ClickGUI.green.getValueInt();
		int blue = ClickGUI.blue.getValueInt();

		if (ClickGUI.rainbow.getValue())
			return ColorUtils.getBreathingRGBColor(1, alpha);
		else
			return new Color(red, green, blue, alpha);
	}

	private record ScreenBounds(int minX, int minY, int maxX, int maxY) {
		private boolean isValid() {
			return maxX > minX && maxY > minY;
		}
	}
}
