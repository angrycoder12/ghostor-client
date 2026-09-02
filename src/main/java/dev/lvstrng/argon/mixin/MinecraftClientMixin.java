package dev.lvstrng.argon.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.EventManager;
import dev.lvstrng.argon.event.events.*;
import dev.lvstrng.argon.utils.MouseSimulation;
import dev.lvstrng.argon.module.modules.combat.AutoGap;
import dev.lvstrng.argon.module.modules.misc.Freecam;
import dev.lvstrng.argon.module.modules.render.BlockESP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
	@Inject(method = "setLevel", at = @At("HEAD"))
	private void ghostor$clearFreecamWorldState(ClientLevel newLevel, CallbackInfo ci) {
		AutoGap.onWorldChanged();
		Freecam.onWorldChanged();
		BlockESP.onWorldChanged();
	}

	@Shadow
	@Nullable
	public ClientLevel level;

	@Shadow
	@Final
	private Window window;

	@Inject(method = "tick", at = @At("HEAD"))
	private void onTick(CallbackInfo ci) {
		if (level != null) {
			TickListener.TickEvent event = new TickListener.TickEvent();

			EventManager.fire(event);
		}
	}

	@Inject(method = "resizeGui", at = @At("HEAD"))
	private void onResolutionChanged(CallbackInfo ci) {
		EventManager.fire(new ResolutionListener.ResolutionEvent(this.window));
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void onItemUse(CallbackInfo ci) {
		ItemUseListener.ItemUseEvent event = new ItemUseListener.ItemUseEvent();

		EventManager.fire(event);
		if (event.isCancelled()) ci.cancel();

		if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
			MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
			ci.cancel();
		}
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void onAttack(CallbackInfoReturnable<Boolean> cir) {
		AttackListener.AttackEvent event = new AttackListener.AttackEvent();

		EventManager.fire(event);
		if (event.isCancelled()) cir.setReturnValue(false);

		if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
			MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_1, false);
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void onBlockBreaking(boolean breaking, CallbackInfo ci) {
		BlockBreakingListener.BlockBreakingEvent event = new BlockBreakingListener.BlockBreakingEvent();

		EventManager.fire(event);
		if (event.isCancelled()) ci.cancel();

		if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
			MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_1, false);
			ci.cancel();
		}
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void onClose(CallbackInfo ci) {
		BlockESP.onClientClosing();
		Argon.INSTANCE.getConfigManager().shutdown();
	}
}
