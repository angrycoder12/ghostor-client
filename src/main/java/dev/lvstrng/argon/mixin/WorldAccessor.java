package dev.lvstrng.argon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

@Mixin(value = {Level.class})
public interface WorldAccessor {
	@Accessor("blockEntityTickers")
	List<TickingBlockEntity> getBlockEntityTickers();
}
