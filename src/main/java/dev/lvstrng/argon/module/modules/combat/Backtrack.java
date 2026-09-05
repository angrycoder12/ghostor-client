package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.EntityAttackListener;
import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.event.events.PacketReceiveListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ColorSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.RenderUtils;
import java.awt.Color;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Target-specific inbound movement delay with a separately tracked latest position. */
public final class Backtrack extends Module implements TickListener, PacketReceiveListener,
		EntityAttackListener, GameRenderListener {
	private static final long COMBAT_CONTEXT_MILLIS = 1_500L;
	private static Backtrack instance;

	private final NumberSetting delay = new NumberSetting(
			EncryptedString.of("Delay (ms)"), 0, 500, 150, 5);
	private final NumberSetting range = new NumberSetting(
			EncryptedString.of("Range"), 1, 8, 4.5, 0.1);
	private final BooleanSetting onlyCombat = new BooleanSetting(
			EncryptedString.of("Only Combat"), true);
	private final BooleanSetting renderRealPosition = new BooleanSetting(
			EncryptedString.of("Render Real Position"), true);
	private final ColorSetting realPositionColor = new ColorSetting(
			EncryptedString.of("Real Position Color"), new Color(139, 99, 255, 210));
	private final NumberSetting maxQueuedPackets = new NumberSetting(
			EncryptedString.of("Max Queued Packets"), 16, 2048, 256, 16);

	private final ConcurrentLinkedQueue<QueuedPacket> packetQueue = new ConcurrentLinkedQueue<>();
	private final AtomicInteger queuedCount = new AtomicInteger();
	private final Object realPositionLock = new Object();
	private volatile Entity target;
	private volatile int targetId = -1;
	private int lastAttackedEntityId = -1;
	private long lastAttackTime;
	private Object trackedLevel;
	private volatile boolean forceFlush;
	private volatile Vec3 realPosition;
	private volatile AABB realBox;
	private VecDeltaCodec realPositionCodec;

	public Backtrack() {
		super(EncryptedString.of("Backtrack"),
				EncryptedString.of("Delays target movement updates to interact with previous positions"),
				-1,
				Category.COMBAT);
		addSettings(delay, range, onlyCombat, renderRealPosition, realPositionColor, maxQueuedPackets);
		instance = this;
	}

	@Override
	public void onEnable() {
		clearState(false);
		trackedLevel = mc.level;
		eventManager.add(PacketReceiveListener.class, this, 1000);
		eventManager.add(EntityAttackListener.class, this, 1000);
		eventManager.add(TickListener.class, this);
		eventManager.add(GameRenderListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketReceiveListener.class, this);
		eventManager.remove(EntityAttackListener.class, this);
		eventManager.remove(TickListener.class, this);
		eventManager.remove(GameRenderListener.class, this);
		clearState(true);
		trackedLevel = null;
		super.onDisable();
	}

	@Override
	public void onEntityAttack(EntityAttackEvent event) {
		if (event.target instanceof LivingEntity living && living != mc.player && living.isAlive()) {
			lastAttackedEntityId = living.getId();
			lastAttackTime = nowMillis();
		}
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		int id = targetId;
		if (event.isCancelled() || delay.getValueLong() <= 0L || id < 0
				|| !isTargetMovementPacket(event.packet, id)) return;

		updateRealPosition(event.packet);
		packetQueue.offer(new QueuedPacket(event.packet, nowMillis()));
		int count = queuedCount.incrementAndGet();
		if (count >= maxQueuedPackets.getValueInt()) forceFlush = true;
		event.cancel();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.getConnection() == null) {
			clearState(false);
			trackedLevel = mc.level;
			return;
		}
		if (trackedLevel != mc.level) {
			clearState(false);
			trackedLevel = mc.level;
			return;
		}

		updateTarget();
		if (forceFlush) {
			flushPackets(false);
			forceFlush = false;
		} else {
			flushPackets(true);
		}
		refreshRealBox();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		AABB box = realBox;
		if (!renderRealPosition.getValue() || !isTracking() || box == null) return;
		RenderUtils.renderBoxOutline(box.inflate(0.025), realPositionColor.getColor(), 1.5F);
	}

	public Entity getTarget() {
		return target;
	}

	public boolean isTracking() {
		return isEnabled() && target != null && targetId >= 0;
	}

	public static boolean isTracking(Entity entity) {
		Backtrack module = instance;
		return module != null && module.isTracking() && entity != null && entity.getId() == module.targetId;
	}

	/** Position used by combat: the intentionally delayed client-side entity position. */
	public static Vec3 getHittablePosition(Entity entity, float tickDelta) {
		if (entity == null) return null;
		return isTracking(entity) ? entity.position() : entity.getPosition(tickDelta);
	}

	/** Box used by combat: the intentionally delayed client-side collision box. */
	public static AABB getHittableBox(Entity entity, float tickDelta) {
		if (entity == null) return null;
		if (isTracking(entity)) return entity.getBoundingBox();
		Vec3 rendered = entity.getPosition(tickDelta);
		return entity.getBoundingBox().move(rendered.subtract(entity.position()));
	}

	/** Latest server-derived position, used by PlayerESP instead of the delayed model. */
	public static Vec3 getRealPosition(Entity entity, float tickDelta) {
		if (entity == null) return null;
		Backtrack module = instance;
		Vec3 position = module != null && isTracking(entity) ? module.realPosition : null;
		return position != null ? position : entity.getPosition(tickDelta);
	}

	/** Latest server-derived box, kept separate from the delayed interactive box. */
	public static AABB getRealBox(Entity entity, float tickDelta) {
		if (entity == null) return null;
		Backtrack module = instance;
		AABB box = module != null && isTracking(entity) ? module.realBox : null;
		if (box != null) return box;
		Vec3 rendered = entity.getPosition(tickDelta);
		return entity.getBoundingBox().move(rendered.subtract(entity.position()));
	}

	public static void onWorldChanged() {
		Backtrack module = instance;
		if (module != null) {
			module.clearState(false);
			module.trackedLevel = null;
		}
	}

	private void updateTarget() {
		Entity next = null;
		long now = nowMillis();
		if (now - lastAttackTime <= COMBAT_CONTEXT_MILLIS) {
			Entity attacked = lastAttackedEntityId < 0 ? null : mc.level.getEntity(lastAttackedEntityId);
			if (isValidTarget(attacked)) next = attacked;
		}

		if (next == null && !onlyCombat.getValue()) {
			double nearest = range.getValue() * range.getValue();
			for (Player player : mc.level.players()) {
				if (!isValidTarget(player)) continue;
				double distance = mc.player.distanceToSqr(player);
				if (distance < nearest) {
					nearest = distance;
					next = player;
				}
			}
		}

		if (next == target) return;
		if (targetId >= 0) flushPackets(false);
		target = next;
		targetId = next == null ? -1 : next.getId();
		initializeRealPosition(next);
	}

	private boolean isValidTarget(Entity entity) {
		if (entity == null || entity == mc.player || !(entity instanceof LivingEntity living) || !living.isAlive()) {
			return false;
		}
		if (entity instanceof Player player && Argon.INSTANCE.getFriendManager().isFriend(player)) return false;
		Vec3 comparison = entity == target && realPosition != null ? realPosition : entity.position();
		return mc.player.position().distanceToSqr(comparison) <= range.getValue() * range.getValue();
	}

	private void initializeRealPosition(Entity entity) {
		synchronized (realPositionLock) {
			if (entity == null) {
				realPosition = null;
				realBox = null;
				realPositionCodec = null;
				return;
			}
			realPosition = entity.position();
			realBox = entity.getBoundingBox();
			realPositionCodec = new VecDeltaCodec();
			realPositionCodec.setBase(entity.getPositionCodec().getBase());
		}
	}

	private void updateRealPosition(Packet<?> packet) {
		Entity tracked = target;
		if (tracked == null) return;
		synchronized (realPositionLock) {
			if (realPosition == null || realPositionCodec == null) {
				realPosition = tracked.position();
				realBox = tracked.getBoundingBox();
				realPositionCodec = new VecDeltaCodec();
				realPositionCodec.setBase(tracked.getPositionCodec().getBase());
			}
			if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()) {
				realPosition = realPositionCodec.decode(move.getXa(), move.getYa(), move.getZa());
				realPositionCodec.setBase(realPosition);
			} else if (packet instanceof ClientboundEntityPositionSyncPacket sync) {
				realPosition = sync.values().position();
				realPositionCodec.setBase(realPosition);
			} else if (packet instanceof ClientboundTeleportEntityPacket teleport) {
				Vec3 change = teleport.change().position();
				Set<Relative> relative = teleport.relatives();
				realPosition = new Vec3(
						relative.contains(Relative.X) ? realPosition.x + change.x : change.x,
						relative.contains(Relative.Y) ? realPosition.y + change.y : change.y,
						relative.contains(Relative.Z) ? realPosition.z + change.z : change.z);
				realPositionCodec.setBase(realPosition);
			}
			refreshRealBoxLocked(tracked);
		}
	}

	private void refreshRealBox() {
		Entity tracked = target;
		if (tracked == null) return;
		synchronized (realPositionLock) {
			refreshRealBoxLocked(tracked);
		}
	}

	private void refreshRealBoxLocked(Entity tracked) {
		if (realPosition == null) {
			realBox = null;
			return;
		}
		realBox = tracked.getBoundingBox().move(realPosition.subtract(tracked.position()));
	}

	private void clearState(boolean flush) {
		if (flush) flushPackets(false);
		else clearQueue();
		target = null;
		targetId = -1;
		lastAttackedEntityId = -1;
		lastAttackTime = 0;
		forceFlush = false;
		initializeRealPosition(null);
	}

	private void clearQueue() {
		packetQueue.clear();
		queuedCount.set(0);
	}

	private void flushPackets(boolean expiredOnly) {
		if (mc.getConnection() == null) {
			clearQueue();
			return;
		}

		long now = nowMillis();
		while (true) {
			QueuedPacket queued = packetQueue.peek();
			if (queued == null) return;
			if (expiredOnly && queued.receivedAt + delay.getValueLong() > now) return;
			packetQueue.poll();
			queuedCount.updateAndGet(value -> Math.max(0, value - 1));
			handlePacket(queued.packet);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void handlePacket(Packet<?> packet) {
		try {
			((Packet) packet).handle((PacketListener) mc.getConnection());
		} catch (RuntimeException ignored) {
			// A despawned target can invalidate an old entity update during cleanup.
		}
	}

	private static boolean isTargetMovementPacket(Packet<?> packet, int id) {
		if (packet instanceof ClientboundMoveEntityPacket move) {
			Entity entity = Argon.mc == null || Argon.mc.level == null ? null : move.getEntity(Argon.mc.level);
			return entity != null && entity.getId() == id;
		}
		if (packet instanceof ClientboundTeleportEntityPacket teleport) return teleport.id() == id;
		if (packet instanceof ClientboundEntityPositionSyncPacket sync) return sync.id() == id;
		return packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == id;
	}

	private static long nowMillis() {
		return System.nanoTime() / 1_000_000L;
	}

	private record QueuedPacket(Packet<?> packet, long receivedAt) {}
}
