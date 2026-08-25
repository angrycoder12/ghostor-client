package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.event.EventManager;
import dev.lvstrng.argon.event.events.PacketReceiveListener;
import dev.lvstrng.argon.event.events.PacketSendListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Connection.class)
public class ClientConnectionMixin {

	@Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true)
	private static <T extends PacketListener> void onPacketReceive(Packet<T> packet, PacketListener listener, CallbackInfo ci) {
		PacketReceiveListener.PacketReceiveEvent event = new PacketReceiveListener.PacketReceiveEvent(packet);

		EventManager.fire(event);
		if (event.isCancelled()) ci.cancel();
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void onPacketSend(Packet<?> packet, CallbackInfo ci) {
		PacketSendListener.PacketSendEvent event = new PacketSendListener.PacketSendEvent(packet);

		EventManager.fire(event);
		if (event.isCancelled()) ci.cancel();
	}
}
