package dev.lvstrng.argon.event.events;

import dev.lvstrng.argon.event.CancellableEvent;
import dev.lvstrng.argon.event.Listener;
import java.util.ArrayList;
import net.minecraft.network.protocol.Packet;


public interface PacketReceiveListener extends Listener {
	void onPacketReceive(PacketReceiveEvent event);

	class PacketReceiveEvent extends CancellableEvent<PacketReceiveListener> {
		public Packet packet;

		public PacketReceiveEvent(Packet packet) {
			this.packet = packet;
		}

		@Override
		public void fire(ArrayList<PacketReceiveListener> listeners) {
			listeners.forEach(e -> e.onPacketReceive(this));
		}

		@Override
		public Class<PacketReceiveListener> getListenerType() {
			return PacketReceiveListener.class;
		}
	}
}
