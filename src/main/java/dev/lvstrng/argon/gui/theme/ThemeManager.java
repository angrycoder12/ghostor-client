package dev.lvstrng.argon.gui.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lvstrng.argon.gui.GhostorTheme;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/** Global appearance persistence. Gameplay configs deliberately do not own this state. */
public final class ThemeManager {
	public enum ParticleColorMode {
		Theme_Accent,
		Custom
	}

	public static final class ParticleSettings {
		public boolean enabled;
		public int amount = 36;
		public double speed = 18.0;
		public double size = 1.5;
		public int opacity = 45;
		public ParticleColorMode colorMode = ParticleColorMode.Theme_Accent;
		public int customColor = new Color(139, 99, 255).getRGB();

		private void sanitize() {
			amount = Math.max(0, Math.min(100, amount));
			speed = Math.max(1.0, Math.min(60.0, speed));
			size = Math.max(0.5, Math.min(4.0, size));
			opacity = Math.max(5, Math.min(100, opacity));
			if (colorMode == null) colorMode = ParticleColorMode.Theme_Accent;
		}
	}

	private static final String DEFAULT_ID = "ghostor-default";
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private final Path path = FabricLoader.getInstance().getConfigDir()
			.resolve("ghostor").resolve("themes.json");
	private final LinkedHashMap<String, ThemeDefinition> builtIns = new LinkedHashMap<>();
	private final LinkedHashMap<String, ThemeDefinition> customs = new LinkedHashMap<>();
	private String activeId = DEFAULT_ID;
	private ParticleSettings particles = new ParticleSettings();

	public ThemeManager() {
		registerBuiltIns();
		load();
		apply(activeId);
	}

	public List<ThemeDefinition> themes() {
		List<ThemeDefinition> result = new ArrayList<>(builtIns.size() + customs.size());
		builtIns.values().forEach(theme -> result.add(theme.copy()));
		customs.values().stream()
				.sorted((left, right) -> left.name.compareToIgnoreCase(right.name))
				.forEach(theme -> result.add(theme.copy()));
		return List.copyOf(result);
	}

	public ThemeDefinition theme(String id) {
		ThemeDefinition theme = builtIns.get(id);
		if (theme == null) theme = customs.get(id);
		return theme == null ? null : theme.copy();
	}

	public ThemeDefinition activeTheme() {
		ThemeDefinition theme = theme(activeId);
		return theme != null ? theme : builtIns.get(DEFAULT_ID).copy();
	}

	public String activeId() {
		return activeId;
	}

	public ParticleSettings particles() {
		return particles;
	}

	public ThemeDefinition createCustom(ThemeDefinition source) {
		ThemeDefinition custom = (source == null ? activeTheme() : source).copy();
		custom.id = UUID.randomUUID().toString();
		custom.name = availableName("Custom Theme");
		custom.builtIn = false;
		custom.sanitize();
		customs.put(custom.id, custom.copy());
		save();
		return custom;
	}

	public ThemeDefinition saveCustom(ThemeDefinition draft) {
		if (draft == null || draft.builtIn || !customs.containsKey(draft.id)) return null;
		ThemeDefinition saved = draft.copy();
		saved.builtIn = false;
		saved.sanitize();
		saved.name = uniqueName(saved.name, saved.id);
		customs.put(saved.id, saved.copy());
		if (saved.id.equals(activeId)) GhostorTheme.apply(saved);
		save();
		return saved.copy();
	}

	public boolean deleteCustom(String id) {
		if (id == null || customs.remove(id) == null) return false;
		if (id.equals(activeId)) {
			activeId = DEFAULT_ID;
			GhostorTheme.apply(builtIns.get(DEFAULT_ID));
		}
		save();
		return true;
	}

	public boolean apply(String id) {
		ThemeDefinition theme = builtIns.get(id);
		if (theme == null) theme = customs.get(id);
		if (theme == null) return false;
		activeId = theme.id;
		GhostorTheme.apply(theme);
		save();
		return true;
	}

	public void saveParticleSettings() {
		particles.sanitize();
		save();
	}

