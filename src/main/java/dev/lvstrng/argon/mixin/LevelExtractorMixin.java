package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.event.EventManager;
import dev.lvstrng.argon.event.events.GameRenderListener;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Inject(
			method = "extract",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractGizmos()V")
	)
	private void onWorldExtract(DeltaTracker deltaTracker, Camera camera, float delta, CallbackInfo ci) {
		LevelExtractor extractor = (LevelExtractor) (Object) this;
		try (var ignored = extractor.collectPerFrameMainThreadGizmos()) {
			EventManager.fire(new GameRenderListener.GameRenderEvent(delta));
		}
	}
}
