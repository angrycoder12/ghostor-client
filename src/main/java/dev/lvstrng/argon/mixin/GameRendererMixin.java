package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.misc.Freecam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
	private void onShouldRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
		if (Argon.INSTANCE.getModuleManager().getModule(Freecam.class).isEnabled())
			cir.setReturnValue(false);
	}
}
