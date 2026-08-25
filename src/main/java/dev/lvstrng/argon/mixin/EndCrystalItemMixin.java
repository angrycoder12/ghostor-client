package dev.lvstrng.argon.mixin;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.render.NoBounce;
import dev.lvstrng.argon.utils.CrystalUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.lvstrng.argon.Argon.mc;

@Mixin(EndCrystalItem.class)
public class EndCrystalItemMixin {

	@Unique
	private Vec3 getPlayerLookVec(Player p) {
		return RenderUtils.getPlayerLookVec(p);
	}

	@Unique
	private Vec3 getClientLookVec() {
		assert mc.player != null;
		return getPlayerLookVec(mc.player);
	}

	@Unique
	private boolean isBlock(Block b, BlockPos p) {
		return (getBlockState(p).getBlock() == b);
	}

	@Unique
	private BlockState getBlockState(BlockPos p) {
		return mc.level.getBlockState(p);
	}

	@Unique
	private boolean canPlaceCrystalServer(BlockPos blockPos) {
		BlockState blockState = mc.level.getBlockState(blockPos);
		if (!blockState.is(Blocks.OBSIDIAN) && !blockState.is(Blocks.BEDROCK))
			return false;
		return CrystalUtils.canPlaceCrystalClientAssumeObsidian(blockPos);
	}

	@Inject(method = "useOn", at = @At("HEAD"))
	private void onUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		NoBounce noBounce = Argon.INSTANCE.getModuleManager().getModule(NoBounce.class);
		if (noBounce.isEnabled()) {
			if (Argon.INSTANCE != null && mc.player != null) {
				ItemStack mainHandStack = mc.player.getMainHandItem();

				if (mainHandStack.is(Items.END_CRYSTAL)) {
					Vec3 e = mc.player.getEyePosition();

					BlockHitResult blockHit = mc.level.clip(new ClipContext(e, e.add(getClientLookVec().scale(4.5)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
					if (isBlock(Blocks.OBSIDIAN, blockHit.getBlockPos()) || isBlock(Blocks.BEDROCK, blockHit.getBlockPos())) {
						HitResult hitResult = mc.hitResult;

						if (hitResult instanceof BlockHitResult blockHit2) {
							BlockPos pos = blockHit2.getBlockPos();

							if (canPlaceCrystalServer(pos))
								context.getItemInHand().shrink(-1);
						}
					}
				}
			}
		}
	}
}
