package dev.lvstrng.argon.module.modules.combat.velocity;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.PacketReceiveListener.PacketReceiveEvent;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.module.setting.Setting;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import net.minecraft.world.phys.Vec3;

public final class JumpResetVelocityMode implements VelocityModeHandler {
	private final NumberSetting chance = new NumberSetting("Jump Reset Chance %", 0, 100, 100, 1);
	private final NumberSetting delay = new NumberSetting("Jump Reset Delay", 0, 10, 0, 1);
	private final List<Setting<?>> settings;
	private int age = -1;
	private boolean fallDamage;
	public JumpResetVelocityMode(BooleanSupplier visible) {
		settings = List.of(chance.visibleWhen(visible), delay.visibleWhen(visible));
	}
	@Override public List<Setting<?>> settings() { return settings; }
	@Override public void onPacket(PacketReceiveEvent event) {
		if (!VelocityPacketUtil.isLocalVelocity(event.packet)) return;
		Vec3 motion = VelocityPacketUtil.movement(event.packet);
		fallDamage = motion != null && motion.x == 0.0D && motion.z == 0.0D && motion.y < 0.0D;
		age = 0;
	}
	@Override public void onTick() {
		if (age < 0) return;
		age++;
		if (age < delay.getValueInt()) return;
		if (age > 20 || Argon.mc.player.hurtTime == 0) { if (age > 5) age = -1; return; }
		if (fallDamage || !Argon.mc.player.onGround() || !Argon.mc.player.isSprinting()) return;
		if (Argon.mc.player.hurtTime >= 8 && (chance.getValue() >= 100.0D
				|| ThreadLocalRandom.current().nextDouble(100.0D) < chance.getValue())) {
			Argon.mc.player.jumpFromGround();
			age = -1;
		}
	}
	@Override public void reset(boolean flush) { age = -1; fallDamage = false; }
}
