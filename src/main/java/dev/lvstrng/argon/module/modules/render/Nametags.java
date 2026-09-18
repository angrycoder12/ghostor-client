package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.module.modules.misc.AntiBot;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.ProjectionUtils;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.awt.Color;
import java.util.Locale;

/** Backend-neutral 2D nameplates projected from player world positions. */
public final class Nametags extends Module implements HudListener {
    private static final int HEAD_SIZE = 11;
    private static final int HEAD_GAP = 3;
    private static final double FULL_SCALE_DISTANCE = 8.0;
    private static final double MIN_SCALE_DISTANCE = 96.0;
    private static final float MIN_SCALE = 0.55F;

    private final NumberSetting offset = new NumberSetting(EncryptedString.of("Offset"), -40, 40, 0, 1);
    private final BooleanSetting rect = new BooleanSetting(EncryptedString.of("Rect"), true);
    private final BooleanSetting showHealth = new BooleanSetting(EncryptedString.of("Show health"), true);
    private final BooleanSetting showInvis = new BooleanSetting(EncryptedString.of("Show invis"), true);
    private final BooleanSetting showPlayerHead = new BooleanSetting(EncryptedString.of("Show Player Head"), false);
    private final BooleanSetting removeTags = new BooleanSetting(EncryptedString.of("Remove tags"), false);

    public Nametags() {
        super(EncryptedString.of("Nametags"), EncryptedString.of("Renders custom player nametags"), -1, Category.RENDER);
        addSettings(offset, rect, showHealth, showInvis, showPlayerHead, removeTags);
    }

    @Override
    public void onEnable() {
        eventManager.add(HudListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(HudListener.class, this);
        super.onDisable();
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (removeTags.getValue() || mc.level == null || mc.player == null) {
            return;
        }

        for (Player player : mc.level.players()) {
            if (!shouldDraw(player)) {
                continue;
            }

            double x = Mth.lerp(event.delta, player.xo, player.getX());
            double y = Mth.lerp(event.delta, player.yo, player.getY()) + player.getBbHeight() + .5;
            double z = Mth.lerp(event.delta, player.zo, player.getZ());
            Vec3 tagPosition = new Vec3(x, y, z);
            ProjectionUtils.ProjectedPoint point = ProjectionUtils.project(tagPosition);
            if (point == null) {
                continue;
            }

            String name = player.getDisplayName().getString();
            String health = showHealth.getValue()
                    ? " " + String.format(Locale.ROOT, "%.1f", player.getHealth())
                    : "";
            int nameWidth = mc.font.width(name);
            int healthWidth = mc.font.width(health);
            boolean drawHead = showPlayerHead.getValue();
            int headWidth = drawHead ? HEAD_SIZE + HEAD_GAP : 0;
            int totalWidth = headWidth + nameWidth + healthWidth;
            int contentHeight = Math.max(mc.font.lineHeight, drawHead ? HEAD_SIZE : 0);
            int drawX = Math.round(point.x()) - totalWidth / 2;
            int drawY = Math.round(point.y()) - 10 - offset.getValueInt();
            float scale = distanceScale(mc.gameRenderer.mainCamera().position().distanceTo(tagPosition));
            float centerX = drawX + totalWidth / 2.0F;
            float centerY = drawY + contentHeight / 2.0F;
            float halfWidth = (totalWidth + 6) * scale / 2.0F;
            float halfHeight = (contentHeight + 4) * scale / 2.0F;
            if (centerX + halfWidth < 0 || centerX - halfWidth > event.context.guiWidth()
                    || centerY + halfHeight < 0 || centerY - halfHeight > event.context.guiHeight()) {
                continue;
            }

            Matrix3x2fStack pose = event.context.pose();
            pose.pushMatrix();
            try {
                pose.scaleAround(scale, scale, centerX, centerY);
                if (rect.getValue()) {
                    event.context.fill(drawX - 3, drawY - 2, drawX + totalWidth + 3,
                            drawY + contentHeight + 2, 0x66000000);
                }
                if (drawHead) {
                    PlayerFaceExtractor.extractRenderState(event.context, playerSkin(player),
                            drawX, drawY, HEAD_SIZE);
                }
                int textX = drawX + headWidth;
                int textY = drawY + (contentHeight - mc.font.lineHeight) / 2;
                int nameColor = Argon.INSTANCE.getFriendManager().isFriend(player)
                        ? GhostorTheme.SUCCESS.getRGB()
                        : Color.WHITE.getRGB();
                event.context.text(mc.font, name, textX, textY, nameColor, false);
                if (!health.isEmpty()) {
                    event.context.text(mc.font, health, textX + nameWidth, textY, healthColor(player), false);
                }
            } finally {
                pose.popMatrix();
            }
        }
    }

    public boolean shouldHideVanilla(Player player) {
        return isEnabled() && player != mc.player && player.isAlive();
    }

	private boolean shouldDraw(Player player) {
		return player != mc.player && player.isAlive() && !AntiBot.renderBot(player)
				&& (showInvis.getValue() || !player.isInvisible());
	}

    private PlayerSkin playerSkin(Player player) {
        if (player instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer.getSkin();
        }
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(player.getUUID());
        return info == null ? DefaultPlayerSkin.get(player.getUUID()) : info.getSkin();
    }

    private static float distanceScale(double distance) {
        double progress = Mth.clamp(
                (distance - FULL_SCALE_DISTANCE) / (MIN_SCALE_DISTANCE - FULL_SCALE_DISTANCE),
                0.0,
                1.0
        );
        return (float) Mth.lerp(progress, 1.0, MIN_SCALE);
    }

    private static int healthColor(Player player) {
        float ratio = player.getMaxHealth() <= 0 ? 0 : player.getHealth() / player.getMaxHealth();
        return ratio < .3F ? Color.RED.getRGB()
                : ratio < .5F ? Color.ORANGE.getRGB()
                : ratio < .7F ? Color.YELLOW.getRGB()
                : Color.GREEN.getRGB();
    }
}
