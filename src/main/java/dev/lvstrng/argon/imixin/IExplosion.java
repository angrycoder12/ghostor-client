package dev.lvstrng.argon.imixin;

import net.minecraft.world.phys.Vec3;

public interface IExplosion {
	void set(Vec3 pos, float power, boolean createFire);
}
