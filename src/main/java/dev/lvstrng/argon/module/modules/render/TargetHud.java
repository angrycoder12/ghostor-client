package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.event.events.EntityAttackListener;
import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.MathUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import dev.lvstrng.argon.utils.Utils;
import dev.lvstrng.argon.utils.WorldUtils;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;

/** Displays the player targeted by the latest committed client attack. */
public final class TargetHud extends Module implements HudListener, EntityAttackListener {
	private static final int PANEL_WIDTH = 340;
	private static final int PANEL_HEIGHT = 200;
	private static final long TIMEOUT_MILLIS = 10_000L;
	private static TargetHud instance;

	private final NumberSetting xCoord = new NumberSetting(EncryptedString.of("X"), 0, 1920, 500, 1);
	private final NumberSetting yCoord = new NumberSetting(EncryptedString.of("Y"), 0, 1080, 500, 1);
	private final BooleanSetting hudTimeout = new BooleanSetting(EncryptedString.of("Timeout"), true)
			.setDescription(EncryptedString.of("Target hud will disappear after 10 seconds"));

	private ClientLevel trackedLevel;
	private int targetEntityId = -1;
	private long lastAttackTime;
	public static float animation = 1.0F;

	public TargetHud() {
		super(EncryptedString.of("Target HUD"),
				EncryptedString.of("Gives you information about the enemy player"),
				-1,
				Category.RENDER);
		addSettings(xCoord, yCoord, hudTimeout);
		instance = this;
	}

	@Override
	public void onEnable() {
		trackedLevel = mc.level;
		clearTarget();
		if (mc.player != null && mc.player.getLastHurtMob() instanceof Player player && player.isAlive()) {
			targetEntityId = player.getId();
			lastAttackTime = nowMillis();
		}
		eventManager.add(HudListener.class, this);
		eventManager.add(EntityAttackListener.class, this, 1000);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		eventManager.remove(EntityAttackListener.class, this);
		clearTarget();
		trackedLevel = null;
		super.onDisable();
	}

	@Override
	public void onEntityAttack(EntityAttackEvent event) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (trackedLevel != mc.level) {
			clearTarget();
			trackedLevel = mc.level;
		}
		Entity entity = event.target;
		if (entity instanceof Player player && WorldUtils.isValidCombatPlayer(player)) {
			targetEntityId = player.getId();
			lastAttackTime = nowMillis();
			animation = Math.max(animation, 0.15F);
		}
	}

	@Override
	public void onRenderHud(HudEvent event) {
		Player target = resolveTarget();
		if (target == null || hudTimeout.getValue() && nowMillis() - lastAttackTime > TIMEOUT_MILLIS) {
			animation = RenderUtils.fast(animation, 1.0F, 15.0F);
			return;
		}

		animation = RenderUtils.fast(animation, 0.0F, 15.0F);
		int x = clamp(xCoord.getValueInt(), 0, Math.max(0, mc.getWindow().getWidth() - PANEL_WIDTH));
		int y = clamp(yCoord.getValueInt(), 0, Math.max(0, mc.getWindow().getHeight() - PANEL_HEIGHT));
		renderTarget(event.context, target, x, y);
	}

	private void renderTarget(GuiGraphicsExtractor context, Player player, int x, int y) {
		PlayerInfo playerInfo = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(player.getUUID());
		PlayerSkin skin = playerInfo == null ? DefaultPlayerSkin.get(player.getUUID()) : playerInfo.getSkin();
		float currentHealth = Math.max(0.0F, player.getHealth() + player.getAbsorptionAmount());
		float effectiveMaximum = Math.max(1.0F, player.getMaxHealth() + player.getAbsorptionAmount());
		float healthRatio = Math.max(0.0F, Math.min(1.0F, currentHealth / effectiveMaximum));
		int barHeight = Math.round(171.0F * healthRatio);

		RenderUtils.unscaledProjection(context);
		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();
		try {
			float scale = Math.max(0.0F, 1.0F - animation);
			matrices.scaleAround(scale, scale, x + PANEL_WIDTH / 2.0F, y + PANEL_HEIGHT / 2.0F);

			RenderUtils.renderRoundedQuad(context, new Color(0, 0, 0, 175),
					x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 5, 5, 5, 5, 10);
			RenderUtils.renderRoundedQuad(context, Utils.getMainColor(255, 1),
					x, y + 27, x + PANEL_WIDTH, y + 29, 0, 0, 0, 0, 10);

			PlayerFaceExtractor.extractRenderState(context, skin, x + 3, y + 3, 20);
			TextRenderer.drawString(player.getName().getString() + " - "
					+ MathUtils.roundToDecimal(player.distanceTo(mc.player), 0.5) + " blocks",
					context, x + 23, y + 5, Color.WHITE.getRGB());
			TextRenderer.drawString(playerInfo == null ? "Type: Bot" : "Type: Player",
					context, x + 5, y + 35,
					playerInfo == null ? new Color(255, 80, 80).getRGB() : Color.WHITE.getRGB());
			TextRenderer.drawString("Health: " + Math.round(currentHealth),
					context, x + 5, y + 65, Color.GREEN.getRGB());
			context.fill(x, y + PANEL_HEIGHT - barHeight, x + 4, y + PANEL_HEIGHT,
					Color.GREEN.darker().getRGB());
			TextRenderer.drawString("Invisible: " + (player.isInvisible() ? "Yes" : "No"),
					context, x + 5, y + 95, Color.WHITE.getRGB());
			TextRenderer.drawString("Ping: " + (playerInfo == null ? "N/A" : playerInfo.getLatency()),
					context, x + 5, y + 125, Color.WHITE.getRGB());

			if (player.hurtTime > 0) {
				int damageTick = Math.min(10, player.hurtTime);
				TextRenderer.drawString("Damage Tick: " + player.hurtTime,
						context, x + 125, y + 65, Color.WHITE.getRGB());
				context.fill(x + 125, y + 80, x + 125 + damageTick * 15, y + 83,
						getDamageTickColor(damageTick).getRGB());
			}
		} finally {
			matrices.popMatrix();
			RenderUtils.scaledProjection(context);
		}
	}

	private Player resolveTarget() {
		if (mc.player == null || mc.level == null) {
			clearTarget();
			trackedLevel = mc.level;
			return null;
		}
		if (trackedLevel != mc.level) {
			clearTarget();
			trackedLevel = mc.level;
			return null;
		}

		Entity entity = targetEntityId < 0 ? null : mc.level.getEntity(targetEntityId);
		if (!(entity instanceof Player player) || !WorldUtils.isValidCombatPlayer(player)) {
			clearTarget();
			return null;
		}
		return player;
	}

	public static void onWorldChanged() {
		TargetHud targetHud = instance;
		if (targetHud != null) {
			targetHud.clearTarget();
			targetHud.trackedLevel = null;
		}
	}

	private void clearTarget() {
		targetEntityId = -1;
		lastAttackTime = 0;
		animation = 1.0F;
	}

	private static Color getDamageTickColor(int hurtTime) {
		return switch (hurtTime) {
			case 10 -> new Color(255, 0, 0);
			case 9 -> new Color(255, 50, 0);
			case 8 -> new Color(255, 100, 0);
			case 7 -> new Color(255, 150, 0);
			case 6 -> new Color(255, 255, 0);
			case 5 -> new Color(200, 255, 0);
			case 4 -> new Color(175, 255, 0);
			case 3 -> new Color(100, 255, 0);
			case 2 -> new Color(50, 255, 0);
			default -> new Color(0, 255, 0);
		};
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static long nowMillis() {
		return System.nanoTime() / 1_000_000L;
	}
}
