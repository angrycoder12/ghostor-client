package dev.lvstrng.argon.module.modules.combat.velocity;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.PacketReceiveListener.PacketReceiveEvent;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.module.setting.Setting;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class StrafeVelocityMode implements VelocityModeHandler {
	private final NumberSetting delay = new NumberSetting("Strafe Delay", 0, 20, 0, 1);
	private final NumberSetting strength = new NumberSetting("Strafe Strength %", 0, 100, 100, 1);
	private final BooleanSetting untilGround = new BooleanSetting("Strafe Until Ground", false);
	private final List<Setting<?>> settings;
	private int ticks = -1;
	private boolean active;
	public StrafeVelocityMode(BooleanSupplier visible) {
		settings = List.of(delay.visibleWhen(visible), strength.visibleWhen(visible), untilGround.visibleWhen(visible));
	}
	@Override public List<Setting<?>> settings() { return settings; }
	@Override public void onPacket(PacketReceiveEvent event) { if (VelocityPacketUtil.isLocalVelocity(event.packet)) ticks = 0; }
	@Override public void onTick() {
		if (ticks >= 0 && ticks++ >= delay.getValueInt()) { ticks = -1; active = true; }
		if (!active) return;
		if (untilGround.getValue() && Argon.mc.player.onGround()) { active = false; return; }
		Vec2 input = Argon.mc.player.input.getMoveVector();
		if (input.lengthSquared() < 1.0E-4F) { if (!untilGround.getValue()) active = false; return; }
		Vec3 motion = Argon.mc.player.getDeltaMovement();
		double speed = Math.hypot(motion.x, motion.z) * strength.getValue() / 100.0D;
		double yaw = Math.toRadians(Argon.mc.player.getYRot());
		double x = -Math.sin(yaw) * input.y + Math.cos(yaw) * input.x;
		double z = Math.cos(yaw) * input.y + Math.sin(yaw) * input.x;
		double length = Math.hypot(x, z);
		if (length > 1.0E-5D) Argon.mc.player.setDeltaMovement(x / length * speed, motion.y, z / length * speed);
		if (!untilGround.getValue()) active = false;
	}
	@Override public void reset(boolean flush) { ticks = -1; active = false; }
}
