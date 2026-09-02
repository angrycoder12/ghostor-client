package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.gui.components.MapTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;

@Mixin(MapItem.class)
public abstract class MapItemMixin extends Item {
	protected MapItemMixin(Properties properties) {
		super(properties);
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return MapTooltipComponent.create(stack);
	}
}
