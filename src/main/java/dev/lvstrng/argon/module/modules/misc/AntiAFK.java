package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.imixin.IKeyBinding;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class AntiAFK extends Module implements TickListener {
	private static final long ACTION_HOLD_MILLIS = 500L;

	private final NumberSetting delay = new NumberSetting(
			EncryptedString.of("Delay (s)"), 5, 300, 30, 1);
	private final ModeSetting<Action> action = new ModeSetting<>(
			EncryptedString.of("Action"), Action.Jump, Action.class);
	private final BooleanSetting rotate = new BooleanSetting(
			EncryptedString.of("Rotate"), true);

	private KeyMapping heldKey;
	private long nextActionAt;
	private long releaseAt;

	public AntiAFK() {
		super(EncryptedString.of("Anti-AFK"),
				EncryptedString.of("Prevents AFK detection using automatic actions"),
				-1,
				Category.MISC);
		addSettings(delay, action, rotate);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		releaseAction();
		resetDelay();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		releaseAction();
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gui.screen() != null) {
			releaseAction();
			resetDelay();
			return;
		}

		long now = System.currentTimeMillis();
		if (isPlayerActive()) {
			releaseAction();
			resetDelay();
			return;
		}

		if (heldKey != null) {
			if (now >= releaseAt) {
				releaseAction();
				resetDelay();
			}
			return;
		}

		if (now < nextActionAt) {
			return;
		}

		heldKey = switch (action.getMode()) {
			case Jump -> mc.options.keyJump;
			case Forward -> mc.options.keyUp;
			case Backward -> mc.options.keyDown;
			case Strafe -> mc.player.tickCount % 2 == 0 ? mc.options.keyRight : mc.options.keyLeft;
		};
		heldKey.setDown(true);
		if (rotate.getValue()) {
			mc.player.setYRot(mc.player.getYRot() + (mc.player.tickCount % 2 == 0 ? 15.0F : -15.0F));
		}
		releaseAt = now + ACTION_HOLD_MILLIS;
	}

	private boolean isPlayerActive() {
		Vec3 movement = mc.player.getDeltaMovement();
		if (movement.x != 0.0D || movement.y != 0.0D || movement.z != 0.0D) {
			return true;
		}

		if (isPhysicallyDown(mc.options.keyUp) || isPhysicallyDown(mc.options.keyDown)
				|| isPhysicallyDown(mc.options.keyLeft) || isPhysicallyDown(mc.options.keyRight)
				|| isPhysicallyDown(mc.options.keyJump) || isPhysicallyDown(mc.options.keyShift)
				|| isPhysicallyDown(mc.options.keySprint)) {
			return true;
		}

		long window = mc.getWindow().handle();
		return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
	}

	private static boolean isPhysicallyDown(KeyMapping key) {
		return ((IKeyBinding) key).isActuallyPressed();
	}

	private void releaseAction() {
		if (heldKey != null) {
			((IKeyBinding) heldKey).resetPressed();
			heldKey = null;
		}
		releaseAt = 0L;
	}

	private void resetDelay() {
		nextActionAt = System.currentTimeMillis() + delay.getValueLong() * 1000L;
	}

	public enum Action {
		Jump,
		Forward,
		Backward,
		Strafe
	}
}
