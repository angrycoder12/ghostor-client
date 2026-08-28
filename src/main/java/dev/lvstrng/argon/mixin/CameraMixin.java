package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.event.EventManager;
import dev.lvstrng.argon.event.events.CameraUpdateListener;
import dev.lvstrng.argon.module.modules.misc.Scaffold;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
	private void update(Args args) {
		CameraUpdateListener.CameraUpdateEvent event = new CameraUpdateListener.CameraUpdateEvent(args.get(0), args.get(1), args.get(2));
		EventManager.fire(event);

		args.set(0, event.getX());
		args.set(1, event.getY());
		args.set(2, event.getZ());
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void ghostor$applyScaffoldForwardCamera(float partialTick, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.getCameraType().isFirstPerson()
				&& Scaffold.shouldOverrideForwardCamera()) {
			setRotation(Scaffold.forwardCameraYaw(), Scaffold.forwardCameraPitch());
		}
	}
}
