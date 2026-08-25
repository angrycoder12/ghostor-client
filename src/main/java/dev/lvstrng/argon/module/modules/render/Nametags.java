package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.ProjectionUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.Locale;

/** Backend-neutral 2D nameplates projected from player world positions. */
public final class Nametags extends Module implements HudListener {
    private final NumberSetting offset = new NumberSetting(EncryptedString.of("Offset"), -40, 40, 0, 1);
    private final BooleanSetting rect = new BooleanSetting(EncryptedString.of("Rect"), true);
    private final BooleanSetting showHealth = new BooleanSetting(EncryptedString.of("Show health"), true);
    private final BooleanSetting showInvis = new BooleanSetting(EncryptedString.of("Show invis"), true);
    private final BooleanSetting removeTags = new BooleanSetting(EncryptedString.of("Remove tags"), false);

    public Nametags() {
        super(EncryptedString.of("Nametags"), EncryptedString.of("Renders custom player nametags"), -1, Category.RENDER);
        addSettings(offset, rect, showHealth, showInvis, removeTags);
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
            ProjectionUtils.ProjectedPoint point = ProjectionUtils.project(new Vec3(x, y, z));
            if (point == null) {
                continue;
            }

            String name = player.getDisplayName().getString();
            String health = showHealth.getValue()
                    ? " " + String.format(Locale.ROOT, "%.1f", player.getHealth())
                    : "";
            int nameWidth = mc.font.width(name);
            int healthWidth = mc.font.width(health);
            int totalWidth = nameWidth + healthWidth;
            int drawX = Math.round(point.x()) - totalWidth / 2;
            int drawY = Math.round(point.y()) - 10 - offset.getValueInt();
            if (drawX + totalWidth < 0 || drawX > event.context.guiWidth()
                    || drawY + mc.font.lineHeight < 0 || drawY > event.context.guiHeight()) {
                continue;
            }

            if (rect.getValue()) {
                event.context.fill(drawX - 3, drawY - 2, drawX + totalWidth + 3,
                        drawY + mc.font.lineHeight + 2, 0x66000000);
            }
            int nameColor = Argon.INSTANCE.getFriendManager().isFriend(player)
                    ? GhostorTheme.SUCCESS.getRGB()
                    : Color.WHITE.getRGB();
            event.context.text(mc.font, name, drawX, drawY, nameColor, false);
            if (!health.isEmpty()) {
                event.context.text(mc.font, health, drawX + nameWidth, drawY, healthColor(player), false);
            }
        }
    }

    public boolean shouldHideVanilla(Player player) {
        return isEnabled() && player != mc.player && player.isAlive();
    }

    private boolean shouldDraw(Player player) {
        return player != mc.player && player.isAlive() && (showInvis.getValue() || !player.isInvisible());
    }

    private static int healthColor(Player player) {
        float ratio = player.getMaxHealth() <= 0 ? 0 : player.getHealth() / player.getMaxHealth();
        return ratio < .3F ? Color.RED.getRGB()
                : ratio < .5F ? Color.ORANGE.getRGB()
                : ratio < .7F ? Color.YELLOW.getRGB()
                : Color.GREEN.getRGB();
    }
}
