package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.module.modules.misc.Freecam;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ViewArea.class)
public final class ViewAreaMixin {
	@Inject(method = "repositionCamera", at = @At("HEAD"), cancellable = true)
	private void ghostor$keepLoadedFreecamSections(SectionPos cameraSection,
			CallbackInfoReturnable<Boolean> cir) {
		if (Freecam.shouldKeepRenderSections()) {
			cir.setReturnValue(false);
		}
	}
}
