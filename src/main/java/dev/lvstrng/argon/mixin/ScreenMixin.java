package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.gui.ClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {

	@Shadow
	@Nullable
	protected Minecraft minecraft;

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void dontRenderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (this.minecraft.gui.screen() instanceof ClickGui) {
			ci.cancel();
		}
	}
}
