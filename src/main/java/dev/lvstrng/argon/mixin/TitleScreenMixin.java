package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.gui.GhostorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ghostor$branding(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        String label = "ghostor client :)";
        int width = minecraft.font.width(label);
        int x = Math.max(6, Math.min(10, context.guiWidth() - width - 8));
        int y = 8;
        context.fill(x - 4, y - 3, x + width + 4, y + minecraft.font.lineHeight + 3, 0x7A11161F);
        context.text(minecraft.font, label, x, y, GhostorTheme.TEXT_MUTED.getRGB(), false);
    }
}
