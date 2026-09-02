package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.components.ShulkerBoxTooltipComponent;
import dev.lvstrng.argon.module.modules.render.NoBounce;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

import static dev.lvstrng.argon.Argon.mc;

@Mixin(ItemStack.class)
public class ItemStackMixin {
	@Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
	private void ghostor$shulkerBoxTooltip(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (!cir.getReturnValue().isPresent()) {
			cir.setReturnValue(ShulkerBoxTooltipComponent.create((ItemStack) (Object) this));
		}
	}

	@Inject(method = "getPopTime", at = @At("HEAD"), cancellable = true)
	private void removeBounceAnimation(CallbackInfoReturnable<Integer> cir) {
		if (mc.player == null) return;

		NoBounce noBounce = Argon.INSTANCE.getModuleManager().getModule(NoBounce.class);
		if (Argon.INSTANCE != null && mc.player != null && noBounce.isEnabled()) {
			ItemStack mainHandStack = mc.player.getMainHandItem();
			if (mainHandStack.is(Items.END_CRYSTAL)) {
				cir.setReturnValue(0);
			}
		}
	}
}
