package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;
import org.lwjgl.glfw.GLFW;

public final class NoJumpDelay extends Module implements TickListener {
	public NoJumpDelay() {
		super(EncryptedString.of("No Jump Delay"),
				EncryptedString.of("Lets you jump faster, removing the delay"),
				-1,
				Category.MISC);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.gui.screen() != null)
			return;

		if (!mc.player.onGround())
			return;

		if (GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_SPACE) != GLFW.GLFW_PRESS)
			return;

		mc.options.keyJump.setDown(false);
		mc.player.jumpFromGround();
	}
}
