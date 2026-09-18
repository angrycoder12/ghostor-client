package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.module.modules.render.PumpkinVision;
import java.util.Optional;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public abstract class HudMixin {
	@Redirect(method = "extractCameraOverlays", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/equipment/Equippable;cameraOverlay()Ljava/util/Optional;"))
	private Optional<Identifier> ghostor$hidePumpkinOverlay(Equippable equippable) {
		Optional<Identifier> overlay = equippable.cameraOverlay();
		if (!PumpkinVision.isHidingOverlay() || overlay.isEmpty()) return overlay;
		Equippable pumpkin = Items.CARVED_PUMPKIN.components().get(DataComponents.EQUIPPABLE);
		return pumpkin != null && pumpkin.cameraOverlay().equals(overlay) ? Optional.empty() : overlay;
	}
}