	private void registerBuiltIns() {
		addBuiltIn(DEFAULT_ID, "Ghostor Default", 0xFF745CFF, 0xFF917DFF, 0xAC05070C,
				0xDE0E141E, 0x695B6A85, 0xFF745CFF, 0xFF414C60, 0xFFEEF2FA,
				0xFF9DAABF, 0xFFF1434E, 14, 70);
		addBuiltIn("midnight-purple", "Midnight Purple", 0xFF8B5CFF, 0xFFB397FF, 0xC0040610,
				0xEA090D19, 0x786D58A0, 0xFF8B5CFF, 0xFF39445A, 0xFFF4F0FF,
				0xFFA79DBD, 0xFFFF5265, 16, 85);
		addBuiltIn("amethyst", "Amethyst", 0xFFA86DFF, 0xFFD0A8FF, 0xAE100B19,
				0xE31B1428, 0x808D6CA8, 0xFFA86DFF, 0xFF5D526B, 0xFFFFF8FF,
				0xFFBDB0C8, 0xFFFF6070, 13, 58);
		addBuiltIn("crimson", "Crimson", 0xFFE34D65, 0xFFFF8192, 0xB20B070B,
				0xE3181119, 0x806F4B54, 0xFFE34D65, 0xFF554249, 0xFFFFF0F2,
				0xFFBDA6AA, 0xFFFF344D, 12, 62);
		addBuiltIn("ice", "Ice", 0xFF42BFEF, 0xFF8BE4FF, 0xB2040B10,
				0xE20B1A24, 0x80658A9C, 0xFF42BFEF, 0xFF3F5662, 0xFFF1FBFF,
				0xFFA5BDC8, 0xFFFF5B68, 14, 55);
		addBuiltIn("monochrome", "Monochrome", 0xFFE7E9ED, 0xFFFFFFFF, 0xC0040507,
				0xE5101216, 0x806F747D, 0xFFE7E9ED, 0xFF4F535B, 0xFFF5F6F8,
				0xFFAAADB3, 0xFFDD5863, 8, 25);
	}

	private void addBuiltIn(String id, String name, int primary, int secondary, int background,
			int panel, int border, int enabled, int disabled, int text, int secondaryText,
			int danger, int radius, int glow) {
		builtIns.put(id, new ThemeDefinition(id, name, true, primary, secondary, background, panel,
				border, enabled, disabled, text, secondaryText, danger, radius, glow));
	}

	private void load() {
		if (!Files.isRegularFile(path)) return;
		try {
			SavedState state = gson.fromJson(Files.readString(path), SavedState.class);
			if (state == null) return;
			if (state.customThemes != null) {
				for (ThemeDefinition custom : state.customThemes) {
					if (custom == null || custom.id == null || custom.id.isBlank() || builtIns.containsKey(custom.id)) continue;
					custom.builtIn = false;
					custom.sanitize();
					customs.put(custom.id, custom.copy());
				}
			}
			if (state.activeId != null && (builtIns.containsKey(state.activeId) || customs.containsKey(state.activeId))) {
				activeId = state.activeId;
			}
			if (state.particles != null) particles = state.particles;
			particles.sanitize();
		} catch (Exception ignored) {
			customs.clear();
			activeId = DEFAULT_ID;
			particles = new ParticleSettings();
		}
	}

	private void save() {
		SavedState state = new SavedState();
		state.version = 1;
		state.activeId = activeId;
		state.customThemes = customs.values().stream().map(ThemeDefinition::copy).toList();
		state.particles = particles;
		try {
			Files.createDirectories(path.getParent());
			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temporary, gson.toJson(state));
			try {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ignored) {
			// The current theme remains usable for the session if persistence fails.
		}
	}

	private String availableName(String base) {
		String candidate = base;
		int suffix = 2;
		while (containsName(candidate, null)) candidate = base + " " + suffix++;
		return candidate;
	}

	private String uniqueName(String requested, String ownId) {
		String base = requested == null || requested.isBlank() ? "Custom Theme" : requested.strip();
		String candidate = base;
		int suffix = 2;
		while (containsName(candidate, ownId)) candidate = base + " " + suffix++;
		return candidate;
	}

	private boolean containsName(String name, String ownId) {
		String normalized = name.toLowerCase(Locale.ROOT);
		return themes().stream().anyMatch(theme -> !theme.id.equals(ownId)
				&& theme.name.toLowerCase(Locale.ROOT).equals(normalized));
	}

	private static final class SavedState {
		int version;
		String activeId;
		List<ThemeDefinition> customThemes;
		ParticleSettings particles;
	}
}
