package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.module.modules.render.Fullbright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public final class LightmapRenderStateExtractorMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void ghostor$applyFullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		Fullbright.Mode mode = Fullbright.activeMode();
		if (mode == Fullbright.Mode.GAMMA) {
			state.brightness = 1.0F;
		} else if (mode == Fullbright.Mode.NIGHTVISION) {
			state.nightVisionEffectIntensity = 1.0F;
		}
	}
}
