package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.item.Items;

public final class TotemOffhand extends Module implements TickListener {
    private final NumberSetting switchDelay = new NumberSetting(EncryptedString.of("Switch Delay"), 0, 5, 0, 1);
    private final NumberSetting equipDelay = new NumberSetting(EncryptedString.of("Equip Delay"), 1, 5, 1, 1);
    private final BooleanSetting switchBack = new BooleanSetting(EncryptedString.of("Switch Back"), false);

    private int switchClock, equipClock, switchBackClock;
    private int previousSlot = -1;
    boolean sent, active = false;

    public TotemOffhand() {
        super(EncryptedString.of("Totem Offhand"), EncryptedString.of("Switches to your totem slot and offhands a totem if you dont have one already"), -1, Category.COMBAT);
        addSettings(switchDelay, equipDelay, switchBack);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        reset();

        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if(mc.gui.screen() != null)
            return;

        if(mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING)
            active = true;

        if(active) {
			if (switchClock < switchDelay.getValueInt()) {
				switchClock++;
				return;
			}

			if(previousSlot == -1)
				previousSlot = mc.player.getInventory().getSelectedSlot();

            if (InventoryUtils.selectItemFromHotbar(Items.TOTEM_OF_UNDYING)) {
                if (equipClock < equipDelay.getValueInt()) {
                    equipClock++;
                    return;
                }

                if (!sent) {
                    mc.getConnection().getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                    sent = true;
                    return;
                }
            }

            if(switchBackClock < switchDelay.getValue()) {
                switchBackClock++;
            } else {
                if(switchBack.getValue())
                    InventoryUtils.setInvSlot(previousSlot);

                reset();
            }
        }
    }

    public void reset() {
        switchClock = 0;
        equipClock = 0;
        switchBackClock = 0;
        previousSlot = -1;

        sent = false;
        active = false;
    }
}
