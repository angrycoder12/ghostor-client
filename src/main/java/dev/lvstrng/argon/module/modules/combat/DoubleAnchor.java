package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.BlockUtils;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

public final class DoubleAnchor extends Module implements TickListener {
	public DoubleAnchor() {
		super(EncryptedString.of("Double Anchor"),
				EncryptedString.of("Helps you do the air place/double anchor"),
				-1,
				Category.COMBAT);
	}

	private BlockPos pos;
	private int count;

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		pos = null;
		count = 0;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.gui.screen() == null) {
			assert mc.player != null;
			if (mc.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) {
				assert mc.level != null;
				if (mc.hitResult instanceof BlockHitResult h && BlockUtils.isAnchorCharged(h.getBlockPos())) {
					if (GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) {
						if (h.getBlockPos().equals(pos)) {
							if (count >= 1) return;
						} else {
							pos = h.getBlockPos();
							count = 0;
						}

						mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, h, 0));
						count++;
					}
				}
			}
		}
	}
}
