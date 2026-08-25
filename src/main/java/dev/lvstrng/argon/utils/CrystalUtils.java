package dev.lvstrng.argon.utils;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import static dev.lvstrng.argon.Argon.mc;

public final class CrystalUtils {
	public static boolean canPlaceCrystalClient(BlockPos block) {
		BlockState blockState = mc.level.getBlockState(block);
		if (!blockState.is(Blocks.OBSIDIAN) && !blockState.is(Blocks.BEDROCK))
			return false;

		return canPlaceCrystalClientAssumeObsidian(block);
	}

	public static boolean canPlaceCrystalClientAssumeObsidian(BlockPos block) {
		BlockPos blockPos2 = block.above();
		if (!mc.level.isEmptyBlock(blockPos2))
			return false;

		double d = blockPos2.getX();
		double e = blockPos2.getY();
		double f = blockPos2.getZ();

		List<Entity> list = mc.level.getEntities(null, new AABB(d, e, f, d + 1.0D, e + 2.0D, f + 1.0D));
		return list.isEmpty();
	}

	public static boolean canPlaceCrystalServer(BlockPos pos) {
		BlockState blockState = mc.level.getBlockState(pos);
		if (!blockState.is(Blocks.OBSIDIAN) || !blockState.is(Blocks.BEDROCK))
			return false;

		BlockPos blockPos = pos.above();
		if (!mc.level.isEmptyBlock(blockPos))
			return false;

		double d = blockPos.getX();
		double e = blockPos.getY();
		double f = blockPos.getZ();

		List<Entity> list = mc.level.getEntities(null, new AABB(d, e, f, d + 1, e + 2, f + 1));
		return list.isEmpty();
	}
}
