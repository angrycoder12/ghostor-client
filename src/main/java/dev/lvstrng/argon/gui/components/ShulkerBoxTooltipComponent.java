package dev.lvstrng.argon.gui.components;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.modules.render.ShulkerBoxTooltip;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** A small native tooltip component; no standalone Shulker Box Tooltip mod code is bundled. */
public final class ShulkerBoxTooltipComponent implements ClientTooltipComponent, TooltipComponent {
	private static final Identifier SLOT_BACKGROUND =
			Identifier.withDefaultNamespace("container/bundle/slot_background");
	private static final int SLOT_SIZE = 24;
	private static final int ITEM_INSET = 4;
	private static final int MAX_COLUMNS = 9;
	private static final int SHULKER_SIZE = 27;

	private final List<PreviewStack> stacks;
	private final int columns;
	private final int rows;

	private ShulkerBoxTooltipComponent(List<PreviewStack> stacks, int columns, int rows) {
		this.stacks = List.copyOf(stacks);
		this.columns = columns;
		this.rows = rows;
	}

	public static void register() {
		ClientTooltipComponentCallback.EVENT.register(component ->
				component instanceof ShulkerBoxTooltipComponent shulker ? shulker : null);
	}

	public static Optional<TooltipComponent> create(ItemStack source) {
		ShulkerBoxTooltip module = getEnabledModule();
		if (module == null || !(source.getItem() instanceof BlockItem blockItem)
				|| !(blockItem.getBlock() instanceof ShulkerBoxBlock)
				|| source.has(DataComponents.CONTAINER_LOOT)) {
			return Optional.empty();
		}

		ItemContainerContents contents = source.get(DataComponents.CONTAINER);
		if (contents == null) {
			return Optional.empty();
		}

		NonNullList<ItemStack> inventory = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
		try {
			contents.copyInto(inventory);
		} catch (RuntimeException malformedContents) {
			return Optional.empty();
		}

		if (inventory.stream().allMatch(ItemStack::isEmpty)) {
			return Optional.empty();
		}

		List<PreviewStack> preview = module.compactMode()
				? mergeStacks(inventory)
				: copySlots(inventory, module.showEmptySlots());
		int slotCount = Math.max(1, preview.size());
		int columns = Math.min(MAX_COLUMNS, slotCount);
		int rows = (slotCount + columns - 1) / columns;
		return Optional.of(new ShulkerBoxTooltipComponent(preview, columns, rows));
	}

	private static ShulkerBoxTooltip getEnabledModule() {
		if (Argon.INSTANCE == null || Argon.INSTANCE.getModuleManager() == null) {
			return null;
		}

		ShulkerBoxTooltip module = Argon.INSTANCE.getModuleManager().getModule(ShulkerBoxTooltip.class);
		return module != null && module.isEnabled() ? module : null;
	}

	private static List<PreviewStack> copySlots(List<ItemStack> inventory, boolean showEmptySlots) {
		int size = inventory.size();
		if (!showEmptySlots) {
			while (size > 0 && inventory.get(size - 1).isEmpty()) {
				size--;
			}
		}

		List<PreviewStack> result = new ArrayList<>(Math.max(1, size));
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = inventory.get(slot);
			result.add(new PreviewStack(stack.copy(), stack.getCount()));
		}
		return result;
	}

	private static List<PreviewStack> mergeStacks(List<ItemStack> inventory) {
		List<PreviewStack> merged = new ArrayList<>();
		for (ItemStack stack : inventory) {
			if (stack.isEmpty()) {
				continue;
			}

			int matchingIndex = -1;
			for (int index = 0; index < merged.size(); index++) {
				if (ItemStack.isSameItemSameComponents(merged.get(index).stack(), stack)) {
					matchingIndex = index;
					break;
				}
			}

			if (matchingIndex >= 0) {
				PreviewStack old = merged.get(matchingIndex);
				merged.set(matchingIndex, new PreviewStack(old.stack(), old.count() + stack.getCount()));
			} else {
				merged.add(new PreviewStack(stack.copyWithCount(1), stack.getCount()));
			}
		}
		merged.sort(Comparator.comparingInt(PreviewStack::count).reversed());
		return merged;
	}

	@Override
	public int getHeight(Font font) {
		return rows * SLOT_SIZE + 4;
	}

	@Override
	public int getWidth(Font font) {
		return columns * SLOT_SIZE;
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height,
			GuiGraphicsExtractor graphics) {
		for (int slot = 0; slot < stacks.size(); slot++) {
			int slotX = x + SLOT_SIZE * (slot % columns);
			int slotY = y + SLOT_SIZE * (slot / columns);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND,
					slotX, slotY, SLOT_SIZE, SLOT_SIZE);

			PreviewStack preview = stacks.get(slot);
			if (!preview.stack().isEmpty()) {
				int itemX = slotX + ITEM_INSET;
				int itemY = slotY + ITEM_INSET;
				graphics.item(preview.stack(), itemX, itemY);
				String count = preview.count() == 1 ? null : Integer.toString(preview.count());
				graphics.itemDecorations(font, preview.stack(), itemX, itemY, count);
			}
		}
	}

	private record PreviewStack(ItemStack stack, int count) {}
}
