package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.event.events.PacketSendListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.TimerUtils;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class AutoWTap extends Module implements PacketSendListener, HudListener {
	private final MinMaxSetting delay = new MinMaxSetting(EncryptedString.of("Delay"), 0, 1000, 1,230, 270);
	private final BooleanSetting inAir = new BooleanSetting(EncryptedString.of("In Air"), false)
			.setDescription(EncryptedString.of("Whether it should W tap in air"));
	private final TimerUtils sprintTimer = new TimerUtils();
	private final TimerUtils tapTimer = new TimerUtils();
	private boolean holdingForward;
	private boolean sprinting;
	private int currentDelay;
	private boolean jumpedWhileHitting;

	public AutoWTap() {
		super(EncryptedString.of("Auto WTap"),
				EncryptedString.of("Automatically W Taps for you so the opponent takes more knockback"),
				-1,
				Category.COMBAT);
		addSettings(delay, inAir);
	}

	@Override
	public void onEnable() {
		eventManager.add(PacketSendListener.class, this);
		eventManager.add(HudListener.class, this);
		currentDelay = delay.getRandomValueInt();
		jumpedWhileHitting = false;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketSendListener.class, this);
		eventManager.remove(HudListener.class, this);
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_W) != 1) {
			sprinting = false;
			holdingForward = false;
			return;
		}

		if (!inAir.getValue() && !mc.player.onGround())
			return;

		if (mc.player.onGround()) {
			jumpedWhileHitting = false;
		}

		if (GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_SPACE) == 1 && !inAir.getValue()) {
			if (holdingForward || sprinting) {
				mc.options.keyUp.setDown(true);
				holdingForward = false;
				sprinting = false;
				return;
			}
		}

		if (holdingForward && tapTimer.delay(1)) {
			mc.options.keyUp.setDown(false);
			sprintTimer.reset();
			sprinting = true;
			holdingForward = false;
		}

		if (sprinting && sprintTimer.delay(currentDelay)) {
			mc.options.keyUp.setDown(true);
			sprinting = false;
			currentDelay = delay.getRandomValueInt();
		}
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (!(event.packet instanceof ServerboundInteractPacket packet))
			return;

		if (packet.hand() != null || packet.location() != null)
			return;

		if (GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_SPACE) == 1 && !inAir.getValue()) {
			jumpedWhileHitting = true;
		}

		if (!inAir.getValue() && !mc.player.onGround())
			return;

		if (!jumpedWhileHitting && mc.options.keyUp.isDown() && mc.player.isSprinting()) {
			sprintTimer.reset();
			holdingForward = true;
		}
	}
}
