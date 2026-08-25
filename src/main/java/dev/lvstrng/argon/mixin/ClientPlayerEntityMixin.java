package dev.lvstrng.argon.mixin;

import com.mojang.authlib.GameProfile;
import dev.lvstrng.argon.event.EventManager;
import dev.lvstrng.argon.event.events.MovementPacketListener;
import dev.lvstrng.argon.event.events.PlayerTickListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin extends AbstractClientPlayer {

	@Shadow
	@Final
	protected Minecraft minecraft;

	public ClientPlayerEntityMixin(ClientLevel world, GameProfile profile) {
		super(world, profile);
	}

	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void onSendMovementPackets(CallbackInfo ci) {
		EventManager.fire(new MovementPacketListener.MovementPacketEvent());
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void onPlayerTick(CallbackInfo ci) {
		EventManager.fire(new PlayerTickListener.PlayerTickEvent());
	}
	//@Inject(method = "sendMovementPackets", at = @At("HEAD"))
}
