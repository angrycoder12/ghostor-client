package dev.lvstrng.argon.module.modules.combat.velocity;

import dev.lvstrng.argon.event.events.PacketReceiveListener.PacketReceiveEvent;
import dev.lvstrng.argon.module.setting.Setting;
import java.util.List;

public interface VelocityModeHandler {
	List<Setting<?>> settings();
	default void onPacket(PacketReceiveEvent event) {}
	default void onTick() {}
	void reset(boolean flush);
}
