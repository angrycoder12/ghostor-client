package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.event.events.PacketSendListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.*;
import org.joml.Matrix3x2fStack;

import java.awt.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class TargetHud extends Module implements HudListener, PacketSendListener {
	private final NumberSetting xCoord = new NumberSetting(EncryptedString.of("X"), 0, 1920, 500, 1);
	private final NumberSetting yCoord = new NumberSetting(EncryptedString.of("Y"), 0, 1080, 500, 1);
	private final BooleanSetting hudTimeout = new BooleanSetting(EncryptedString.of("Timeout"), true)
			.setDescription(EncryptedString.of("Target hud will disappear after 10 seconds"));
	private long lastAttackTime = 0;
	public static float animation;
	private static final long timeout = 10000;

	public TargetHud() {
		super(EncryptedString.of("Target HUD"),
				EncryptedString.of("Gives you information about the enemy player"),
				-1,
				Category.RENDER);
		addSettings(xCoord, yCoord, hudTimeout);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		eventManager.add(PacketSendListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		eventManager.remove(PacketSendListener.class, this);
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		GuiGraphicsExtractor context = event.context;

		int x = xCoord.getValueInt();
		int y = yCoord.getValueInt();

		RenderUtils.unscaledProjection(context);
		if ((!hudTimeout.getValue() || (System.currentTimeMillis() - lastAttackTime <= timeout)) &&
				mc.player.getLastHurtMob() != null && mc.player.getLastHurtMob() instanceof Player player && player.isAlive()) {
			animation = RenderUtils.fast(animation, mc.player.getLastHurtMob() instanceof Player player1 && player1.isAlive() ? 0 : 1, 15f);

			PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
			Matrix3x2fStack matrices = context.pose();
			matrices.pushMatrix();
			matrices.scaleAround(Math.max(0.0f, 1.0f - animation), 1.0f, x + 170.0f, y + 100.0f);

		RenderUtils.renderRoundedQuad(context, new Color(0, 0, 0, 175), x, y, x + 340, y + 200, 5, 5, 5, 5, 10);
		RenderUtils.renderRoundedQuad(context, Utils.getMainColor(255, 1), x, y + 27, x + 340, y + 29, 0, 0, 0, 0, 10);

			TextRenderer.drawString(player.getName().getString() + " - " + MathUtils.roundToDecimal(player.distanceTo(mc.player), 0.5) + " blocks", context, x + 23, y + 5, Color.WHITE.getRGB());

			if (entry == null) {
				int charOff1 = x + 5;
				CharSequence chars = "Type: Bot";

				TextRenderer.drawString(chars, context, charOff1, y + 35, new Color(255, 80, 80, 255).getRGB());

				matrices.popMatrix();
				RenderUtils.scaledProjection(context);
				return;
			} else {
				int charOff1 = x + 5;
				CharSequence chars = "Type: Player";

				TextRenderer.drawString(chars, context, charOff1, y + 35, Color.white.getRGB());
			}

			TextRenderer.drawString("Health: " + Math.round((player.getHealth() + player.getAbsorptionAmount())), context, x + 5, y + 65, Color.GREEN.getRGB());
			context.fill(x, y + 200, x + 4, (y + 200) - Math.min(Math.round((player.getHealth() + player.getAbsorptionAmount()) * 5), 171), Color.GREEN.darker().getRGB());
			//RenderUtils.renderRoundedOutline(context, Color.green, x, y + 200, x, (y + 200) - Math.min(Math.round((player.getHealth() + player.getAbsorptionAmount()) * 5), 174), 0, 0, 0, 0, 3, 30);

			TextRenderer.drawString("Invisible: " + (player.isInvisible() ? "Yes" : "No"), context, x + 5, y + 95, Color.WHITE.getRGB());

			TextRenderer.drawString("Ping: " + entry.getLatency(), context, x + 5, y + 125, Color.WHITE.getRGB());

			PlayerFaceExtractor.extractRenderState(context, entry.getSkin(), x + 3, y + 3, 20);

			if (player.hurtTime != 0) {
				int charOff1 = x + 125;
				CharSequence chars = ("Damage Tick: " + player.hurtTime);

				TextRenderer.drawString(chars, context, charOff1, y + 65, Color.WHITE.getRGB());
				//TextRenderer.drawString("Damage Tick: " + player.hurtTime, context, x + 125, y + 65, Color.WHITE.getRGB());
				context.fill(x + 125, y + 80, (x + 125) + (player.hurtTime * 15), y + 83, getDamageTickColor(player.hurtTime).getRGB());
			}
			matrices.popMatrix();
		} else {
			animation = RenderUtils.fast(animation, 1, 15f);
		}
		RenderUtils.scaledProjection(context);
	}

	private Color getDamageTickColor(int hurtTime) {
		return switch (hurtTime) {
			case 0 -> null;
			case 10 -> new Color(255, 0, 0, 255);
			case 9 -> new Color(255, 50, 0, 255);
			case 8 -> new Color(255, 100, 0, 255);
			case 7 -> new Color(255, 150, 0, 255);
			case 6 -> new Color(255, 255, 0, 255);
			case 5 -> new Color(200, 255, 0, 255);
			case 4 -> new Color(175, 255, 0, 255);
			case 3 -> new Color(100, 255, 0, 255);
			case 2 -> new Color(50, 255, 0, 255);
			case 1 -> new Color(0, 255, 0, 255);
			default -> throw new IllegalStateException("uv" + hurtTime);
		};
	}

	@Override
	public void onPacketSend(PacketSendListener.PacketSendEvent event) {
		if (event.packet instanceof ServerboundInteractPacket packet
				&& packet.hand() == null && packet.location() == null
				&& mc.crosshairPickEntity instanceof Player) {
			lastAttackTime = System.currentTimeMillis();
		}
	}
}
