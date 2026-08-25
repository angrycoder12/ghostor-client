package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.EventManager;
import dev.lvstrng.argon.event.events.ButtonListener;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.SelfDestruct;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"))
	private void onPress(long window, int action, KeyEvent input, CallbackInfo ci) {
		EventManager.fire(new ButtonListener.ButtonEvent(input.key(), window, action));
	}
}
