package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.misc.ClientSpoof;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Changes only the brand value consumed by Minecraft's normal handshake packet. */
@Mixin(ClientBrandRetriever.class)
public abstract class ClientBrandRetrieverMixin {
    @Inject(method = "getClientModName", at = @At("RETURN"), cancellable = true)
    private static void ghostor$spoofClientBrand(CallbackInfoReturnable<String> cir) {
        if (Argon.INSTANCE == null || Argon.INSTANCE.getModuleManager() == null) return;

        ClientSpoof spoof = Argon.INSTANCE.getModuleManager().getModule(ClientSpoof.class);
        if (spoof != null && spoof.isEnabled()) cir.setReturnValue(spoof.getSpoofedBrand());
    }
}
