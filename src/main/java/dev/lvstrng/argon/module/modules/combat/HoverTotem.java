package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.mixin.HandledScreenMixin;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

public final class HoverTotem extends Module implements TickListener {
	private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0, 20, 0, 1);
	private final BooleanSetting hotbar = new BooleanSetting(EncryptedString.of("Hotbar"), true).setDescription(EncryptedString.of("Puts a totem in your hotbar as well, if enabled (Setting below will work if this is enabled)"));
	private final NumberSetting slot = new NumberSetting(EncryptedString.of("Totem Slot"), 1, 9, 1, 1)
			.setDescription(EncryptedString.of("Your preferred totem slot"));
	private final BooleanSetting autoSwitch = new BooleanSetting(EncryptedString.of("Auto Switch"), false)
			.setDescription(EncryptedString.of("Switches to totem slot when going inside the inventory"));

	private int clock;

	public HoverTotem() {
		super(EncryptedString.of("Hover Totem"),
				EncryptedString.of("Equips a totem in your totem and offhand slots if a totem is hovered"),
				-1,
				Category.COMBAT);
		addSettings(delay, hotbar, slot, autoSwitch);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		clock = 0;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.gui.screen() instanceof InventoryScreen inv) {
			Slot hoveredSlot = ((HandledScreenMixin) inv).getHoveredSlot();

			if (autoSwitch.getValue())
				mc.player.getInventory().setSelectedSlot(slot.getValueInt() - 1);

			if (hoveredSlot != null) {
				int slot = hoveredSlot.getContainerSlot();

				if (slot > 35)
					return;

				int totem = this.slot.getValueInt() - 1;

				if (hoveredSlot.getItem().getItem() == Items.TOTEM_OF_UNDYING) {
					if (hotbar.getValue() && mc.player.getInventory().getItem(totem).getItem() != Items.TOTEM_OF_UNDYING) {
						if (clock > 0) {
							clock--;
							return;
						}

						mc.gameMode.handleContainerInput(inv.getMenu().containerId, slot, totem, ContainerInput.SWAP, mc.player);
						clock = delay.getValueInt();
					} else if (!mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
						if (clock > 0) {
							clock--;
							return;
						}

						mc.gameMode.handleContainerInput(inv.getMenu().containerId, slot, 40, ContainerInput.SWAP, mc.player);
						clock = delay.getValueInt();
					}
				}
			}
		} else clock = delay.getValueInt();
	}
}
