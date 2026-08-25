package dev.lvstrng.argon.event.events;

import dev.lvstrng.argon.event.Event;
import dev.lvstrng.argon.event.Listener;
import java.util.ArrayList;

public interface GameRenderListener extends Listener {
	void onGameRender(GameRenderEvent event);

	class GameRenderEvent extends Event<GameRenderListener> {
		public float delta;

		public GameRenderEvent(float delta) {
			this.delta = delta;
		}

		@Override
		public void fire(ArrayList<GameRenderListener> listeners) {
			listeners.forEach(e -> e.onGameRender(this));
		}

		@Override
		public Class<GameRenderListener> getListenerType() {
			return GameRenderListener.class;
		}
	}
}
