package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.misc.SafeWalk;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes vanilla's own edge-clamping path available without faking sneak state. */
@Mixin(Player.class)
public abstract class PlayerSafeWalkMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("RETURN"), cancellable = true)
    private void ghostor$safeWalk(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if ((Object) this != minecraft.player || Argon.INSTANCE == null || Argon.INSTANCE.getModuleManager() == null) {
            return;
        }
        SafeWalk safeWalk = Argon.INSTANCE.getModuleManager().getModule(SafeWalk.class);
        if (safeWalk != null && safeWalk.shouldPreventEdge()) {
            cir.setReturnValue(true);
        }
    }
}
