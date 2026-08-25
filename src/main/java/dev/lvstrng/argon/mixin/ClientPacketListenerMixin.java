package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.module.modules.misc.Freecam;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public final class ClientPacketListenerMixin {
	@Inject(method = "handleForgetLevelChunk", at = @At("HEAD"), cancellable = true)
	private void ghostor$retainFreecamChunk(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
		Freecam freecam = Freecam.enabledInstance();
		if (freecam != null && freecam.retainChunk(packet.pos())) {
			// Cancel the complete handler so the retained chunk also keeps its light data.
			ci.cancel();
		}
	}
}
