package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.PacketSendListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CrystalOptimizer extends Module implements PacketSendListener {
	public CrystalOptimizer() {
		super(EncryptedString.of("Crystal Optimizer"),
				EncryptedString.of("Makes your crystals disappear faster client-side so you can place crystals faster"),
				-1,
				Category.COMBAT);
	}

	@Override
	public void onEnable() {
		eventManager.add(PacketSendListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketSendListener.class, this);
		super.onDisable();
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (!(event.packet instanceof ServerboundInteractPacket interactPacket)
				|| interactPacket.hand() != null || interactPacket.location() != null || mc.hitResult == null)
			return;

		if (mc.hitResult.getType() == HitResult.Type.ENTITY && mc.hitResult instanceof EntityHitResult hit
				&& hit.getEntity() instanceof EndCrystal) {
			MobEffectInstance weakness = mc.player.getEffect(MobEffects.WEAKNESS);
			MobEffectInstance strength = mc.player.getEffect(MobEffects.STRENGTH);
			if (!(weakness == null || strength != null && strength.getAmplifier() > weakness.getAmplifier()
					|| WorldUtils.isTool(mc.player.getMainHandItem())))
				return;

			hit.getEntity().discard();
			hit.getEntity().setRemoved(Entity.RemovalReason.KILLED);
			hit.getEntity().onClientRemoval();
		}
	}
}
