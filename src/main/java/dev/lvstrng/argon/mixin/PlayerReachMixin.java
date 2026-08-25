package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.combat.Reach;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerReachMixin {
    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void ghostor$reach(CallbackInfoReturnable<Double> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((Object) this != minecraft.player || Argon.INSTANCE == null || Argon.INSTANCE.getModuleManager() == null) {
            return;
        }
        Reach reach = Argon.INSTANCE.getModuleManager().getModule(Reach.class);
        if (reach != null) {
            cir.setReturnValue(reach.getReach(cir.getReturnValue()));
        }
    }
}
