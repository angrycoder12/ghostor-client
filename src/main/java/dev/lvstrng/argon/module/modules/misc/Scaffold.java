package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.imixin.IKeyBinding;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.PlacementUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.awt.Color;

public final class Scaffold extends Module implements TickListener, HudListener, GameRenderListener {
	private final BooleanSetting shift = new BooleanSetting(EncryptedString.of("Shift"), false);
	private final NumberSetting rotationSpeed = new NumberSetting(
			EncryptedString.of("Rotation speed"), 0, 300, 80, 1);
	private final BooleanSetting render = new BooleanSetting(EncryptedString.of("Render"), true);
	private final NumberSetting renderRed = new NumberSetting(
			EncryptedString.of("RenderColor-R"), 0, 255, 255, 1);
	private final NumberSetting renderGreen = new NumberSetting(
			EncryptedString.of("RenderColor-G"), 0, 255, 0, 1);
	private final NumberSetting renderBlue = new NumberSetting(
			EncryptedString.of("RenderColor-B"), 0, 255, 0, 1);
	private final NumberSetting renderAlpha = new NumberSetting(
			EncryptedString.of("RenderAlpha"), 0, 255, 100, 1);

	private float yaw;
	private float pitch;
	private int blockCount;
	private BlockPos targetBlock;
	private boolean forcingSneak;

	public Scaffold() {
		super(EncryptedString.of("Scaffold"),
				EncryptedString.of("Automatically places blocks while moving"),
				-1,
				Category.MISC);
		addSettings(shift, rotationSpeed, render, renderRed, renderGreen, renderBlue, renderAlpha);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(HudListener.class, this);
		eventManager.add(GameRenderListener.class, this);
		if (mc.player != null) {
			yaw = mc.player.getYRot();
			pitch = mc.player.getXRot();
		}
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(HudListener.class, this);
		eventManager.remove(GameRenderListener.class, this);
		releaseSneak();
		targetBlock = null;
		blockCount = 0;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) {
			releaseSneak();
			targetBlock = null;
			return;
		}

		blockCount = PlacementUtils.countHotbarBlocks();
		BlockPos under = BlockPos.containing(mc.player.getX(), mc.player.getY() - 1.0D, mc.player.getZ());
		PlacementUtils.PlacementTarget target = PlacementUtils.findSupport(under);
		targetBlock = target == null ? null : under;

		if (target != null) {
			float[] desired = PlacementUtils.rotations(target);
			float step = rotationSpeed.getValueFloat() <= 0.0F
					? 360.0F : rotationSpeed.getValueFloat() / 20.0F;
			yaw = Mth.approachDegrees(yaw, desired[0], step);
			pitch = Mth.approachDegrees(pitch, desired[1], step);

			int slot = PlacementUtils.findBlockSlot();
			if (slot >= 0) {
				PlacementUtils.place(target, slot, yaw, pitch);
			}
		} else {
			yaw = mc.player.getYRot();
			pitch = mc.player.getXRot();
		}

		setSneak(shift.getValue() && mc.level.getBlockState(under).isAir());
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.player == null || mc.level == null || mc.gui.screen() != null) {
			return;
		}
		String text = blockCount + " blocks";
		int x = event.context.guiWidth() / 2 + 8 - mc.font.width(text) / 2;
		int y = event.context.guiHeight() / 2 - 4;
		event.context.text(mc.font, text, x, y, blockCount <= 16 ? 0xFFFF0000 : 0xFFFFFFFF, false);
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (!render.getValue() || targetBlock == null || mc.player == null || mc.level == null) {
			return;
		}

		Color fill = new Color(renderRed.getValueInt(), renderGreen.getValueInt(),
				renderBlue.getValueInt(), renderAlpha.getValueInt());
		Color outline = new Color(renderRed.getValueInt(), renderGreen.getValueInt(),
				renderBlue.getValueInt(), 255);
		AABB box = new AABB(targetBlock);
		RenderUtils.renderFilledBox(box, fill);
		RenderUtils.renderBoxOutline(box, outline, 2.0F);
	}

	private void setSneak(boolean shouldSneak) {
		forcingSneak = shouldSneak;
		boolean physical = ((IKeyBinding) mc.options.keyShift).isActuallyPressed();
		mc.options.keyShift.setDown(shouldSneak || physical);
	}

	private void releaseSneak() {
		if (forcingSneak) {
			((IKeyBinding) mc.options.keyShift).resetPressed();
			forcingSneak = false;
		}
	}
}
