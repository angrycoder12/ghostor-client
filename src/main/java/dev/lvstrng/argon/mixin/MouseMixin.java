package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.EventManager;
import dev.lvstrng.argon.event.events.ButtonListener;
import dev.lvstrng.argon.event.events.MouseMoveListener;
import dev.lvstrng.argon.event.events.MouseUpdateListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow public abstract double xpos();
	@Shadow public abstract double ypos();

	@Unique private double argon$lastMouseX;
	@Unique private double argon$lastMouseY;
	@Unique private boolean argon$initialized;
	@Unique private final int[] argon$buttonStates = new int[GLFW.GLFW_MOUSE_BUTTON_LAST + 1];

	@Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
	private void onMouseUpdate(CallbackInfo ci) {
		EventManager.fire(new MouseUpdateListener.MouseUpdateEvent());
		long window = minecraft.getWindow().handle();
		double x = xpos();
		double y = ypos();

		if (!argon$initialized) {
			argon$initialized = true;
			argon$lastMouseX = x;
			argon$lastMouseY = y;

			for (int button = 0; button < argon$buttonStates.length; button++) {
				argon$buttonStates[button] = GLFW.glfwGetMouseButton(window, button);
			}
			return;
		}

		if (x != argon$lastMouseX || y != argon$lastMouseY) {
			argon$lastMouseX = x;
			argon$lastMouseY = y;
			EventManager.fire(new MouseMoveListener.MouseMoveEvent(window, x, y));
		}

		for (int button = 0; button < argon$buttonStates.length; button++) {
			int state = GLFW.glfwGetMouseButton(window, button);
			if (state != argon$buttonStates[button]) {
				argon$buttonStates[button] = state;
				EventManager.fire(new ButtonListener.ButtonEvent(button, window, state));
			}
		}
	}
}
