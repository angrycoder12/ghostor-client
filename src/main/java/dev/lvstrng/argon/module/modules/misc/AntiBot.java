package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.IdentityHashMap;
import java.util.Map;

/** Crow AntiBot's combat/render predicates adapted to modern styled text and entity events. */
public final class AntiBot extends Module implements TickListener {
	private static final long NEW_PLAYER_MILLIS = 4000L;
	private static final int LEGACY_RED_RGB = 0xFF5555;
	private static final Map<Player, Long> NEW_PLAYERS = new IdentityHashMap<>();
	private static AntiBot instance;
	private static boolean fabricEventsRegistered;

	private final BooleanSetting waitTicks = new BooleanSetting(EncryptedString.of("Wait 80t"), false);
	private final BooleanSetting hideDead = new BooleanSetting(EncryptedString.of("Hide dead"), true);
	private final BooleanSetting checkYaw = new BooleanSetting(EncryptedString.of("Check yaw"), true);
	private final NumberSetting yawTolerance = new NumberSetting(
			EncryptedString.of("Yaw tol"), 0.1D, 5.0D, 1.0D, 0.1D);
	private ClientLevel observedLevel;

	public AntiBot() {
		super(EncryptedString.of("AntiBot"),
				EncryptedString.of("Filters entities that match Crow's bot detection rules"),
				-1,
				Category.MISC);
		addSettings(waitTicks, hideDead, checkYaw, yawTolerance);
		instance = this;
		registerFabricEvents();
		// Crow ships AntiBot enabled by default. Config loading can still turn it off later.
		setEnabled(true);
	}

	@Override
	public void onEnable() {
		observedLevel = mc.level;
		eventManager.add(TickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		NEW_PLAYERS.clear();
		observedLevel = null;
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (observedLevel != mc.level) {
			NEW_PLAYERS.clear();
			observedLevel = mc.level;
		}
		if (waitTicks.getValue() && !NEW_PLAYERS.isEmpty()) {
			long cutoff = System.currentTimeMillis() - NEW_PLAYER_MILLIS;
			NEW_PLAYERS.entrySet().removeIf(entry -> entry.getValue() < cutoff
					|| entry.getKey().isRemoved() || entry.getKey().level() != mc.level);
		}
	}

	/** Crow's stricter predicate used by combat targeting. */
	public static boolean bot(Entity entity) {
		AntiBot module = enabledInstance();
		if (module == null || Argon.mc.player == null || Argon.mc.level == null
				|| Argon.mc.gui.screen() != null || entity == null) {
			return false;
		}
		if (entity instanceof Player player && module.waitTicks.getValue()
				&& NEW_PLAYERS.containsKey(player)) {
			return true;
		}
		if (startsWithLegacyRed(entity.getName()) || !entity.isAlive() && module.hideDead.getValue()) {
			return true;
		}
		Component displayName = entity.getDisplayName();
		if (displayName == null) return false;

		if (module.checkYaw.getValue()) {
			float yawDifference = Math.abs(entity.getYRot() - Argon.mc.player.getYRot()) % 360.0F;
			if (yawDifference > 180.0F) yawDifference = 360.0F - yawDifference;
			if (yawDifference <= module.yawTolerance.getValueFloat()) return true;
		}

		String name = displayName.getString();
		if (name.indexOf('\u00a7') >= 0) return name.contains("[NPC] ");
		if (name.isEmpty() && entity.getName().getString().isEmpty()) return true;
		if (name.length() == 10) {
			int numbers = 0;
			int letters = 0;
			for (char character : name.toCharArray()) {
				if (Character.isLetter(character)) {
					if (Character.isUpperCase(character)) return false;
					letters++;
				} else {
					if (!Character.isDigit(character)) return false;
					numbers++;
				}
			}
			return numbers >= 2 && letters >= 2;
		}
		return false;
	}

	/** Crow's narrower predicate used by ESP and nametag rendering. */
	public static boolean renderBot(Entity entity) {
		AntiBot module = enabledInstance();
		if (module == null || Argon.mc.player == null || Argon.mc.level == null || entity == null) {
			return false;
		}
		if (!entity.isAlive() && module.hideDead.getValue()) return true;
		String entityName = entity.getName().getString();
		if (entityName.startsWith("\u00c2\u00a7c")) return true;
		Component displayName = entity.getDisplayName();
		if (displayName == null) return false;
		String display = displayName.getString();
		String lower = display.toLowerCase(java.util.Locale.ROOT);
		return display.contains("[NPC] ") || lower.startsWith("npc") || lower.contains("[npc]")
				|| display.isEmpty() && entityName.isEmpty();
	}

	public static void onWorldChanged() {
		NEW_PLAYERS.clear();
		AntiBot module = instance;
		if (module != null) module.observedLevel = null;
	}

	private static AntiBot enabledInstance() {
		AntiBot module = instance;
		return module != null && module.isEnabled() && !module.isClientDisabled() ? module : null;
	}

	private static void registerFabricEvents() {
		if (fabricEventsRegistered) return;
		fabricEventsRegistered = true;
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			AntiBot module = enabledInstance();
			if (module == null || !module.waitTicks.getValue() || Argon.mc.player == null
					|| level != Argon.mc.level || !(entity instanceof Player player)
					|| player == Argon.mc.player) {
				return;
			}
			NEW_PLAYERS.put(player, System.currentTimeMillis());
		});
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Player player) NEW_PLAYERS.remove(player);
		});
	}

	private static boolean startsWithLegacyRed(Component component) {
		if (component == null) return false;
		if (component.getString().startsWith("\u00a7c")) return true;
		for (Component part : component.toFlatList()) {
			if (part.getString().isEmpty()) continue;
			TextColor color = part.getStyle().getColor();
			return color != null && color.getValue() == LEGACY_RED_RGB;
		}
		return false;
	}
}
