package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.render.Nametags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(
            method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void ghostor$replaceVanillaNameTag(Entity entity, EntityRenderState state, float delta, CallbackInfo ci) {
        if (!(entity instanceof Player player) || Argon.INSTANCE == null || Argon.INSTANCE.getModuleManager() == null) {
            return;
        }
        Nametags nametags = Argon.INSTANCE.getModuleManager().getModule(Nametags.class);
        if (nametags != null && nametags.shouldHideVanilla(player)) {
            state.nameTag = null;
            state.scoreText = null;
        }
    }
}
