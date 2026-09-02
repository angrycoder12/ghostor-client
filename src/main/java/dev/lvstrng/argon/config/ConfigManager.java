package dev.lvstrng.argon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.ClickGui;
import dev.lvstrng.argon.gui.layout.GuiLayoutState;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.setting.ActionSetting;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ColorSetting;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.module.setting.StringSetting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;

/** Versioned, named, atomic Ghostor configuration store. */
public final class ConfigManager {
    public static final String DEFAULT_NAME = "none none";
    public static final String DEFAULT_ID = "none-none";
    public static final String EXTENSION = ".ghostorconfig";
    private static final int VERSION = 1;
    private static final long MAX_IMPORT_BYTES = 2L * 1024L * 1024L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ConfigManager instance;

    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("ghostor").resolve("configs");
    private final Path indexPath = directory.resolve("index.json");
    private final LinkedHashMap<String, Meta> configs = new LinkedHashMap<>();
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Ghostor Config Writer");
        thread.setDaemon(true);
        return thread;
    });
    private final JsonObject defaults;
    private ScheduledFuture<?> pendingSave;
    private String activeId = DEFAULT_ID;
    private boolean initialized;
    private boolean applying;
    private boolean dirty;
    private long changeRevision;

    public ConfigManager() {
        instance = this;
        defaults = captureDocument("defaults", false, true);
    }

    public static void notifyChanged() {
        ConfigManager manager = instance;
        if (manager != null) manager.onLiveStateChanged();
    }

    public Path directory() {
        return directory;
    }

    public boolean hasExistingStore() {
        return Files.isRegularFile(indexPath) || Files.isRegularFile(configPath(DEFAULT_ID));
    }

    public synchronized void initialize() {
        if (initialized) return;
        try {
            Files.createDirectories(directory);
            loadIndex();
            if (!configs.containsKey(DEFAULT_ID)) {
                configs.put(DEFAULT_ID, new Meta(DEFAULT_ID, DEFAULT_NAME, true));
            }
            boolean createdActiveDefault = false;
            if (!Files.isRegularFile(configPath(DEFAULT_ID))) {
                writeDocument(DEFAULT_ID, withMetadata(captureDocument(DEFAULT_NAME, true, false),
                        configs.get(DEFAULT_ID)));
                createdActiveDefault = DEFAULT_ID.equals(activeId);
            }
            if (!configs.containsKey(activeId)) activeId = DEFAULT_ID;
            initialized = true;
            if (!createdActiveDefault) {
                Result<JsonObject> loaded = readDocument(configPath(activeId));
                if (!loaded.ok() || !applyDocument(loaded.value())) {
                    activeId = DEFAULT_ID;
                    Result<JsonObject> fallback = readDocument(configPath(DEFAULT_ID));
                    if (fallback.ok()) applyDocument(fallback.value());
                }
            }
            dirty = false;
            saveIndex();
        } catch (Exception ignored) {
            configs.clear();
            configs.put(DEFAULT_ID, new Meta(DEFAULT_ID, DEFAULT_NAME, true));
            activeId = DEFAULT_ID;
            initialized = true;
            dirty = false;
        }
    }

    public synchronized List<ConfigSummary> summaries() {
        List<ConfigSummary> result = new ArrayList<>();
        Meta base = configs.get(DEFAULT_ID);
        if (base != null) result.add(summary(base));
        configs.values().stream()
                .filter(meta -> !meta.id.equals(DEFAULT_ID))
                .sorted(Comparator.comparing(meta -> meta.name.toLowerCase(Locale.ROOT)))
                .map(this::summary)
                .forEach(result::add);
        return List.copyOf(result);
    }

    public synchronized ConfigSummary active() {
        Meta meta = configs.get(activeId);
        return meta == null ? null : summary(meta);
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized boolean isApplying() {
        return applying;
    }

    public synchronized SwitchResult switchTo(String id, UnsavedAction action) {
        Meta target = configs.get(id);
        if (target == null) return SwitchResult.failed("Config no longer exists");
		if (id.equals(activeId)) return SwitchResult.success();
        if (dirty && !activeMeta().autoSave && action == null) {
            return SwitchResult.confirmation();
        }

        Result<JsonObject> document = readDocument(configPath(id));
        if (!document.ok()) return SwitchResult.failed(document.message());
        if (!validateDocument(document.value()).ok()) return SwitchResult.failed("Config is invalid or unsupported");

        if (dirty && !activeMeta().autoSave && action == UnsavedAction.SAVE) {
            Result<Void> saved = saveActiveNow();
            if (!saved.ok()) return SwitchResult.failed(saved.message());
        } else if (activeMeta().autoSave) {
            Result<Void> saved = saveActiveNow();
            if (!saved.ok()) return SwitchResult.failed(saved.message());
        }

		JsonObject rollback = captureDocument(activeMeta().name, activeMeta().autoSave, false);
		if (!applyDocument(document.value())) {
			applyDocument(rollback);
			return SwitchResult.failed("Config could not be applied");
		}
        activeId = id;
        dirty = false;
        saveIndex();
		return SwitchResult.success();
    }

    public synchronized Result<ConfigSummary> create(String rawName, boolean autoSave, CreationBasis basis,
            ImportedConfig imported) {
        Result<String> validatedName = validateName(rawName, null);
        if (!validatedName.ok()) return Result.error(validatedName.message());
        if (basis == CreationBasis.CURRENT && !DEFAULT_ID.equals(activeId)) {
            return Result.error("Use Current is only available from none none");
        }
        if (dirty && !activeMeta().autoSave && basis != CreationBasis.CURRENT) {
            return Result.error("Save or discard the active config's changes first");
        }
        if (activeMeta().autoSave || DEFAULT_ID.equals(activeId)) {
            Result<Void> saved = saveActiveNow();
            if (!saved.ok()) return Result.error(saved.message());
        }

        String id = UUID.randomUUID().toString();
        Meta meta = new Meta(id, validatedName.value(), autoSave);
        JsonObject document;
        if (basis == CreationBasis.SCRATCH) {
            document = defaults.deepCopy();
        } else if (basis == CreationBasis.IMPORTED) {
            if (imported == null) return Result.error("Choose a config file first");
            document = imported.document.deepCopy();
        } else {
            document = captureDocument(meta.name, autoSave, false);
        }
        document = withMetadata(document, meta);
        Result<Void> written = writeDocument(id, document);
        if (!written.ok()) return Result.error(written.message());

        configs.put(id, meta);
		JsonObject rollback = captureDocument(activeMeta().name, activeMeta().autoSave, false);
		if (basis != CreationBasis.CURRENT && !applyDocument(document)) {
			applyDocument(rollback);
            configs.remove(id);
            try {
                Files.deleteIfExists(configPath(id));
            } catch (IOException ignored) {
            }
            return Result.error("The new config could not be applied");
        }
        activeId = id;
        dirty = false;
        saveIndex();
        return Result.ok(summary(meta));
    }

    public synchronized Result<Void> rename(String id, String rawName) {
        Meta meta = configs.get(id);
        if (meta == null) return Result.error("Config no longer exists");
        return edit(id, rawName, meta.autoSave);
    }

    public synchronized Result<Void> setAutoSave(String id, boolean autoSave) {
        Meta meta = configs.get(id);
        if (meta == null) return Result.error("Config no longer exists");
        return edit(id, meta.name, autoSave);
    }

    public synchronized Result<Void> edit(String id, String rawName, boolean autoSave) {
        if (DEFAULT_ID.equals(id)) return Result.error("The default config cannot be edited");
        Meta meta = configs.get(id);
        if (meta == null) return Result.error("Config no longer exists");
        Result<String> name = validateName(rawName, id);
        if (!name.ok()) return Result.error(name.message());

        Result<JsonObject> document = readDocument(configPath(id));
        if (!document.ok()) return Result.error(document.message());
        Meta updated = new Meta(id, name.value(), autoSave);
        Result<Void> result = writeDocument(id, withMetadata(document.value(), updated));
        if (result.ok()) {
            meta.name = updated.name;
            meta.autoSave = updated.autoSave;
            saveIndex();
            if (id.equals(activeId) && autoSave && dirty) scheduleAutosave();
        }
        return result;
    }

    public synchronized Result<Void> saveActiveNow() {
        cancelPendingSave();
        return saveCurrentTo(activeId);
    }

    public synchronized Result<Void> saveCurrentTo(String id) {
        Meta meta = configs.get(id);
        if (meta == null) return Result.error("Config no longer exists");
        JsonObject document = withMetadata(captureDocument(meta.name, meta.autoSave, false), meta);
        Result<Void> result = writeDocument(id, document);
        if (result.ok() && id.equals(activeId)) dirty = false;
        return result;
    }

    public synchronized Result<Void> delete(String id) {
        if (DEFAULT_ID.equals(id)) return Result.error("The default config cannot be deleted");
        Meta meta = configs.get(id);
        if (meta == null) return Result.error("Config no longer exists");
        if (id.equals(activeId)) {
            SwitchResult switched = switchTo(DEFAULT_ID, UnsavedAction.DISCARD);
            if (!switched.switched()) return Result.error(switched.message());
        }
        configs.remove(id);
        try {
            Files.deleteIfExists(configPath(id));
            saveIndex();
            return Result.ok(null);
        } catch (IOException exception) {
            configs.put(id, meta);
            return Result.error("Could not delete the config file");
        }
    }

    public synchronized Result<ImportedConfig> readImport(Path source) {
        if (source == null) return Result.error("No file selected");
        try {
            if (!Files.isRegularFile(source) || Files.size(source) > MAX_IMPORT_BYTES) {
                return Result.error("Config file is missing or larger than 2 MB");
            }
            Result<JsonObject> parsed = parseDocument(Files.readString(source, StandardCharsets.UTF_8));
            if (!parsed.ok()) return Result.error(parsed.message());
            Result<Void> valid = validateDocument(parsed.value());
            if (!valid.ok()) return Result.error(valid.message());
            String name = stringOr(parsed.value(), "name", stripExtension(source.getFileName().toString()));
            if (!validateName(name, null).ok()) name = "Imported Config";
            boolean autoSave = booleanOr(parsed.value(), "autoSave", false);
            return Result.ok(new ImportedConfig(parsed.value().deepCopy(), name, autoSave));
        } catch (Exception exception) {
            return Result.error("Could not read that config file");
        }
    }

    public synchronized Result<Void> export(String id, Path destination) {
        Meta meta = configs.get(id);
        if (meta == null) return Result.error("Config no longer exists");
        if (destination == null) return Result.error("No export location selected");
        if (id.equals(activeId) && meta.autoSave) {
            Result<Void> flushed = saveActiveNow();
            if (!flushed.ok()) return flushed;
        }
        Result<JsonObject> document = readDocument(configPath(id));
        if (!document.ok()) return Result.error(document.message());
        String fileName = destination.getFileName().toString();
        Path target = fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION)
                ? destination : destination.resolveSibling(fileName + EXTENSION);
        return atomicWrite(target.toAbsolutePath().normalize(), GSON.toJson(document.value()));
    }

    public synchronized Path suggestedExportPath(String id) {
        Meta meta = configs.get(id);
        String safe = meta == null ? "ghostor-config" : meta.name.replaceAll("[^a-zA-Z0-9._ -]", "_").strip();
        if (safe.isBlank()) safe = "ghostor-config";
        return Path.of(System.getProperty("user.home", ".")).resolve(safe + EXTENSION);
    }

    public synchronized void shutdown() {
        if (!initialized) return;
        cancelPendingSave();
        if (activeMeta().autoSave || DEFAULT_ID.equals(activeId)) saveCurrentTo(activeId);
        saveIndex();
        writer.shutdown();
    }

    private synchronized void onLiveStateChanged() {
        if (!initialized || applying) return;
        dirty = true;
        changeRevision++;
        if (activeMeta().autoSave || DEFAULT_ID.equals(activeId)) scheduleAutosave();
    }

    private void scheduleAutosave() {
        cancelPendingSave();
        Meta meta = activeMeta();
        String id = activeId;
        long revision = changeRevision;
        String json = GSON.toJson(withMetadata(captureDocument(meta.name, meta.autoSave, false), meta));
        pendingSave = writer.schedule(() -> {
            synchronized (ConfigManager.this) {
                if (!id.equals(activeId) || revision != changeRevision) return;
                Result<Void> result = atomicWrite(configPath(id), json);
                if (result.ok() && revision == changeRevision) dirty = false;
            }
        }, 250, TimeUnit.MILLISECONDS);
    }

    private void cancelPendingSave() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
            pendingSave = null;
        }
    }

    private JsonObject captureDocument(String name, boolean autoSave, boolean useDefaults) {
        JsonObject root = new JsonObject();
        root.addProperty("configVersion", VERSION);
        root.addProperty("name", name);
        root.addProperty("autoSave", autoSave);
        root.addProperty("savedAt", Instant.now().toString());
        JsonObject modules = new JsonObject();
        if (Argon.INSTANCE != null && Argon.INSTANCE.getModuleManager() != null) {
            for (Module module : Argon.INSTANCE.getModuleManager().getModules()) {
                JsonObject state = new JsonObject();
                state.addProperty("enabled", useDefaults ? module.isDefaultEnabled() : module.isEnabled());
                state.addProperty("clientDisabled", !useDefaults && module.isClientDisabled());
                JsonObject settings = new JsonObject();
                for (Setting<?> setting : module.getSettings()) {
                    if (setting instanceof ActionSetting || setting.getName() == null) continue;
                    JsonObject saved = saveSetting(setting, useDefaults);
                    if (saved != null) settings.add(setting.getName().toString(), saved);
                }
                state.add("settings", settings);
                if (module instanceof ConfigStateProvider provider) {
                    state.add("moduleData", useDefaults
                            ? provider.defaultConfigState() : provider.saveConfigState());
                }
                modules.add(moduleId(module), state);
            }
        }
        root.add("modules", modules);
        if (!useDefaults && Argon.INSTANCE != null && Argon.INSTANCE.getClickGui() != null) {
            root.add("guiLayout", GSON.toJsonTree(Argon.INSTANCE.getClickGui().captureLayoutState()));
        }
        return root;
    }

    private JsonObject saveSetting(Setting<?> setting, boolean defaults) {
        JsonObject result = new JsonObject();
        if (setting instanceof BooleanSetting value) {
            result.addProperty("type", "boolean");
            result.addProperty("value", defaults ? value.getOriginalValue() : value.getValue());
        } else if (setting instanceof NumberSetting value) {
            result.addProperty("type", "number");
            result.addProperty("value", defaults ? value.getOriginalValue() : value.getValue());
        } else if (setting instanceof ModeSetting<?> value) {
            result.addProperty("type", "mode");
			result.addProperty("value", defaults ? value.getOriginalModeName() : value.getModeName());
        } else if (setting instanceof KeybindSetting value) {
            result.addProperty("type", "keybind");
            result.addProperty("value", defaults ? value.getOriginalKey() : value.getKey());
        } else if (setting instanceof StringSetting value) {
            result.addProperty("type", "string");
            result.addProperty("value", defaults ? value.getOriginalValue() : value.getValue());
        } else if (setting instanceof MinMaxSetting value) {
            result.addProperty("type", "minmax");
            JsonObject range = new JsonObject();
            range.addProperty("min", defaults ? value.getOriginalMinValue() : value.getMinValue());
            range.addProperty("max", defaults ? value.getOriginalMaxValue() : value.getMaxValue());
            result.add("value", range);
        } else if (setting instanceof ColorSetting value) {
            result.addProperty("type", "color");
            result.addProperty("value", defaults ? value.getOriginalArgb() : value.getArgb());
        } else {
            return null;
        }
        return result;
    }

    private Result<Void> validateDocument(JsonObject root) {
        try {
            if (root == null || intOr(root, "configVersion", -1) != VERSION) {
                return Result.error("Unsupported config version");
            }
            JsonElement modules = root.get("modules");
            if (modules == null || !modules.isJsonObject()) return Result.error("Config has no module data");
            JsonElement layoutElement = root.get("guiLayout");
            if (layoutElement != null) {
                if (!layoutElement.isJsonObject()) return Result.error("Invalid GUI layout data");
                GuiLayoutState.SavedLayout layout = GSON.fromJson(layoutElement, GuiLayoutState.SavedLayout.class);
                validatePanel(layout == null ? null : layout.main);
                validatePanel(layout == null ? null : layout.friends);
                validatePanel(layout == null ? null : layout.blockSelector);
                validatePanel(layout == null ? null : layout.configs);
                validatePanel(layout == null ? null : layout.configForm);
            }
            Map<String, Module> known = modulesById();
            for (Map.Entry<String, JsonElement> entry : modules.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonObject()) return Result.error("Invalid module entry");
                Module module = known.get(entry.getKey());
                if (module == null) continue;
                JsonObject state = entry.getValue().getAsJsonObject();
				if (state.has("enabled") && (!state.get("enabled").isJsonPrimitive()
						|| !state.getAsJsonPrimitive("enabled").isBoolean())) return Result.error("Invalid enabled state");
				if (state.has("clientDisabled") && (!state.get("clientDisabled").isJsonPrimitive()
						|| !state.getAsJsonPrimitive("clientDisabled").isBoolean())) return Result.error("Invalid disabled state");
                JsonElement settings = state.get("settings");
                if (settings != null && !settings.isJsonObject()) return Result.error("Invalid settings data");
                if (settings != null) validateSettings(module, settings.getAsJsonObject());
                JsonElement custom = state.get("moduleData");
                if (custom != null && module instanceof ConfigStateProvider provider) {
                    if (!custom.isJsonObject()) return Result.error("Invalid module-specific data");
                    provider.validateConfigState(custom.getAsJsonObject());
                }
            }
            return Result.ok(null);
        } catch (Exception exception) {
            return Result.error("Config data is malformed");
        }
    }

    private void validateSettings(Module module, JsonObject saved) {
        Map<String, Setting<?>> known = settingsByName(module);
        for (Map.Entry<String, JsonElement> entry : saved.entrySet()) {
            Setting<?> setting = known.get(entry.getKey());
            if (setting == null) continue;
            if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException();
            JsonElement value = entry.getValue().getAsJsonObject().get("value");
            if (value == null) throw new IllegalArgumentException();
            if (setting instanceof MinMaxSetting) {
                if (!value.isJsonObject() || !isFiniteNumber(value.getAsJsonObject().get("min"))
                        || !isFiniteNumber(value.getAsJsonObject().get("max"))) throw new IllegalArgumentException();
            } else if (setting instanceof StringSetting) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                        || value.getAsString().length() > 4096) throw new IllegalArgumentException();
            } else if (setting instanceof BooleanSetting) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
			} else if (setting instanceof ModeSetting<?> mode) {
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
						|| !mode.isValidModeName(value.getAsString())) throw new IllegalArgumentException();
			} else if (setting instanceof KeybindSetting) {
				if (!isIntegral(value) || value.getAsLong() < -1 || value.getAsLong() > 65_535) {
					throw new IllegalArgumentException();
				}
			} else if (setting instanceof ColorSetting) {
				if (!isIntegral(value) || value.getAsLong() < Integer.MIN_VALUE
						|| value.getAsLong() > Integer.MAX_VALUE) throw new IllegalArgumentException();
            } else if (!isFiniteNumber(value)) {
                throw new IllegalArgumentException();
            }
        }
    }

    private boolean applyDocument(JsonObject root) {
        Result<Void> valid = validateDocument(root);
        if (!valid.ok()) return false;
        applying = true;
        try {
            JsonObject savedModules = root.getAsJsonObject("modules");
            JsonObject defaultModules = defaults.getAsJsonObject("modules");
            List<Module> modules = Argon.INSTANCE.getModuleManager().getModules();
            ClickGui clickGui = Argon.INSTANCE.getClickGui();
            boolean keepGuiOpen = Argon.mc != null && Argon.mc.gui.screen() == clickGui;

            for (Module module : modules) {
                if (module.getCategory() != Category.CLIENT && module.isEnabled()) module.setEnabled(false);
            }
            for (Module module : modules) {
                JsonObject state = moduleState(savedModules, defaultModules, module);
                boolean disabled = booleanOr(state, "clientDisabled", false) && module.getOriginalCategory() != Category.CLIENT;
                module.setClientDisabled(disabled);
                applySettings(module, state.getAsJsonObject("settings"));
                if (module instanceof ConfigStateProvider provider && state.has("moduleData")) {
                    provider.loadConfigState(state.getAsJsonObject("moduleData"));
                }
            }
            if (root.has("guiLayout") && root.get("guiLayout").isJsonObject()) {
                GuiLayoutState.SavedLayout layout = GSON.fromJson(root.get("guiLayout"), GuiLayoutState.SavedLayout.class);
                clickGui.applyLayoutState(layout);
            } else {
                clickGui.resetLayoutState();
            }
            for (Module module : modules) {
                JsonObject state = moduleState(savedModules, defaultModules, module);
                boolean enabled = booleanOr(state, "enabled", false) && !module.isClientDisabled();
                if (keepGuiOpen && module instanceof ClickGUI) continue;
                module.setEnabled(enabled);
            }
            clickGui.refreshModuleWindows();
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            applying = false;
        }
    }

    private void applySettings(Module module, JsonObject saved) {
        if (saved == null) return;
        Map<String, Setting<?>> known = settingsByName(module);
        for (Map.Entry<String, JsonElement> entry : saved.entrySet()) {
            Setting<?> setting = known.get(entry.getKey());
            if (setting == null || !entry.getValue().isJsonObject()) continue;
            JsonElement value = entry.getValue().getAsJsonObject().get("value");
            if (value == null) continue;
            if (setting instanceof BooleanSetting target) target.setValue(value.getAsBoolean());
            else if (setting instanceof NumberSetting target) target.setValue(value.getAsDouble());
			else if (setting instanceof ModeSetting<?> target) target.setModeName(value.getAsString());
            else if (setting instanceof KeybindSetting target) {
                target.setKey(value.getAsInt());
                if (target.isModuleKey()) module.setKey(value.getAsInt());
            } else if (setting instanceof StringSetting target) target.setValue(value.getAsString());
            else if (setting instanceof MinMaxSetting target) {
                JsonObject range = value.getAsJsonObject();
                double minimum = range.get("min").getAsDouble();
                double maximum = range.get("max").getAsDouble();
                if (minimum <= maximum) {
                    target.setMinValue(minimum);
                    target.setMaxValue(maximum);
                } else {
                    target.setMinValue(maximum);
                    target.setMaxValue(minimum);
                }
            } else if (setting instanceof ColorSetting target) target.setArgb(value.getAsInt());
        }
    }

    private JsonObject moduleState(JsonObject saved, JsonObject fallback, Module module) {
        String id = moduleId(module);
        JsonElement state = saved.get(id);
        if (state != null && state.isJsonObject()) return state.getAsJsonObject();
        return fallback.getAsJsonObject(id).deepCopy();
    }

    private void loadIndex() {
        configs.clear();
        activeId = DEFAULT_ID;
        if (!Files.isRegularFile(indexPath)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(indexPath)).getAsJsonObject();
            if (intOr(root, "version", -1) != VERSION) return;
            String savedActive = stringOr(root, "activeId", DEFAULT_ID);
            JsonElement entries = root.get("configs");
            if (entries != null && entries.isJsonArray()) {
                for (JsonElement element : entries.getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject object = element.getAsJsonObject();
                    String id = stringOr(object, "id", "");
                    String name = stringOr(object, "name", "");
                    if (!validId(id) || !Files.isRegularFile(configPath(id))) continue;
                    if (DEFAULT_ID.equals(id)) name = DEFAULT_NAME;
                    if (!DEFAULT_ID.equals(id) && !validateName(name, id).ok()) continue;
                    configs.put(id, new Meta(id, name, DEFAULT_ID.equals(id) || booleanOr(object, "autoSave", false)));
                }
            }
            if (configs.containsKey(savedActive)) activeId = savedActive;
        } catch (Exception ignored) {
            configs.clear();
            activeId = DEFAULT_ID;
        }
    }

    private void saveIndex() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            root.addProperty("activeId", activeId);
            JsonArray entries = new JsonArray();
            for (Meta meta : configs.values()) {
                JsonObject object = new JsonObject();
                object.addProperty("id", meta.id);
                object.addProperty("name", meta.name);
                object.addProperty("autoSave", meta.autoSave);
                entries.add(object);
            }
            root.add("configs", entries);
            atomicWrite(indexPath, GSON.toJson(root));
        } catch (Exception ignored) {
        }
    }

    private Result<JsonObject> readDocument(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_IMPORT_BYTES) {
                return Result.error("Config file is missing or too large");
            }
            return parseDocument(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return Result.error("Could not read the config file");
        }
    }

    private Result<JsonObject> parseDocument(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return Result.error("Config root must be a JSON object");
            return Result.ok(parsed.getAsJsonObject());
        } catch (Exception exception) {
            return Result.error("Config contains invalid JSON");
        }
    }

    private Result<Void> writeDocument(String id, JsonObject document) {
        if (!validId(id)) return Result.error("Unsafe config identifier");
        return atomicWrite(configPath(id), GSON.toJson(document));
    }

    private Result<Void> atomicWrite(Path target, String content) {
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) return Result.error("Invalid file location");
        Path temporary = parent.resolve(normalized.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
			byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
			try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
				ByteBuffer buffer = ByteBuffer.wrap(bytes);
				while (buffer.hasRemaining()) channel.write(buffer);
				channel.force(true);
			}
            try {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
            }
            return Result.ok(null);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            return Result.error("Could not write the config file");
        }
    }

    private JsonObject withMetadata(JsonObject document, Meta meta) {
        JsonObject copy = document.deepCopy();
        copy.addProperty("configVersion", VERSION);
        copy.addProperty("name", meta.name);
        copy.addProperty("autoSave", meta.autoSave);
        copy.addProperty("savedAt", Instant.now().toString());
        return copy;
    }

    private Result<String> validateName(String rawName, String editingId) {
        if (rawName == null) return Result.error("Enter a config name");
        String name = rawName.strip();
        if (name.isEmpty() || name.length() > 48) return Result.error("Names must be 1 to 48 characters");
		if (name.equalsIgnoreCase(DEFAULT_NAME) || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
				|| name.matches(".*[<>:\"|?*].*") || name.endsWith(".")
				|| name.codePoints().anyMatch(Character::isISOControl)
				|| name.matches("(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?$")) {
            return Result.error("That config name is reserved or unsafe");
        }
        for (Meta meta : configs.values()) {
            if (!meta.id.equals(editingId) && meta.name.equalsIgnoreCase(name)) {
                return Result.error("A config with that name already exists");
            }
        }
        return Result.ok(name);
    }

    private Meta activeMeta() {
        Meta meta = configs.get(activeId);
        return meta == null ? new Meta(DEFAULT_ID, DEFAULT_NAME, true) : meta;
    }

    private ConfigSummary summary(Meta meta) {
        return new ConfigSummary(meta.id, meta.name, meta.autoSave, meta.id.equals(activeId),
                meta.id.equals(activeId) && dirty, meta.id.equals(DEFAULT_ID));
    }

    private Path configPath(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Unsafe config identifier");
        Path path = directory.resolve(id + EXTENSION).normalize();
        if (!path.getParent().equals(directory.normalize())) throw new IllegalArgumentException("Unsafe config path");
        return path;
    }

    private static boolean validId(String id) {
        return DEFAULT_ID.equals(id) || id != null && id.matches("[0-9a-fA-F-]{36}");
    }

    private static String moduleId(Module module) {
        return module.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Module> modulesById() {
        Map<String, Module> result = new LinkedHashMap<>();
        for (Module module : Argon.INSTANCE.getModuleManager().getModules()) result.put(moduleId(module), module);
        return result;
    }

    private static Map<String, Setting<?>> settingsByName(Module module) {
        Map<String, Setting<?>> result = new LinkedHashMap<>();
        for (Setting<?> setting : module.getSettings()) {
            if (!(setting instanceof ActionSetting) && setting.getName() != null) {
                result.put(setting.getName().toString(), setting);
            }
        }
        return result;
    }

    private static boolean isFiniteNumber(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return false;
        double value = element.getAsDouble();
        return Double.isFinite(value);
    }

    private static void validatePanel(GuiLayoutState.PanelState panel) {
        if (panel != null && !panel.isValid()) throw new IllegalArgumentException("Invalid GUI panel bounds");
    }

	private static boolean isIntegral(JsonElement element) {
		if (!isFiniteNumber(element)) return false;
		double value = element.getAsDouble();
		return value == Math.rint(value);
	}

    private static int intOr(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    private static boolean booleanOr(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                ? value.getAsBoolean() : fallback;
    }

    private static String stringOr(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : fallback;
    }

    private static String stripExtension(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)
                ? name.substring(0, name.length() - EXTENSION.length()) : name;
    }

    public enum CreationBasis {
        CURRENT,
        SCRATCH,
        IMPORTED
    }

    public enum UnsavedAction {
        SAVE,
        DISCARD
    }

    public record ConfigSummary(String id, String name, boolean autoSave, boolean active,
            boolean dirty, boolean permanent) {
    }

    public record ImportedConfig(JsonObject document, String suggestedName, boolean autoSave) {
    }

    public record Result<T>(boolean ok, T value, String message) {
        public static <T> Result<T> ok(T value) {
            return new Result<>(true, value, "");
        }

        public static <T> Result<T> error(String message) {
            return new Result<>(false, null, message);
        }
    }

    public record SwitchResult(boolean switched, boolean needsConfirmation, String message) {
		private static SwitchResult success() {
            return new SwitchResult(true, false, "");
        }

        private static SwitchResult confirmation() {
            return new SwitchResult(false, true, "Unsaved changes");
        }

        private static SwitchResult failed(String message) {
            return new SwitchResult(false, false, message);
        }
    }

    private static final class Meta {
        private final String id;
        private String name;
        private boolean autoSave;

        private Meta(String id, String name, boolean autoSave) {
            this.id = id;
            this.name = name;
            this.autoSave = autoSave;
        }
    }
}
