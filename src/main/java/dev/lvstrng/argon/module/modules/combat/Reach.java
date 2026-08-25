package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Extends the local player's entity interaction range when its filters pass. */
public final class Reach extends Module {
    private final MinMaxSetting reach = new MinMaxSetting(
            EncryptedString.of("Reach (Blocks)"), 3, 6, .05, 3.1, 3.3);
    private final BooleanSetting weaponOnly = new BooleanSetting(EncryptedString.of("Weapon only"), false);
    private final BooleanSetting movingOnly = new BooleanSetting(EncryptedString.of("Moving only"), false);
    private final BooleanSetting sprintOnly = new BooleanSetting(EncryptedString.of("Sprint only"), false);

    public Reach() {
        super(EncryptedString.of("Reach"), EncryptedString.of("Extends your attack reach with configurable conditions"), -1, Category.COMBAT);
        addSettings(reach, weaponOnly, movingOnly, sprintOnly);
    }

    public double getReach(double vanillaReach) {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return vanillaReach;
        }
        if (weaponOnly.getValue() && !WorldUtils.isWeapon(mc.player.getMainHandItem())) {
            return vanillaReach;
        }
        if (movingOnly.getValue() && mc.player.input.getMoveVector().lengthSquared() == 0) {
            return vanillaReach;
        }
        if (sprintOnly.getValue() && !mc.player.isSprinting()) {
            return vanillaReach;
        }

        double selected = reach.getMinValue() == reach.getMaxValue()
                ? reach.getMinValue()
                : ThreadLocalRandom.current().nextDouble(reach.getMinValue(), reach.getMaxValue());
        double vanillaBonus = Math.max(0, vanillaReach - Player.DEFAULT_ENTITY_INTERACTION_RANGE);
        return selected + vanillaBonus;
    }
}
