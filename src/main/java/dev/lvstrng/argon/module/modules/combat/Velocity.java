package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.EntityAttackListener;
import dev.lvstrng.argon.event.events.PacketReceiveListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Applies each selected knockback event exactly once after a short lag window. */
public final class Velocity extends Module implements PacketReceiveListener, EntityAttackListener, TickListener {
	private static final int MAX_PENDING_EVENTS = 128;
	private static Velocity instance;

	private final NumberSetting horizontal = new NumberSetting(
			EncryptedString.of("Horizontal %"), 0, 100, 85, 1);
	private final NumberSetting vertical = new NumberSetting(
			EncryptedString.of("Vertical %"), 0, 100, 100, 1);
	private final NumberSetting chance = new NumberSetting(
			EncryptedString.of("Chance %"), 0, 100, 100, 1);
	private final NumberSetting lagTime = new NumberSetting(
			EncryptedString.of("Lag Time (ms)"), 0, 500, 75, 5);
	private final BooleanSetting explosions = new BooleanSetting(
			EncryptedString.of("Explosions"), true);
	private final BooleanSetting onlyPlayers = new BooleanSetting(
			EncryptedString.of("Players only"), false);
	private final BooleanSetting onlyWhileTargeting = new BooleanSetting(
			EncryptedString.of("Targeting"), false);
	private final BooleanSetting onlySprinting = new BooleanSetting(
			EncryptedString.of("Sprint only"), false);
	private final BooleanSetting groundOnly = new BooleanSetting(
			EncryptedString.of("Ground only"), false);

	private final ConcurrentLinkedQueue<PendingVelocity> pending = new ConcurrentLinkedQueue<>();
	private final AtomicInteger pendingCount = new AtomicInteger();
	private Object trackedLevel;
	private int lastAttackedEntityId = -1;

	public Velocity() {
		super(EncryptedString.of("Velocity"),
				EncryptedString.of("Delays and scales incoming knockback"),
				-1,
				Category.COMBAT);
		addSettings(horizontal, vertical, chance, lagTime, explosions,
				onlyPlayers, onlyWhileTargeting, onlySprinting, groundOnly);
		instance = this;
	}

	@Override
	public void onEnable() {
		clearPending(false);
		trackedLevel = mc.level;
		eventManager.add(PacketReceiveListener.class, this, 900);
		eventManager.add(EntityAttackListener.class, this);
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketReceiveListener.class, this);
		eventManager.remove(EntityAttackListener.class, this);
		eventManager.remove(TickListener.class, this);
		clearPending(true);
		trackedLevel = null;
		lastAttackedEntityId = -1;
		super.onDisable();
	}

	@Override
	public void onEntityAttack(EntityAttackEvent event) {
		lastAttackedEntityId = event.target == null ? -1 : event.target.getId();
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (event.isCancelled() || mc.player == null || mc.level == null || !passesConditions()) return;
		if (pendingCount.get() >= MAX_PENDING_EVENTS || !passesChance()) return;

		Packet<?> modified;
		if (event.packet instanceof ClientboundSetEntityMotionPacket motion) {
			if (motion.id() != mc.player.getId()) return;
			Vec3 scaled = scale(motion.movement());
			modified = new ClientboundSetEntityMotionPacket(motion.id(), scaled);
		} else if (event.packet instanceof ClientboundExplodePacket explosion) {
			if (!explosions.getValue() || explosion.playerKnockback().isEmpty()) return;
			modified = new ClientboundExplodePacket(
					explosion.center(), explosion.radius(), explosion.blockCount(),
					explosion.playerKnockback().map(this::scale), explosion.explosionParticle(),
					explosion.explosionSound(), explosion.blockParticles());
		} else {
			return;
		}

		long configuredLag = lagTime.getValueLong();
		if (configuredLag <= 0L) {
			event.cancel();
			mc.execute(() -> handlePacket(modified));
			return;
		}
		long releaseAt = System.nanoTime() + configuredLag * 1_000_000L;
		pending.offer(new PendingVelocity(event.packet, modified, releaseAt));
		pendingCount.incrementAndGet();
		event.cancel();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.getConnection() == null || !mc.player.isAlive()) {
			clearPending(false);
			trackedLevel = mc.level;
			return;
		}
		if (trackedLevel != mc.level) {
			clearPending(false);
			trackedLevel = mc.level;
			return;
		}

		long now = System.nanoTime();
		while (true) {
			PendingVelocity event = pending.peek();
			if (event == null || event.releaseAtNanos > now) return;
			pending.poll();
			pendingCount.updateAndGet(value -> Math.max(0, value - 1));
			handlePacket(event.modified);
		}
	}

	private boolean passesConditions() {
		if (onlyWhileTargeting.getValue() && !(mc.hitResult instanceof EntityHitResult)) return false;
		if (onlySprinting.getValue() && !mc.player.isSprinting()) return false;
		if (groundOnly.getValue() && !mc.player.onGround()) return false;
		if (onlyPlayers.getValue()) {
			Entity attacked = lastAttackedEntityId < 0 ? null : mc.level.getEntity(lastAttackedEntityId);
			if (!(attacked instanceof Player) && !(mc.player.getLastAttacker() instanceof Player)) return false;
		}
		return true;
	}

	private boolean passesChance() {
		return chance.getValue() >= 100.0
				|| chance.getValue() > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < chance.getValue();
	}

	private Vec3 scale(Vec3 movement) {
		double horizontalScale = horizontal.getValue() / 100.0;
		double verticalScale = vertical.getValue() / 100.0;
		return new Vec3(movement.x * horizontalScale, movement.y * verticalScale, movement.z * horizontalScale);
	}

	private void clearPending(boolean flushOriginal) {
		PendingVelocity event;
		while ((event = pending.poll()) != null) {
			if (flushOriginal && mc.getConnection() != null) handlePacket(event.original);
		}
		pendingCount.set(0);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void handlePacket(Packet<?> packet) {
		if (mc.getConnection() == null) return;
		try {
			((Packet) packet).handle((PacketListener) mc.getConnection());
		} catch (RuntimeException ignored) {
			// Connection/world replacement can invalidate a packet during cleanup.
		}
	}

	public static void onWorldChanged() {
		Velocity module = instance;
		if (module != null) {
			module.clearPending(false);
			module.trackedLevel = null;
			module.lastAttackedEntityId = -1;
		}
	}

	private record PendingVelocity(Packet<?> original, Packet<?> modified, long releaseAtNanos) {}
}
