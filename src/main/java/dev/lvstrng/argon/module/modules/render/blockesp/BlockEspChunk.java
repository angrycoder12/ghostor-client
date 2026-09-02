package dev.lvstrng.argon.module.modules.render.blockesp;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import java.util.function.Predicate;

/** Match cache for one loaded chunk. */
public final class BlockEspChunk {
	private final ChunkPos pos;
	private final Long2ObjectMap<BlockEspEntry> entries = new Long2ObjectOpenHashMap<>(64);

	public BlockEspChunk(ChunkPos pos) {
		this.pos = pos;
	}

	public ChunkPos pos() {
		return pos;
	}

	public Long2ObjectMap<BlockEspEntry> entries() {
		return entries;
	}

	public int size() {
		return entries.size();
	}

	public BlockEspEntry get(BlockPos pos) {
		return entries.get(pos.asLong());
	}

	public void put(BlockPos pos, BlockState state) {
		BlockEspEntry existing = entries.get(pos.asLong());
		if (existing == null) entries.put(pos.asLong(), new BlockEspEntry(pos, state));
		else existing.update(state);
	}

	public boolean remove(BlockPos pos) {
		return entries.remove(pos.asLong()) != null;
	}

	public static BlockEspChunk scan(LevelChunk chunk, Predicate<BlockState> matcher, int resultLimit) {
		BlockEspChunk result = new BlockEspChunk(chunk.getPos());
		if (resultLimit <= 0) return result;
		LevelChunkSection[] sections = chunk.getSections();
		int minY = chunk.getMinY();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();

		for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
			LevelChunkSection section = sections[sectionIndex];
			if (Thread.currentThread().isInterrupted()) return result;
			if (section == null || section.hasOnlyAir()) continue;
			section.acquire();
			try {
				if (!section.maybeHas(matcher)) continue;
				int baseY = minY + sectionIndex * 16;
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							BlockState state = section.getBlockState(x, y, z);
							if (!matcher.test(state)) continue;
							BlockPos pos = new BlockPos(minX + x, baseY + y, minZ + z);
							result.put(pos, state);
							if (result.size() >= resultLimit) return result;
						}
					}
				}
			} finally {
				section.release();
			}
		}
		return result;
	}
}
