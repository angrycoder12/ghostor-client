package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.EventManager;
import dev.lvstrng.argon.event.events.EntityAttackListener;
import dev.lvstrng.argon.module.modules.misc.NoBreakDelay;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

	@Inject(method = "attack", at = @At("RETURN"))
	private void ghostor$afterEntityAttack(Player player, Entity target, CallbackInfo ci) {
		EventManager.fire(new EntityAttackListener.EntityAttackEvent(target));
	}
}
