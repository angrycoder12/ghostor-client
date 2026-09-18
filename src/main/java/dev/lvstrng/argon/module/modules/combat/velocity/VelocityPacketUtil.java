package dev.lvstrng.argon.module.modules.combat.velocity;

import dev.lvstrng.argon.Argon;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

final class VelocityPacketUtil {
	private VelocityPacketUtil() {}
	static boolean isLocalVelocity(Packet<?> packet) {
		return packet instanceof ClientboundSetEntityMotionPacket motion
				&& Argon.mc.player != null && motion.id() == Argon.mc.player.getId();
	}
	static Vec3 movement(Packet<?> packet) {
		if (packet instanceof ClientboundSetEntityMotionPacket motion) return motion.movement();
		if (packet instanceof ClientboundExplodePacket explosion) return explosion.playerKnockback().orElse(null);
		return null;
	}
	static Packet<?> withMovement(Packet<?> packet, Vec3 movement) {
		if (packet instanceof ClientboundSetEntityMotionPacket motion) {
			return new ClientboundSetEntityMotionPacket(motion.id(), movement);
		}
		if (packet instanceof ClientboundExplodePacket explosion) {
			return new ClientboundExplodePacket(explosion.center(), explosion.radius(), explosion.blockCount(),
					java.util.Optional.of(movement), explosion.explosionParticle(), explosion.explosionSound(),
					explosion.blockParticles());
		}
		return packet;
	}
	@SuppressWarnings({"rawtypes", "unchecked"})
	static void handle(Packet<?> packet) {
		if (Argon.mc.getConnection() == null) return;
		try {
			((Packet) packet).handle((PacketListener) Argon.mc.getConnection());
		} catch (RuntimeException ignored) {
			// World replacement can invalidate a delayed event during cleanup.
		}
	}
}
