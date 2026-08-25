package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.PacketReceiveListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

public class PackSpoof extends Module implements PacketReceiveListener {
    public PackSpoof() {
        super(EncryptedString.of("Pack Spoof"), EncryptedString.of("Ignores custom resource packs"), -1, Category.MISC);
    }

    @Override
    public void onEnable() {
        eventManager.add(PacketReceiveListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PacketReceiveListener.class, this);
        super.onDisable();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if(mc.getConnection() != null) {
            Packet<?> packet = event.packet;
            if (packet instanceof ClientboundResourcePackPushPacket) {
                event.cancel();

                mc.getConnection().send(new ServerboundResourcePackPacket(mc.player.getUUID(), ServerboundResourcePackPacket.Action.ACCEPTED));
                mc.getConnection().send(new ServerboundResourcePackPacket(mc.player.getUUID(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            }
        }
    }
}
