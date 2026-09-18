package dev.lvstrng.argon.module.modules.combat.velocity;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.PacketReceiveListener.PacketReceiveEvent;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.module.setting.Setting;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.world.phys.Vec3;

public final class ReversalVelocityMode implements VelocityModeHandler {
	private final NumberSetting delay = new NumberSetting("Reversal Delay", 0, 20, 0, 1);
	private final NumberSetting strength = new NumberSetting("Reversal Strength %", 0, 100, 100, 1);
	private final BooleanSetting onlyMoving = new BooleanSetting("Reversal Only Moving", true);
	private final List<Setting<?>> settings;
	private int ticks = -1;
	public ReversalVelocityMode(BooleanSupplier visible) {
		settings = List.of(delay.visibleWhen(visible), strength.visibleWhen(visible), onlyMoving.visibleWhen(visible));
	}
	@Override public List<Setting<?>> settings() { return settings; }
	@Override public void onPacket(PacketReceiveEvent event) { if (VelocityPacketUtil.isLocalVelocity(event.packet)) ticks = 0; }
	@Override public void onTick() {
		if (ticks < 0 || ticks++ < delay.getValueInt()) return;
		ticks = -1;
		if (onlyMoving.getValue() && Argon.mc.player.input.getMoveVector().lengthSquared() < 1.0E-4F) return;
		Vec3 motion = Argon.mc.player.getDeltaMovement();
		double multiplier = -strength.getValue() / 100.0D;
		Argon.mc.player.setDeltaMovement(motion.x * multiplier, motion.y, motion.z * multiplier);
	}
	@Override public void reset(boolean flush) { ticks = -1; }
}
