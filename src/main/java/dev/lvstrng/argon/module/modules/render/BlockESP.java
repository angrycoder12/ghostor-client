package dev.lvstrng.argon.module.modules.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.config.ConfigManager;
import dev.lvstrng.argon.config.ConfigStateProvider;
import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.event.events.PacketReceiveListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.gui.ClickGui;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.render.blockesp.BlockEspBlockData;
import dev.lvstrng.argon.module.modules.render.blockesp.BlockEspChunk;
import dev.lvstrng.argon.module.modules.render.blockesp.BlockEspEntry;
import dev.lvstrng.argon.module.modules.render.blockesp.BlockEspGroup;
import dev.lvstrng.argon.module.modules.render.blockesp.BlockEspShapeMode;
import dev.lvstrng.argon.module.setting.ActionSetting;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.ColorSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.ClientState;
import dev.lvstrng.argon.utils.WorldUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Chunk-cached Block ESP with native Ghostor settings and per-block overrides.
 * Loaded chunks are scanned on one background worker and never by the render loop.
 */
public final class BlockESP extends Module implements TickListener, GameRenderListener, PacketReceiveListener, ConfigStateProvider {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
			.resolve("ghostor").resolve("block-esp.json");

	public final ActionSetting blocks = new ActionSetting("Blocks", this::openSelector)
			.setDescription("Choose blocks and edit individual overrides");
	public final ModeSetting<BlockEspShapeMode> shapeMode = new ModeSetting<>(
			"Shape Mode", BlockEspShapeMode.Both, BlockEspShapeMode.class);
	public final ColorSetting lineColor = new ColorSetting("Line Color", new Color(116, 92, 255, 255));
	public final ColorSetting sideColor = new ColorSetting("Side Color", new Color(116, 92, 255, 55));
	public final BooleanSetting tracers = new BooleanSetting("Tracers", false);
	public final ColorSetting tracerColor = new ColorSetting("Tracer Color", new Color(145, 125, 255, 210));
	public final NumberSetting renderDistance = new NumberSetting("Render Distance", 8, 256, 64, 4);
	public final NumberSetting maxBlocks = new NumberSetting("Max Blocks", 16, 4096, 512, 16);
	public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);

	private final Set<String> selectedBlocks = new HashSet<>();
	private final Map<String, BlockEspBlockData> blockData = new HashMap<>();
	private final Long2ObjectOpenHashMap<BlockEspChunk> chunks = new Long2ObjectOpenHashMap<>();
	private final Queue<ChunkOperation> pendingChunkOperations = new ConcurrentLinkedQueue<>();
	private final Queue<BlockPos> pendingBlockUpdates = new ConcurrentLinkedQueue<>();
	private final LongOpenHashSet queuedChunks = new LongOpenHashSet();
	private final Queue<ScanResult> scanResults = new ConcurrentLinkedQueue<>();
	private final AtomicInteger scanGeneration = new AtomicInteger();
	private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Ghostor Block ESP Scanner");
		thread.setDaemon(true);
		return thread;
	});
	private final BlockEspBlockData globalRenderData = new BlockEspBlockData();

	private List<BlockEspEntry> renderEntries = List.of();
	private List<BlockEspGroup> renderGroups = List.of();
	private ClientLevel cachedLevel;
	private boolean blacklist;
	private boolean rescanRequested;
	private boolean renderCacheDirty;
	private int entryCount;
	private int tickCounter;
	private int observedMaxBlocks = -1;
	private int observedRenderDistance = -1;
	private BlockPos lastSortPosition;
	private Future<?> activeFullScan;

	public BlockESP() {
		super("Block ESP", "Highlights selected block types in the world", -1, Category.RENDER);
		addSettings(blocks, shapeMode, lineColor, sideColor, tracers, tracerColor,
				renderDistance, maxBlocks, throughWalls);
		loadConfig();
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(GameRenderListener.class, this);
		eventManager.add(PacketReceiveListener.class, this);
		rescanRequested = true;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(GameRenderListener.class, this);
		eventManager.remove(PacketReceiveListener.class, this);
		clearRuntimeCache();
		saveConfig();
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			clearRuntimeCache();
			return;
		}
		if (cachedLevel != mc.level) {
			clearRuntimeCache();
			cachedLevel = mc.level;
			rescanRequested = true;
		}

		if (rescanRequested) {
			clearChunkData();
			startLoadedChunkScan();
			rescanRequested = false;
		}

		drainChunkOperations();
		drainScanResults();
		drainBlockUpdates();

		int currentMax = maxBlocks.getValueInt();
		int currentDistance = renderDistance.getValueInt();
		BlockPos playerPos = mc.player.blockPosition();
		if (currentMax != observedMaxBlocks || currentDistance != observedRenderDistance
				|| lastSortPosition == null || !lastSortPosition.equals(playerPos)) {
			if (observedMaxBlocks >= 0 && currentMax != observedMaxBlocks && blacklist) {
				requestRescan();
			}
			observedMaxBlocks = currentMax;
			observedRenderDistance = currentDistance;
			lastSortPosition = playerPos.immutable();
			renderCacheDirty = true;
		}

		if (++tickCounter % 20 == 0) {
			reconcileLoadedChunks();
			pruneUnloadedChunks();
		}
		if (renderCacheDirty) rebuildRenderCache();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.level == null || mc.player == null) return;
		refreshGlobalRenderData();
		for (BlockEspEntry entry : renderEntries) entry.render(this);

		Vec3 start = mc.hitResult != null ? mc.hitResult.getLocation() : mc.gameRenderer.mainCamera().position();
		for (BlockEspGroup group : renderGroups) {
			BlockEspBlockData data = resolveData(group.block());
			if (!data.tracer) continue;
			GizmoProperties line = Gizmos.line(start, group.center(), data.tracerColor);
			if (throughWalls()) line.setAlwaysOnTop();
		}
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (event.packet instanceof ClientboundLevelChunkWithLightPacket packet) {
			pendingChunkOperations.add(new ChunkOperation(false, packet.getX(), packet.getZ()));
		} else if (event.packet instanceof ClientboundForgetLevelChunkPacket packet) {
			pendingChunkOperations.add(new ChunkOperation(true, packet.pos().x(), packet.pos().z()));
		} else if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
			pendingBlockUpdates.add(packet.getPos().immutable());
		} else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket packet) {
			packet.runUpdates((pos, state) -> pendingBlockUpdates.add(pos.immutable()));
		}
	}

	public ClientLevel minecraftLevel() {
		return mc.level;
	}

	public boolean throughWalls() {
		return throughWalls.getValue();
	}

	public boolean isBlacklist() {
		return blacklist;
	}

	public void setBlacklist(boolean blacklist) {
		if (this.blacklist == blacklist) return;
		this.blacklist = blacklist;
		requestRescan();
		ConfigManager.notifyChanged();
		saveConfig();
	}

	public boolean isListed(Block block) {
		return selectedBlocks.contains(id(block));
	}

	public boolean isRendered(Block block) {
		return matches(block);
	}

	public void toggleListed(Block block) {
		String id = id(block);
		if (!selectedBlocks.remove(id)) selectedBlocks.add(id);
		requestRescan();
		ConfigManager.notifyChanged();
	}

	public void clearSelected() {
		if (selectedBlocks.isEmpty()) return;
		selectedBlocks.clear();
		requestRescan();
		ConfigManager.notifyChanged();
	}

	public int selectedCount() {
		return selectedBlocks.size();
	}

	public boolean matches(Block block) {
		if (block == null || block.asItem() == Items.AIR) return false;
		boolean listed = selectedBlocks.contains(id(block));
		return blacklist ? !listed : listed;
	}

	public BlockEspBlockData customData(Block block) {
		refreshGlobalRenderData();
		String identifier = id(block);
		BlockEspBlockData existing = blockData.get(identifier);
		if (existing != null) return existing;
		BlockEspBlockData created = new BlockEspBlockData(
				globalRenderData.shapeMode, globalRenderData.lineColor(), globalRenderData.sideColor(),
				globalRenderData.tracer, globalRenderData.tracerColor());
		blockData.put(identifier, created);
		ConfigManager.notifyChanged();
		return created;
	}

	public BlockEspBlockData resolveData(Block block) {
		BlockEspBlockData custom = blockData.get(id(block));
		return custom != null && custom.useCustomSettings ? custom : globalRenderData;
	}

	public void markConfigChanged(boolean requiresRescan) {
		if (requiresRescan) requestRescan();
		renderCacheDirty = true;
		ConfigManager.notifyChanged();
	}

	@Override
	public JsonObject saveConfigState() {
		JsonObject state = new JsonObject();
		state.addProperty("blacklist", blacklist);
		state.add("selected", GSON.toJsonTree(selectedBlocks));
		state.add("blockSettings", GSON.toJsonTree(blockData));
		return state;
	}

	@Override
	public JsonObject defaultConfigState() {
		JsonObject state = new JsonObject();
		state.addProperty("blacklist", false);
		state.add("selected", new com.google.gson.JsonArray());
		state.add("blockSettings", new JsonObject());
		return state;
	}

	@Override
	public void validateConfigState(JsonObject state) {
		JsonElement blacklistValue = state.get("blacklist");
		if (blacklistValue != null && (!blacklistValue.isJsonPrimitive()
				|| !blacklistValue.getAsJsonPrimitive().isBoolean())) {
			throw new IllegalArgumentException("Invalid blacklist mode");
		}
		JsonElement selected = state.get("selected");
		if (selected != null && !selected.isJsonArray()) throw new IllegalArgumentException("Invalid selected blocks");
		if (selected != null) {
			for (JsonElement element : selected.getAsJsonArray()) {
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					throw new IllegalArgumentException("Invalid block id");
				}
			}
		}
		JsonElement data = state.get("blockSettings");
		if (data != null && !data.isJsonObject()) throw new IllegalArgumentException("Invalid block settings");
		if (data != null) {
			for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
				if (Identifier.tryParse(entry.getKey()) == null || !entry.getValue().isJsonObject()) {
					throw new IllegalArgumentException("Invalid block settings entry");
				}
				BlockEspBlockData parsed = GSON.fromJson(entry.getValue(), BlockEspBlockData.class);
				if (parsed == null) throw new IllegalArgumentException("Invalid block override");
				parsed.validate();
			}
		}
	}

	@Override
	public void loadConfigState(JsonObject state) {
		validateConfigState(state);
		Set<String> loadedSelected = new HashSet<>();
		Map<String, BlockEspBlockData> loadedData = new HashMap<>();
		JsonElement selected = state.get("selected");
		if (selected != null) {
			for (JsonElement element : selected.getAsJsonArray()) {
				String value = element.getAsString();
				Identifier identifier = Identifier.tryParse(value);
				if (identifier != null && BuiltInRegistries.BLOCK.containsKey(identifier)) loadedSelected.add(value);
			}
		}
		JsonElement data = state.get("blockSettings");
		if (data != null) {
			for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
				Identifier identifier = Identifier.tryParse(entry.getKey());
				if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) continue;
				BlockEspBlockData parsed = GSON.fromJson(entry.getValue(), BlockEspBlockData.class);
				parsed.validate();
				loadedData.put(entry.getKey(), parsed);
			}
		}
		selectedBlocks.clear();
		selectedBlocks.addAll(loadedSelected);
		blockData.clear();
		blockData.putAll(loadedData);
		blacklist = getBoolean(state, "blacklist", false);
		requestRescan();
	}

	public String blockCategory(Block block) {
		String path = id(block);
		if (path.contains("ore") || path.contains("ancient_debris")) return "Ore";
		if (path.contains("chest") || path.contains("barrel") || path.contains("shulker")
				|| path.contains("hopper") || path.contains("furnace")) return "Container";
		if (path.contains("crafting") || path.contains("anvil") || path.contains("enchant")
				|| path.contains("beacon") || path.contains("spawner") || path.contains("table")) return "Utility";
		return "Other";
	}

	public void saveConfig() {
		// The old file remains a one-time migration source. Named configs now own
		// every Block ESP value so separate writes cannot bleed across profiles.
	}

	public static void onWorldChanged() {
		BlockESP module = getInstance();
		if (module != null) module.clearRuntimeCache();
	}

	public static void onClientClosing() {
		BlockESP module = getInstance();
		if (module != null) module.saveConfig();
	}

	private static BlockESP getInstance() {
		if (Argon.INSTANCE == null || Argon.INSTANCE.getModuleManager() == null) return null;
		return Argon.INSTANCE.getModuleManager().getModule(BlockESP.class);
	}

	private void openSelector() {
		if (!ClientState.hasActiveWorld() || !(mc.gui.screen() instanceof ClickGui parent)) return;
		parent.openBlockSelector(this);
	}

	private void requestRescan() {
		rescanRequested = true;
		renderCacheDirty = true;
	}

	private void startLoadedChunkScan() {
		if (mc.player == null || mc.level == null) return;
		ChunkPos center = mc.player.chunkPosition();
		List<LevelChunk> loadedChunks = WorldUtils.getLoadedChunks()
				.sorted(Comparator.comparingInt(chunk -> chunk.getPos().distanceSquared(center)))
				.toList();
		int generation = scanGeneration.get();
		Predicate<BlockState> filter = snapshotFilter();
		int resultLimit = perChunkResultLimit();
		for (LevelChunk chunk : loadedChunks) queuedChunks.add(chunk.getPos().pack());
		activeFullScan = scanExecutor.submit(() -> {
			for (LevelChunk chunk : loadedChunks) {
				if (Thread.currentThread().isInterrupted() || generation != scanGeneration.get()) return;
				BlockEspChunk scanned = BlockEspChunk.scan(chunk, filter, resultLimit);
				scanResults.add(new ScanResult(generation, chunk.getPos().pack(), scanned));
			}
		});
	}

	private void scheduleChunkScan(long packedPos) {
		if (mc.level == null || !queuedChunks.add(packedPos)) return;
		int x = ChunkPos.getX(packedPos);
		int z = ChunkPos.getZ(packedPos);
		if (!mc.level.hasChunk(x, z)) {
			queuedChunks.remove(packedPos);
			return;
		}
		LevelChunk chunk = mc.level.getChunk(x, z);
		int generation = scanGeneration.get();
		Predicate<BlockState> filter = snapshotFilter();
		int resultLimit = perChunkResultLimit();
		scanExecutor.execute(() -> {
			if (generation != scanGeneration.get()) return;
			BlockEspChunk scanned = BlockEspChunk.scan(chunk, filter, resultLimit);
			scanResults.add(new ScanResult(generation, packedPos, scanned));
		});
	}

	private void drainChunkOperations() {
		ChunkOperation operation;
		while ((operation = pendingChunkOperations.poll()) != null) {
			long key = ChunkPos.pack(operation.x, operation.z);
			if (operation.remove) removeChunk(key);
			else scheduleChunkScan(key);
		}
	}

	private void drainScanResults() {
		ScanResult result;
		while ((result = scanResults.poll()) != null) {
			if (result.generation != scanGeneration.get() || mc.level == null) continue;
			queuedChunks.remove(result.key);
			int x = ChunkPos.getX(result.key);
			int z = ChunkPos.getZ(result.key);
			if (!mc.level.hasChunk(x, z)) continue;
			BlockEspChunk old = chunks.put(result.key, result.chunk);
			entryCount += result.chunk.size() - (old == null ? 0 : old.size());
			renderCacheDirty = true;
		}
	}

	private void drainBlockUpdates() {
		int budget = 2048;
		BlockPos pos;
		while (budget-- > 0 && (pos = pendingBlockUpdates.poll()) != null) updateBlock(pos);
	}

	/**
	 * Packet delivery and client-chunk insertion are not guaranteed to happen in
	 * the same order. Reconcile once per second so every chunk visible through
	 * the same loaded-chunk source as Storage ESP is eventually scanned.
	 */
	private void reconcileLoadedChunks() {
		if (mc.level == null || mc.player == null) return;
		WorldUtils.getLoadedChunks().forEach(chunk -> {
			long key = chunk.getPos().pack();
			if (!chunks.containsKey(key) && !queuedChunks.contains(key)) scheduleChunkScan(key);
		});
	}

	private void updateBlock(BlockPos pos) {
		if (mc.level == null || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
		long chunkKey = ChunkPos.pack(pos);
		BlockEspChunk chunk = chunks.get(chunkKey);
		BlockState state = mc.level.getBlockState(pos);
		boolean shouldTrack = !state.isAir() && matches(state.getBlock());
		if (chunk == null) {
			if (!shouldTrack) return;
			chunk = new BlockEspChunk(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
			chunks.put(chunkKey, chunk);
		}

		BlockEspEntry existing = chunk.get(pos);
		if (shouldTrack) {
			chunk.put(pos, state);
			if (existing == null) entryCount++;
		} else if (chunk.remove(pos)) {
			entryCount--;
		}
		renderCacheDirty = true;
	}

	private void pruneUnloadedChunks() {
		if (mc.level == null) return;
		List<Long> toRemove = new ArrayList<>();
		for (long key : chunks.keySet()) {
			if (!mc.level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key))) toRemove.add(key);
		}
		toRemove.forEach(this::removeChunk);
	}

	private void removeChunk(long key) {
		BlockEspChunk removed = chunks.remove(key);
		if (removed != null) {
			entryCount -= removed.size();
			renderCacheDirty = true;
		}
		queuedChunks.remove(key);
	}

	private void rebuildRenderCache() {
		if (mc.player == null) return;
		Vec3 player = mc.player.position();
		double maxDistanceSquared = renderDistance.getValue() * renderDistance.getValue();
		int limit = maxBlocks.getValueInt();
		Comparator<BlockEspEntry> nearestFirst = Comparator.comparingDouble(
				entry -> entry.pos().distToCenterSqr(player));
		PriorityQueue<BlockEspEntry> nearest = new PriorityQueue<>(limit, nearestFirst.reversed());
		for (BlockEspChunk chunk : chunks.values()) {
			for (BlockEspEntry entry : chunk.entries().values()) {
				double distance = entry.pos().distToCenterSqr(player);
				if (distance > maxDistanceSquared) continue;
				if (nearest.size() < limit) nearest.add(entry);
				else if (distance < nearest.peek().pos().distToCenterSqr(player)) {
					nearest.poll();
					nearest.add(entry);
				}
			}
		}
		List<BlockEspEntry> candidates = new ArrayList<>(nearest);
		candidates.sort(nearestFirst);
		renderEntries = List.copyOf(candidates);
		renderGroups = buildGroups(renderEntries);
		renderCacheDirty = false;
	}

	private List<BlockEspGroup> buildGroups(List<BlockEspEntry> entries) {
		Long2ObjectOpenHashMap<BlockEspEntry> byPosition = new Long2ObjectOpenHashMap<>(entries.size());
		for (BlockEspEntry entry : entries) byPosition.put(entry.pos().asLong(), entry);
		LongOpenHashSet visited = new LongOpenHashSet(entries.size());
		List<BlockEspGroup> groups = new ArrayList<>();
		Queue<BlockEspEntry> queue = new ArrayDeque<>();

		for (BlockEspEntry root : entries) {
			if (!visited.add(root.pos().asLong())) continue;
			BlockEspGroup group = new BlockEspGroup(root.block());
			queue.add(root);
			while (!queue.isEmpty()) {
				BlockEspEntry entry = queue.remove();
				group.add(entry);
				for (Direction direction : Direction.values()) {
					long neighborKey = BlockPos.offset(entry.pos().asLong(), direction);
					BlockEspEntry neighbor = byPosition.get(neighborKey);
					if (neighbor != null && neighbor.block() == root.block() && visited.add(neighborKey)) {
						queue.add(neighbor);
					}
				}
			}
			groups.add(group);
		}
		return List.copyOf(groups);
	}

	private Predicate<BlockState> snapshotFilter() {
		Set<String> selected = Set.copyOf(selectedBlocks);
		boolean blacklistSnapshot = blacklist;
		return state -> {
			if (state.isAir() || state.getBlock().asItem() == Items.AIR) return false;
			boolean listed = selected.contains(id(state.getBlock()));
			return blacklistSnapshot ? !listed : listed;
		};
	}

	private int perChunkResultLimit() {
		if (!blacklist) return Integer.MAX_VALUE;
		return Math.max(128, Math.min(1024, maxBlocks.getValueInt()));
	}

	private void clearRuntimeCache() {
		clearChunkData();
		cachedLevel = null;
		rescanRequested = isEnabled();
	}

	private void clearChunkData() {
		scanGeneration.incrementAndGet();
		if (activeFullScan != null) {
			activeFullScan.cancel(true);
			activeFullScan = null;
		}
		chunks.clear();
		pendingChunkOperations.clear();
		pendingBlockUpdates.clear();
		queuedChunks.clear();
		scanResults.clear();
		renderEntries = List.of();
		renderGroups = List.of();
		entryCount = 0;
		renderCacheDirty = true;
		lastSortPosition = null;
	}

	private void refreshGlobalRenderData() {
		globalRenderData.shapeMode = shapeMode.getMode();
		globalRenderData.lineColor = lineColor.getArgb();
		globalRenderData.sideColor = sideColor.getArgb();
		globalRenderData.tracer = tracers.getValue();
		globalRenderData.tracerColor = tracerColor.getArgb();
	}

	private void loadConfig() {
		if (!Files.isRegularFile(CONFIG_PATH)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(CONFIG_PATH)).getAsJsonObject();
			blacklist = getBoolean(root, "blacklist", false);
			JsonElement selected = root.get("selected");
			if (selected != null && selected.isJsonArray()) {
				selected.getAsJsonArray().forEach(element -> {
					String value = element.getAsString();
					Identifier id = Identifier.tryParse(value);
					if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) selectedBlocks.add(value);
				});
			}
			JsonElement data = root.get("blockSettings");
			if (data != null && data.isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
					Identifier id = Identifier.tryParse(entry.getKey());
					if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) continue;
					BlockEspBlockData parsed = GSON.fromJson(entry.getValue(), BlockEspBlockData.class);
					if (parsed != null) {
						parsed.validate();
						blockData.put(entry.getKey(), parsed);
					}
				}
			}
			JsonElement globalElement = root.get("global");
			if (globalElement != null && globalElement.isJsonObject()) loadGlobal(globalElement.getAsJsonObject());
		} catch (Exception ignored) {
			selectedBlocks.clear();
			blockData.clear();
			blacklist = false;
		}
	}

	private void loadGlobal(JsonObject global) {
		try {
			if (global.has("shapeMode")) shapeMode.setMode(BlockEspShapeMode.valueOf(global.get("shapeMode").getAsString()));
		} catch (IllegalArgumentException ignored) {
		}
		if (global.has("lineColor")) lineColor.setArgb(global.get("lineColor").getAsInt());
		if (global.has("sideColor")) sideColor.setArgb(global.get("sideColor").getAsInt());
		if (global.has("tracers")) tracers.setValue(global.get("tracers").getAsBoolean());
		if (global.has("tracerColor")) tracerColor.setArgb(global.get("tracerColor").getAsInt());
		if (global.has("renderDistance")) renderDistance.setValue(global.get("renderDistance").getAsDouble());
		if (global.has("maxBlocks")) maxBlocks.setValue(global.get("maxBlocks").getAsDouble());
		if (global.has("throughWalls")) throughWalls.setValue(global.get("throughWalls").getAsBoolean());
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
	}

	private static String id(Block block) {
		Identifier identifier = BuiltInRegistries.BLOCK.getKey(block);
		return identifier == null ? "minecraft:air" : identifier.toString();
	}

	private record ChunkOperation(boolean remove, int x, int z) {
	}

	private record ScanResult(int generation, long key, BlockEspChunk chunk) {
	}
}
