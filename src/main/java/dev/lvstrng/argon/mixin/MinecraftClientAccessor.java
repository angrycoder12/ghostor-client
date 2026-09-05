package dev.lvstrng.argon.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
	@Accessor
	MouseHandler getMouseHandler();

	@Invoker
	void invokeStartUseItem();

	@Invoker
	boolean invokeStartAttack();

	@Accessor("rightClickDelay")
	void setItemUseCooldown(int cooldown);

	@Accessor("rightClickDelay")
	int getItemUseCooldown();
}
