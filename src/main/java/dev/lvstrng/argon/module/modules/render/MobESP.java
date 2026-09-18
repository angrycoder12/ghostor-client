package dev.lvstrng.argon.module.modules.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lvstrng.argon.config.ConfigManager;
import dev.lvstrng.argon.config.ConfigStateProvider;
import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.gui.ClickGui;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.render.blockesp.BlockEspShapeMode;
import dev.lvstrng.argon.module.modules.render.mobesp.MobEspData;
import dev.lvstrng.argon.module.setting.ActionSetting;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ColorSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.ClientState;
import dev.lvstrng.argon.utils.RenderUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** PlayerESP-style rendering for selected living, non-player entity types. */
public final class MobESP extends Module implements GameRenderListener, ConfigStateProvider {
	private static final Gson GSON = new GsonBuilder().create();
	public final ActionSetting entities = new ActionSetting("Entities", this::openSelector)
			.setDescription("Choose mobs and edit individual overrides");
	public final ModeSetting<BlockEspShapeMode> shapeMode = new ModeSetting<>(
			"Shape Mode", BlockEspShapeMode.Both, BlockEspShapeMode.class);
	public final ColorSetting lineColor = new ColorSetting("Line Color", new Color(116, 92, 255, 255));
	public final ColorSetting sideColor = new ColorSetting("Fill Color", new Color(116, 92, 255, 55));
	public final BooleanSetting tracers = new BooleanSetting("Tracers", false);
	public final ColorSetting tracerColor = new ColorSetting("Tracer Color", new Color(145, 125, 255, 210));
	public final NumberSetting renderDistance = new NumberSetting("Render Distance", 8, 256, 96, 4);
	public final NumberSetting maxEntities = new NumberSetting("Max Entities", 1, 512, 128, 1);
	public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);

	private final Set<String> selected = new HashSet<>();
	private final Map<String, MobEspData> custom = new HashMap<>();
	private final MobEspData global = new MobEspData();

	public MobESP() {
		super("Mob ESP", "Highlights selected mobs in the world", -1, Category.RENDER);
		addSettings(entities, shapeMode, lineColor, sideColor, tracers, tracerColor,
				renderDistance, maxEntities, throughWalls);
	}

	@Override public void onEnable() { eventManager.add(GameRenderListener.class, this); super.onEnable(); }
	@Override public void onDisable() { eventManager.remove(GameRenderListener.class, this); super.onDisable(); }

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.level == null || mc.player == null || selected.isEmpty()) return;
		refreshGlobal();
		double maxDistanceSq = renderDistance.getValue() * renderDistance.getValue();
		int limit = maxEntities.getValueInt();
		PriorityQueue<Candidate> nearest = new PriorityQueue<>(limit,
				Comparator.comparingDouble(Candidate::distanceSq).reversed());
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!valid(entity) || !selected.contains(id(entity.getType()))) continue;
			double distance = mc.player.distanceToSqr(entity);
			if (distance > maxDistanceSq) continue;
			Candidate candidate = new Candidate((LivingEntity) entity, distance);
			if (nearest.size() < limit) nearest.add(candidate);
			else if (distance < nearest.peek().distanceSq) { nearest.poll(); nearest.add(candidate); }
		}
		ArrayList<Candidate> ordered = new ArrayList<>(nearest);
		ordered.sort(Comparator.comparingDouble(Candidate::distanceSq));
		Vec3 tracerStart = RenderUtils.getCameraPos().add(mc.player.getViewVector(event.delta).scale(0.12D));
		for (Candidate candidate : ordered) render(candidate.entity, event.delta, tracerStart);
	}

	private void render(LivingEntity entity, float delta, Vec3 tracerStart) {
		Vec3 interpolated = entity.getPosition(delta);
		AABB box = entity.getBoundingBox().move(interpolated.subtract(entity.position())).inflate(0.02D);
		MobEspData data = resolveData(entity.getType());
		if (data.shapeMode == BlockEspShapeMode.Box || data.shapeMode == BlockEspShapeMode.Both)
			RenderUtils.renderFilledBox(box, data.sideColor(), throughWalls.getValue());
		if (data.shapeMode == BlockEspShapeMode.Lines || data.shapeMode == BlockEspShapeMode.Both)
			RenderUtils.renderBoxOutline(box, data.lineColor(), 1.0F, throughWalls.getValue());
		if (data.tracer)
			RenderUtils.renderLine(data.tracerColor(), tracerStart, box.getCenter(), 1.0F, throughWalls.getValue());
	}

	private boolean valid(Entity entity) {
		return entity instanceof LivingEntity living && !(entity instanceof Player)
				&& !(entity instanceof ArmorStand) && living.isAlive() && !living.isRemoved()
				&& !living.isSpectator();
	}

	public List<EntityType<?>> selectableTypes() {
		return BuiltInRegistries.ENTITY_TYPE.stream()
				.filter(type -> SpawnEggItem.byId(type).isPresent())
				.sorted(Comparator.comparing(type -> type.getDescription().getString(), String.CASE_INSENSITIVE_ORDER))
				.toList();
	}
	public boolean isSelected(EntityType<?> type) { return selected.contains(id(type)); }
	public void toggle(EntityType<?> type) {
		String id = id(type);
		if (!selected.remove(id)) selected.add(id);
		ConfigManager.notifyChanged();
	}
	public void clearSelected() { if (!selected.isEmpty()) { selected.clear(); ConfigManager.notifyChanged(); } }
	public int selectedCount() { return selected.size(); }
	public MobEspData customData(EntityType<?> type) {
		refreshGlobal();
		return custom.computeIfAbsent(id(type), ignored -> new MobEspData(global.shapeMode,
				global.lineColor(), global.sideColor(), global.tracer, global.tracerColor()));
	}
	public MobEspData resolveData(EntityType<?> type) {
		MobEspData data = custom.get(id(type));
		return data != null && data.useCustomSettings ? data : global;
	}
	public void markConfigChanged() { ConfigManager.notifyChanged(); }

	private void refreshGlobal() {
		global.shapeMode = shapeMode.getMode();
		global.lineColor = lineColor.getArgb();
		global.sideColor = sideColor.getArgb();
		global.tracer = tracers.getValue();
		global.tracerColor = tracerColor.getArgb();
	}
	private void openSelector() {
		if (ClientState.hasActiveWorld() && mc.gui.screen() instanceof ClickGui parent) parent.openMobSelector(this);
	}

	@Override public JsonObject saveConfigState() {
		JsonObject state = new JsonObject();
		state.add("selected", GSON.toJsonTree(selected));
		state.add("mobSettings", GSON.toJsonTree(custom));
		return state;
	}
	@Override public JsonObject defaultConfigState() {
		JsonObject state = new JsonObject();
		state.add("selected", new com.google.gson.JsonArray());
		state.add("mobSettings", new JsonObject());
		return state;
	}
	@Override public void validateConfigState(JsonObject state) {
		JsonElement selectedValue = state.get("selected");
		if (selectedValue != null && !selectedValue.isJsonArray()) throw new IllegalArgumentException("Invalid selected mobs");
		JsonElement settings = state.get("mobSettings");
		if (settings != null && !settings.isJsonObject()) throw new IllegalArgumentException("Invalid mob settings");
		if (settings != null) for (Map.Entry<String, JsonElement> entry : settings.getAsJsonObject().entrySet()) {
			Identifier identifier = Identifier.tryParse(entry.getKey());
			if (identifier == null || !entry.getValue().isJsonObject()) throw new IllegalArgumentException("Invalid mob override");
			MobEspData value = GSON.fromJson(entry.getValue(), MobEspData.class);
			if (value == null) throw new IllegalArgumentException("Invalid mob override");
			value.validate();
		}
	}
	@Override public void loadConfigState(JsonObject state) {
		validateConfigState(state);
		selected.clear();
		JsonElement selectedValue = state.get("selected");
		if (selectedValue != null) for (JsonElement value : selectedValue.getAsJsonArray()) {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) continue;
			Identifier identifier = Identifier.tryParse(value.getAsString());
			if (identifier != null && BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) selected.add(value.getAsString());
		}
		custom.clear();
		JsonElement settings = state.get("mobSettings");
		if (settings != null) for (Map.Entry<String, JsonElement> entry : settings.getAsJsonObject().entrySet()) {
			Identifier identifier = Identifier.tryParse(entry.getKey());
			if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) continue;
			MobEspData value = GSON.fromJson(entry.getValue(), MobEspData.class);
			value.validate(); custom.put(entry.getKey(), value);
		}
	}

	public static String id(EntityType<?> type) { return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(); }
	private record Candidate(LivingEntity entity, double distanceSq) {}
}
