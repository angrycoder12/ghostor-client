package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.module.modules.blatant.LegitSpeed;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMoveMixin {
	@Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
	private void ghostor$applyLegitSpeed(float friction, Vec3 movementInput, CallbackInfo ci) {
		if (LegitSpeed.applyMovement((Entity) (Object) this, friction, movementInput)) ci.cancel();
	}
}
