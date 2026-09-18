package dev.lvstrng.argon.module.modules.combat.velocity;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.PacketReceiveListener.PacketReceiveEvent;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.module.setting.Setting;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;

public final class LagVelocityMode implements VelocityModeHandler {
	private static final int MAX_PACKETS = 512;
	private final NumberSetting delay = new NumberSetting("Lag Ticks", 1, 20, 5, 1);
	private final BooleanSetting explosions = new BooleanSetting("Lag Explosions", true);
	private final BooleanSetting jumpReset = new BooleanSetting("Lag Jump Reset", false);
	private final List<Setting<?>> settings;
	private final ConcurrentLinkedQueue<Packet<?>> packets = new ConcurrentLinkedQueue<>();
	private final AtomicInteger packetCount = new AtomicInteger();
	private int remainingTicks;
	private boolean lagging;

	public LagVelocityMode(BooleanSupplier visible) {
		settings = List.of(delay.visibleWhen(visible), explosions.visibleWhen(visible), jumpReset.visibleWhen(visible));
	}
	@Override public List<Setting<?>> settings() { return settings; }
	@Override public synchronized void onPacket(PacketReceiveEvent event) {
		boolean trigger = VelocityPacketUtil.isLocalVelocity(event.packet)
				|| event.packet instanceof ClientboundExplodePacket explosion
				&& explosions.getValue() && explosion.playerKnockback().isPresent();
		if (trigger && !lagging) { lagging = true; remainingTicks = delay.getValueInt(); }
		if (!lagging || event.packet instanceof ClientboundKeepAlivePacket) return;
		packets.add(event.packet);
		if (packetCount.incrementAndGet() >= MAX_PACKETS) Argon.mc.execute(this::flush);
		event.cancel();
	}
	@Override public synchronized void onTick() {
		if (!lagging || --remainingTicks > 0) return;
		flush();
		if (jumpReset.getValue() && Argon.mc.player.onGround() && Argon.mc.player.isSprinting()) {
			Argon.mc.player.jumpFromGround();
		}
	}
	private synchronized void flush() {
		Packet<?> packet;
		while ((packet = packets.poll()) != null) {
			packetCount.updateAndGet(value -> Math.max(0, value - 1));
			VelocityPacketUtil.handle(packet);
		}
		lagging = false;
		remainingTicks = 0;
	}
	@Override public synchronized void reset(boolean flush) {
		if (flush) Argon.mc.execute(this::flush);
		else { packets.clear(); packetCount.set(0); }
		lagging = false;
		remainingTicks = 0;
	}
}
