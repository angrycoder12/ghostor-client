package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.misc.NoBreakDelay;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {
	@Shadow
	private int destroyDelay;

	@Redirect(method = "continueDestroyBlock",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", opcode = Opcodes.GETFIELD, ordinal = 0))
	public int updateBlockBreakingProgress(MultiPlayerGameMode clientPlayerInteractionManager) {
		int cooldown = this.destroyDelay;
		return Argon.INSTANCE.getModuleManager().getModule(NoBreakDelay.class).isEnabled() ? 0 : cooldown;
	}
}
