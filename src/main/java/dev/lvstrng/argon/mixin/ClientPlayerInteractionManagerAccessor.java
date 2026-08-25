package dev.lvstrng.argon.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface ClientPlayerInteractionManagerAccessor {
	@Invoker("ensureHasSentCarriedItem")
	void syncSlot();
}
