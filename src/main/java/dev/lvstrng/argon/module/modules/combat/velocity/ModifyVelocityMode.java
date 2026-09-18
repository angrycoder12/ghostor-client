package dev.lvstrng.argon.module.modules.combat.velocity;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.PacketReceiveListener.PacketReceiveEvent;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.module.setting.Setting;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;

public final class ModifyVelocityMode implements VelocityModeHandler {
	public enum Trigger { Always, OnGround, InAir }
	private final NumberSetting horizontal = new NumberSetting("Horizontal %", -100, 100, 0, 1);
	private final NumberSetting vertical = new NumberSetting("Vertical %", -100, 100, 0, 1);
	private final NumberSetting chance = new NumberSetting("Chance %", 0, 100, 100, 1);
	private final ModeSetting<Trigger> trigger = new ModeSetting<>("Trigger", Trigger.Always, Trigger.class);
	private final BooleanSetting onlyMoving = new BooleanSetting("Only Moving", false);
	private final NumberSetting preserveHorizontal = new NumberSetting("Preserve Horizontal %", 0, 100, 0, 1);
	private final NumberSetting preserveVertical = new NumberSetting("Preserve Vertical %", 0, 100, 0, 1);
	private final BooleanSetting explosions = new BooleanSetting("Explosions", true);
	private final List<Setting<?>> settings;

	public ModifyVelocityMode(BooleanSupplier visible) {
		settings = List.of(horizontal.visibleWhen(visible), vertical.visibleWhen(visible), chance.visibleWhen(visible),
				trigger.visibleWhen(visible), onlyMoving.visibleWhen(visible), preserveHorizontal.visibleWhen(visible),
				preserveVertical.visibleWhen(visible), explosions.visibleWhen(visible));
	}
	@Override public List<Setting<?>> settings() { return settings; }
	@Override public void onPacket(PacketReceiveEvent event) {
		if (!VelocityPacketUtil.isLocalVelocity(event.packet)
				&& !(event.packet instanceof ClientboundExplodePacket explosion
				&& explosions.getValue() && explosion.playerKnockback().isPresent())) return;
		if (trigger.isMode(Trigger.OnGround) && !Argon.mc.player.onGround()) return;
		if (trigger.isMode(Trigger.InAir) && Argon.mc.player.onGround()) return;
		if (onlyMoving.getValue() && Argon.mc.player.input.getMoveVector().lengthSquared() < 1.0E-4F) return;
		if (chance.getValue() <= 0.0D
				|| chance.getValue() < 100.0D && ThreadLocalRandom.current().nextDouble(100.0D) >= chance.getValue()) return;
		Vec3 incoming = VelocityPacketUtil.movement(event.packet);
		if (incoming == null) return;
		if (horizontal.getValue() == 0.0D && vertical.getValue() == 0.0D
				&& preserveHorizontal.getValue() == 0.0D && preserveVertical.getValue() == 0.0D) {
			event.cancel();
			return;
		}
		Vec3 current = Argon.mc.player.getDeltaMovement();
		double h = horizontal.getValue() / 100.0D;
		double v = vertical.getValue() / 100.0D;
		Vec3 changed = new Vec3(
				h == 0.0D ? current.x * preserveHorizontal.getValue() / 100.0D : incoming.x * h,
				v == 0.0D ? current.y * preserveVertical.getValue() / 100.0D : incoming.y * v,
				h == 0.0D ? current.z * preserveHorizontal.getValue() / 100.0D : incoming.z * h);
		event.cancel();
		var modified = VelocityPacketUtil.withMovement(event.packet, changed);
		Argon.mc.execute(() -> VelocityPacketUtil.handle(modified));
	}
	@Override public void reset(boolean flush) {}
}
