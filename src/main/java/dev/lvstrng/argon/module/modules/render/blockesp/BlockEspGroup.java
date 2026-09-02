package dev.lvstrng.argon.module.modules.render.blockesp;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** Adjacent same-type matches share one tracer aimed at their center. */
public final class BlockEspGroup {
	private final Block block;
	private final List<BlockEspEntry> entries = new ArrayList<>();
	private double sumX;
	private double sumY;
	private double sumZ;

	public BlockEspGroup(Block block) {
		this.block = block;
	}

	public void add(BlockEspEntry entry) {
		entries.add(entry);
		sumX += entry.pos().getX() + 0.5;
		sumY += entry.pos().getY() + 0.5;
		sumZ += entry.pos().getZ() + 0.5;
	}

	public Block block() {
		return block;
	}

	public List<BlockEspEntry> entries() {
		return entries;
	}

	public Vec3 center() {
		int count = Math.max(1, entries.size());
		return new Vec3(sumX / count, sumY / count, sumZ / count);
	}
}
