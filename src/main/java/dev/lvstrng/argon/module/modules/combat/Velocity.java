package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.PacketReceiveListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.combat.velocity.JumpResetVelocityMode;
import dev.lvstrng.argon.module.modules.combat.velocity.LagVelocityMode;
import dev.lvstrng.argon.module.modules.combat.velocity.ModifyVelocityMode;
import dev.lvstrng.argon.module.modules.combat.velocity.ReversalVelocityMode;
import dev.lvstrng.argon.module.modules.combat.velocity.StrafeVelocityMode;
import dev.lvstrng.argon.module.modules.combat.velocity.VelocityModeHandler;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

/** LiquidBounce-inspired knockback modes implemented on Ghostor's packet/tick bus. */
public final class Velocity extends Module implements PacketReceiveListener, TickListener {
	public enum Mode { Modify, Lag, Reversal, Strafe, JumpReset }

	private static Velocity instance;
	private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.Modify, Mode.class);
	private final NumberSetting correctionPause = new NumberSetting(
			EncryptedString.of("Correction Pause (ticks)"), 0, 100, 20, 1);
	private final Map<Mode, VelocityModeHandler> handlers = new EnumMap<>(Mode.class);
	private VelocityModeHandler activeHandler;
	private volatile int pausedTicks;

	public Velocity() {
		super(EncryptedString.of("Velocity"),
				EncryptedString.of("Modifies incoming knockback behavior"), -1, Category.COMBAT);
		addSettings(mode, correctionPause);
		register(Mode.Modify, new ModifyVelocityMode(() -> mode.isMode(Mode.Modify)));
		register(Mode.Lag, new LagVelocityMode(() -> mode.isMode(Mode.Lag)));
		register(Mode.Reversal, new ReversalVelocityMode(() -> mode.isMode(Mode.Reversal)));
		register(Mode.Strafe, new StrafeVelocityMode(() -> mode.isMode(Mode.Strafe)));
		register(Mode.JumpReset, new JumpResetVelocityMode(() -> mode.isMode(Mode.JumpReset)));
		activeHandler = handlers.get(mode.getMode());
		instance = this;
	}

	private void register(Mode value, VelocityModeHandler handler) {
		handlers.put(value, handler);
		handler.settings().forEach(this::addSetting);
	}

	@Override
	public void onEnable() {
		pausedTicks = 0;
		syncMode();
		activeHandler.reset(false);
		eventManager.add(PacketReceiveListener.class, this, 900);
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketReceiveListener.class, this);
		eventManager.remove(TickListener.class, this);
		for (VelocityModeHandler handler : handlers.values()) handler.reset(true);
		pausedTicks = 0;
		super.onDisable();
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (event.isCancelled() || mc.player == null || mc.level == null) return;
		syncMode();
		if (event.packet instanceof ClientboundPlayerPositionPacket) {
			pausedTicks = correctionPause.getValueInt();
			activeHandler.reset(true);
			return;
		}
		if (pausedTicks <= 0) activeHandler.onPacket(event);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
			for (VelocityModeHandler handler : handlers.values()) handler.reset(false);
			return;
		}
		syncMode();
		if (pausedTicks > 0) pausedTicks--;
		else activeHandler.onTick();
	}

	private void syncMode() {
		VelocityModeHandler selected = handlers.get(mode.getMode());
		if (activeHandler == selected) return;
		if (activeHandler != null) activeHandler.reset(true);
		activeHandler = selected;
		activeHandler.reset(false);
	}

	public static void onWorldChanged() {
		Velocity module = instance;
		if (module == null) return;
		for (VelocityModeHandler handler : module.handlers.values()) handler.reset(false);
		module.pausedTicks = 0;
	}
}
