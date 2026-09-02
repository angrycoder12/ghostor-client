package dev.lvstrng.argon.module.modules.render.blockesp;

import dev.lvstrng.argon.module.modules.render.BlockESP;
import java.awt.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One cached world match. Rendering resolves current global/per-block settings. */
public final class BlockEspEntry {
	private final BlockPos pos;
	private BlockState state;

	public BlockEspEntry(BlockPos pos, BlockState state) {
		this.pos = pos.immutable();
		this.state = state;
	}

	public BlockPos pos() {
		return pos;
	}

	public Block block() {
		return state.getBlock();
	}

	public void update(BlockState state) {
		this.state = state;
	}

	public void render(BlockESP module) {
		if (module.minecraftLevel() == null) return;
		BlockEspBlockData data = module.resolveData(block());
		AABB box = new AABB(pos);
		try {
			VoxelShape shape = state.getShape(module.minecraftLevel(), pos);
			if (!shape.isEmpty()) box = shape.bounds().move(pos);
		} catch (RuntimeException ignored) {
			// A malformed/dynamic state should not take down the world renderer.
		}

		if (data.shapeMode == BlockEspShapeMode.Box || data.shapeMode == BlockEspShapeMode.Both) {
			add(Gizmos.cuboid(box, GizmoStyle.fill(color(data.sideColor))), module.throughWalls());
		}
		if (data.shapeMode == BlockEspShapeMode.Lines || data.shapeMode == BlockEspShapeMode.Both) {
			add(Gizmos.cuboid(box.inflate(0.002), GizmoStyle.stroke(color(data.lineColor), 1.25F)),
					module.throughWalls());
		}
	}

	private static int color(int argb) {
		return new Color(argb, true).getRGB();
	}

	private static void add(GizmoProperties properties, boolean throughWalls) {
		if (throughWalls) properties.setAlwaysOnTop();
	}
}
