package dev.lvstrng.argon.event.events;

import dev.lvstrng.argon.event.Event;
import dev.lvstrng.argon.event.Listener;
import java.util.ArrayList;
import net.minecraft.world.entity.Entity;

/** Fired after Minecraft has committed an attack against a concrete entity. */
public interface EntityAttackListener extends Listener {
	void onEntityAttack(EntityAttackEvent event);

	final class EntityAttackEvent extends Event<EntityAttackListener> {
		public final Entity target;

		public EntityAttackEvent(Entity target) {
			this.target = target;
		}

		@Override
		public void fire(ArrayList<EntityAttackListener> listeners) {
			listeners.forEach(listener -> listener.onEntityAttack(this));
		}

		@Override
		public Class<EntityAttackListener> getListenerType() {
			return EntityAttackListener.class;
		}
	}
}
