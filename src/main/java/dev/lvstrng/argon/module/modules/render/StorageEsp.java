package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.event.events.PacketReceiveListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import java.awt.*;

public final class StorageEsp extends Module implements GameRenderListener, PacketReceiveListener {
	private final NumberSetting alpha = new NumberSetting(EncryptedString.of("Alpha"), 1, 255, 125, 1);
	private final BooleanSetting donutBypass = new BooleanSetting(EncryptedString.of("Donut Bypass"), false);
	private final BooleanSetting tracers = new BooleanSetting(EncryptedString.of("Tracers"), false)
			.setDescription(EncryptedString.of("Draws a line from your player to the storage block"));

	public StorageEsp() {
		super(EncryptedString.of("Storage ESP"),
				EncryptedString.of("Renders storage blocks through walls"),
				-1,
				Category.RENDER);
		addSettings(donutBypass, alpha, tracers);
	}

	@Override
	public void onEnable() {
		eventManager.add(PacketReceiveListener.class, this);
		eventManager.add(GameRenderListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketReceiveListener.class, this);
		eventManager.remove(GameRenderListener.class, this);
		super.onDisable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		renderStorages(event);
	}

	private Color getColor(BlockEntity blockEntity, int a) {
		if (blockEntity instanceof TrappedChestBlockEntity) {
			return new Color(200, 91, 0, a);
		} else if (blockEntity instanceof ChestBlockEntity) {
			return new Color(156, 91, 0, a);
		} else if (blockEntity instanceof EnderChestBlockEntity) {
			return new Color(117, 0, 255, a);
		} else if (blockEntity instanceof SpawnerBlockEntity) {
			return new Color(138, 126, 166, a);
		} else if (blockEntity instanceof ShulkerBoxBlockEntity) {
			return new Color(134, 0, 158, a);
		} else if (blockEntity instanceof FurnaceBlockEntity) {
			return new Color(125, 125, 125, a);
		} else if (blockEntity instanceof BarrelBlockEntity) {
			return new Color(255, 140, 140, a);
		} else if (blockEntity instanceof EnchantingTableBlockEntity) {
			return new Color(80, 80, 255, a);
		} else return new Color(255, 255, 255, 0);
	}

	private void renderStorages(GameRenderEvent event) {
		if (mc.level == null) {
			return;
		}

		for (LevelChunk chunk : WorldUtils.getLoadedChunks().toList()) {
			for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
				BlockEntity blockEntity = mc.level.getBlockEntity(blockPos);
				if (blockEntity == null) {
					continue;
				}

				RenderUtils.renderFilledBox(
						new net.minecraft.world.phys.AABB(blockPos).deflate(0.1D),
						getColor(blockEntity, alpha.getValueInt())
				);

				Vec3 center = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
				if (tracers.getValue() && mc.hitResult != null) {
					RenderUtils.renderLine(
							getColor(blockEntity, 255),
							mc.hitResult.getLocation(),
							center
					);
				}
			}
		}
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (donutBypass.getValue()) {
			if (event.packet instanceof ClientboundSectionBlocksUpdatePacket) {
				event.cancel();
			}
		}
	}
}
