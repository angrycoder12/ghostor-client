package dev.lvstrng.argon.gui.components;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.render.MapTooltip;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;

public final class MapTooltipComponent implements ClientTooltipComponent, TooltipComponent {
	private static final Identifier MAP_BACKGROUND =
			Identifier.withDefaultNamespace("textures/map/map_background.png");
	private static final int SIZE = 66;
	private static final int MAP_INSET = 4;
	private static final int Y_OFFSET = -2;
	private static final float MAP_SCALE = 0.45F;

	private final MapId mapId;

	private MapTooltipComponent(MapId mapId) {
		this.mapId = mapId;
	}

	public static void register() {
		ClientTooltipComponentCallback.EVENT.register(component ->
				component instanceof MapTooltipComponent mapTooltip ? mapTooltip : null);
	}

	public static boolean shouldShow() {
		if (Argon.INSTANCE == null || Argon.INSTANCE.getModuleManager() == null) {
			return false;
		}

		MapTooltip module = Argon.INSTANCE.getModuleManager().getModule(MapTooltip.class);
		return module != null && module.isEnabled();
	}

	public static Optional<TooltipComponent> create(ItemStack stack) {
		if (!shouldShow()) {
			return Optional.empty();
		}

		MapId mapId = stack.get(DataComponents.MAP_ID);
		return mapId == null
				? Optional.empty()
				: Optional.of(new MapTooltipComponent(mapId));
	}

	@Override
	public int getHeight(Font font) {
		return SIZE;
	}

	@Override
	public int getWidth(Font font) {
		return SIZE;
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		MapItemSavedData mapData = minecraft.level.getMapData(mapId);
		if (mapData == null) {
			return;
		}

		int previewX = x;
		int previewY = y + Y_OFFSET;
		graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_BACKGROUND,
				previewX, previewY, 0, 0, SIZE, SIZE, SIZE, SIZE);

		graphics.pose().pushMatrix();
		try {
			graphics.pose().translate(previewX + MAP_INSET, previewY + MAP_INSET);
			graphics.pose().scale(MAP_SCALE, MAP_SCALE);

			MapRenderState renderState = new MapRenderState();
			minecraft.getMapRenderer().extractRenderState(mapId, mapData, renderState);
			graphics.map(renderState);
		} finally {
			graphics.pose().popMatrix();
		}
	}
}
