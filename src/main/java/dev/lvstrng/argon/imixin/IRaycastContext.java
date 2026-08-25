package dev.lvstrng.argon.imixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

public interface IRaycastContext {
	void set(Vec3 start, Vec3 end, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling, Entity entity);
}
