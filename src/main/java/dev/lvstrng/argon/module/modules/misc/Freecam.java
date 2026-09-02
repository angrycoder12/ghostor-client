package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.CameraUpdateListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.imixin.IKeyBinding;
import dev.lvstrng.argon.mixin.KeyBindingAccessor;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;


public final class Freecam extends Module implements TickListener, CameraUpdateListener {
	private final NumberSetting speed = new NumberSetting(EncryptedString.of("Speed"), 1, 10, 1, 1);
	public Vec3 oldPos;
	public Vec3 pos;
	private final Set<ChunkPos> retainedChunks = new HashSet<>();
	private ClientLevel observedLevel;
	private boolean retainingChunks;

	public Freecam() {
		super(EncryptedString.of("Freecam"),
				EncryptedString.of("Lets you move freely around the world without actually moving"),
				-1,
				Category.MISC);
		addSettings(speed);

		oldPos = Vec3.ZERO;
		pos = Vec3.ZERO;
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(CameraUpdateListener.class, this);
		discardRetainedChunks();
		observedLevel = mc.level;
		retainingChunks = mc.level != null && mc.player != null;
		if (mc.level != null && mc.player != null) {
			this.oldPos = this.pos = mc.player.getEyePosition();
			mc.levelExtractor.allChanged();
		}

		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(CameraUpdateListener.class, this);
		retainingChunks = false;
		releaseRetainedChunks();
		observedLevel = null;
		restoreSuppressedInput();

		if (mc.level != null && mc.player != null) {
			mc.player.setDeltaMovement(Vec3.ZERO);
			mc.levelExtractor.allChanged();
		}
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			discardRetainedChunks();
			observedLevel = null;
			retainingChunks = false;
			return;
		}

		if (observedLevel != mc.level) {
			discardRetainedChunks();
			observedLevel = mc.level;
			oldPos = pos = mc.player.getEyePosition();
			retainingChunks = true;
			mc.levelExtractor.allChanged();
		}

		if (mc.gui.screen() != null)
			return;

		mc.options.keyUse.setDown(false);
		mc.options.keyAttack.setDown(false);
		mc.options.keyUp.setDown(false);
		mc.options.keyDown.setDown(false);
		mc.options.keyLeft.setDown(false);
		mc.options.keyRight.setDown(false);
		mc.options.keyJump.setDown(false);
		mc.options.keyShift.setDown(false);

		float f = (float) Math.PI / 180;
		float f2 = (float) Math.PI;
		Vec3 vec3d = new Vec3(-Mth.sin(-mc.player.getYRot() * f - f2), 0.0, -Mth.cos(-mc.player.getYRot() * f - f2));
		Vec3 vec3d2 = new Vec3(0.0, 1.0, 0.0);
		Vec3 vec3d3 = vec3d2.cross(vec3d);
		Vec3 vec3d4 = vec3d.cross(vec3d2);
		Vec3 vec3d5 = Vec3.ZERO;
		KeyMapping keyBinding = mc.options.keyUp;

		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.add(vec3d);
		}

		KeyMapping keyBinding2 = mc.options.keyDown;
		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding2).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.subtract(vec3d);
		}

		KeyMapping keyBinding3 = mc.options.keyLeft;
		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding3).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.add(vec3d3);
		}

		KeyMapping keyBinding4 = mc.options.keyRight;
		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding4).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.add(vec3d4);
		}

		KeyMapping keyBinding5 = mc.options.keyJump;
		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding5).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.add(0.0, speed.getValue(), 0.0);
		}

		KeyMapping keyBinding6 = mc.options.keyShift;
		if (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding6).getBoundKey().getValue()) == GLFW.GLFW_PRESS) {
			vec3d5 = vec3d5.add(0.0, -speed.getValue(), 0.0);
		}

		KeyMapping keyBinding7 = mc.options.keySprint;
		vec3d5 = vec3d5.normalize().scale(speed.getValue() * (GLFW.glfwGetKey(mc.getWindow().handle(), ((KeyBindingAccessor) keyBinding7).getBoundKey().getValue()) == GLFW.GLFW_PRESS ? 2 : 1));

		oldPos = pos;
		pos = pos.add(vec3d5);
	}

	@Override
	public void onCameraUpdate(CameraUpdateEvent event) {
		if (mc.player == null || mc.level == null) {
			return;
		}

		float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

		if (mc.gui.screen() != null)
			return;

		event.setX(Mth.lerp(tickDelta, oldPos.x, pos.x));
		event.setY(Mth.lerp(tickDelta, oldPos.y, pos.y));
		event.setZ(Mth.lerp(tickDelta, oldPos.z, pos.z));
	}

	/** Called from the packet hook before vanilla processes a server-requested chunk unload. */
	public boolean retainChunk(ChunkPos chunkPos) {
		if (!isEnabled() || !retainingChunks || mc.level == null || mc.level != observedLevel) {
			return false;
		}

		retainedChunks.add(chunkPos);
		return true;
	}

	/** Prevents a delayed unload from winning over a newer load for the same chunk. */
	public void acceptChunkLoad(ChunkPos chunkPos) {
		if (isEnabled() && retainingChunks && mc.level == observedLevel) {
			retainedChunks.remove(chunkPos);
		}
	}

	public static Freecam enabledInstance() {
		if (dev.lvstrng.argon.Argon.INSTANCE == null
				|| dev.lvstrng.argon.Argon.INSTANCE.getModuleManager() == null) {
			return null;
		}
		Freecam freecam = dev.lvstrng.argon.Argon.INSTANCE.getModuleManager().getModule(Freecam.class);
		return freecam != null && freecam.isEnabled() ? freecam : null;
	}

	public static void onWorldChanged() {
		Freecam freecam = enabledInstance();
		if (freecam != null) {
			freecam.discardRetainedChunks();
			freecam.observedLevel = null;
			freecam.retainingChunks = false;
			freecam.oldPos = freecam.pos = Vec3.ZERO;
		}
	}

	private void releaseRetainedChunks() {
		Set<ChunkPos> chunksToRelease = Set.copyOf(retainedChunks);
		retainedChunks.clear();

		ClientPacketListener connection = mc.getConnection();
		if (chunksToRelease.isEmpty() || connection == null || mc.level == null
				|| mc.level != observedLevel || connection.getLevel() != observedLevel) {
			return;
		}

		// Replay the delayed vanilla unloads instead of dropping only chunk data. This also
		// performs vanilla's debug-render and lighting cleanup once retention ends.
		for (ChunkPos chunkPos : chunksToRelease) {
			connection.handleForgetLevelChunk(new ClientboundForgetLevelChunkPacket(chunkPos));
		}
	}

	private void discardRetainedChunks() {
		retainedChunks.clear();
	}

	private void restoreSuppressedInput() {
		KeyMapping[] suppressed = {
				mc.options.keyUse, mc.options.keyAttack, mc.options.keyUp, mc.options.keyDown,
				mc.options.keyLeft, mc.options.keyRight, mc.options.keyJump, mc.options.keyShift
		};
		for (KeyMapping key : suppressed) {
			((IKeyBinding) key).resetPressed();
		}
	}
}
