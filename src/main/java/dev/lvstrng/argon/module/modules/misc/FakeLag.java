package dev.lvstrng.argon.module.modules.misc;

import com.google.common.collect.Queues;
import dev.lvstrng.argon.event.events.PacketReceiveListener;
import dev.lvstrng.argon.event.events.PacketSendListener;
import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.TimerUtils;
import java.util.Queue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class FakeLag extends Module implements PlayerTickListener, PacketReceiveListener, PacketSendListener {
	public final Queue<Packet<?>> packetQueue = Queues.newConcurrentLinkedQueue();
	public boolean bool;
	public Vec3 pos = Vec3.ZERO;
	public TimerUtils timerUtil = new TimerUtils();
	private final MinMaxSetting lagDelay = new MinMaxSetting(EncryptedString.of("Lag Delay"), 0, 1000, 1, 100, 200);
	private final BooleanSetting cancelOnElytra = new BooleanSetting(EncryptedString.of("Cancel on Elytra"), false)
			.setDescription(EncryptedString.of("Cancel the lagging effect when you're wearing an elytra"));

	private int delay;
	public FakeLag() {
		super(EncryptedString.of("Fake Lag"),
				EncryptedString.of("Makes it impossible to aim at you by creating a lagging effect"),
				-1,
				Category.MISC);
		addSettings(lagDelay, cancelOnElytra);
	}

	@Override
	public void onEnable() {
		eventManager.add(PlayerTickListener.class, this);
		eventManager.add(PacketSendListener.class, this);
		eventManager.add(PacketReceiveListener.class, this);

		timerUtil.reset();
		if (mc.player != null)
			pos = mc.player.position();

		delay = lagDelay.getRandomValueInt();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PlayerTickListener.class, this);
		eventManager.remove(PacketSendListener.class, this);
		eventManager.remove(PacketReceiveListener.class, this);
		reset();
		super.onDisable();
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (mc.level == null)
			return;

		if(mc.player.isDeadOrDying())
			return;

		if (event.packet instanceof ClientboundExplodePacket) {
			reset();
		}
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (mc.level == null || mc.player.isUsingItem() || mc.player.isDeadOrDying())
			return;

		if (event.packet instanceof ServerboundInteractPacket || event.packet instanceof ServerboundSwingPacket || event.packet instanceof ServerboundUseItemOnPacket || event.packet instanceof ServerboundContainerClickPacket) {
			reset();
			return;
		}

		if (cancelOnElytra.getValue() && mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
			reset();
			return;
		}

		if (!bool) {
			packetQueue.add(event.packet);
			event.cancel();
		}
	}

	@Override
	public void onPlayerTick() {
		if (timerUtil.delay(delay)) {
			if (mc.player != null && !mc.player.isUsingItem()) {
				reset();
				delay = lagDelay.getRandomValueInt();
			}
		}
	}

	private void reset() {
		if (mc.player == null || mc.level == null)
			return;

		bool = true;

		synchronized (packetQueue) {
			while (!packetQueue.isEmpty()) {
				mc.getConnection().getConnection().send(packetQueue.poll(), null, false);
			}
		}

		bool = false;
		timerUtil.reset();
		pos = mc.player.position();
	}
}
