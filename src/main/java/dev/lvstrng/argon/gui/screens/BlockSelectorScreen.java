package dev.lvstrng.argon.gui.screens;

import dev.lvstrng.argon.gui.ClickGui;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.gui.components.GhostorIcons;
import dev.lvstrng.argon.gui.layout.GuiBounds;
import dev.lvstrng.argon.module.modules.render.BlockESP;
import dev.lvstrng.argon.module.modules.render.blockesp.BlockEspBlockData;
import dev.lvstrng.argon.module.modules.render.blockesp.BlockEspBlockData.ColorTarget;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

/** Ghostor-native block selector popout and per-block override editor. */
public final class BlockSelectorScreen {
	private static final int MARGIN = 18;
	private static final int ROW_HEIGHT = 46;
	private static final int ROW_GAP = 5;
	private static final int SEARCH_HEIGHT = 40;
	private static final int FOOTER_HEIGHT = 66;
	private static final int COLOR_HEIGHT = 54;

	private final ClickGui parent;
	private final BlockESP module;
	private final GuiBounds bounds;
	private final List<Block> allBlocks;
	private List<Block> filteredBlocks;
	private String search = "";
	private boolean searchFocused;
	private Block focusedBlock;
	private double listScroll;
	private double detailScroll;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int listX;
	private int listY;
	private int listWidth;
	private int listBottom;
	private int detailX;
	private int detailY;
	private int detailWidth;
	private int detailBottom;
	private ColorTarget draggingTarget;
	private int draggingChannel = -1;

	public BlockSelectorScreen(ClickGui parent, BlockESP module) {
		this.parent = parent;
		this.module = module;
		this.bounds = parent.blockSelectorBounds();
		this.allBlocks = BuiltInRegistries.BLOCK.stream()
				.filter(block -> block.asItem() != Items.AIR)
				.sorted(Comparator.comparing(block -> block.getName().getString().toLowerCase(Locale.ROOT)))
				.toList();
		this.filteredBlocks = allBlocks;
	}

	public void render(GuiGraphicsExtractor context, int physicalMouseX, int physicalMouseY, float delta) {
		layout();
		renderPanel(context, physicalMouseX, physicalMouseY);
		boolean resizeHovered = bounds.isOverResizeHandle(
				physicalMouseX, physicalMouseY, GhostorTheme.RESIZE_HANDLE);
		GhostorTheme.resizeHandle(context, bounds.right(), bounds.bottom(),
				resizeHovered || bounds.isResizing());
	}

	private void layout() {
		Minecraft minecraft = Minecraft.getInstance();
		int screenWidth = minecraft.getWindow().getWidth();
		int screenHeight = minecraft.getWindow().getHeight();
		bounds.clampToScreen(screenWidth, screenHeight, GhostorTheme.PANEL_MARGIN);
		panelX = bounds.x();
		panelY = bounds.y();
		panelWidth = bounds.width();
		panelHeight = bounds.height();

		int contentWidth = panelWidth - 40;
		listWidth = Math.max(300, (int) (contentWidth * 0.61));
		int gap = 14;
		detailWidth = contentWidth - listWidth - gap;
		if (detailWidth < 255) {
			detailWidth = 255;
			listWidth = contentWidth - detailWidth - gap;
		}
		listX = panelX + 20;
		detailX = listX + listWidth + gap;
		listY = panelY + 132;
		detailY = listY;
		listBottom = panelY + panelHeight - FOOTER_HEIGHT - 12;
		detailBottom = listBottom;
		clampScroll();
	}

	private void renderPanel(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		GhostorTheme.glowOutline(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight,
				GhostorTheme.RADIUS);
		GhostorTheme.panel(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight,
				GhostorTheme.SURFACE_GLASS, GhostorTheme.RADIUS);
		GhostorTheme.outline(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight,
				GhostorTheme.ACCENT_BORDER, GhostorTheme.RADIUS);

		TextRenderer.drawString("Select Blocks", context, panelX + 20, panelY + 22, GhostorTheme.TEXT.getRGB());
		TextRenderer.drawSmallString("Choose blocks to highlight", context, panelX + 20, panelY + 44,
				GhostorTheme.TEXT_MUTED.getRGB());
		renderSearch(context, mouseX, mouseY);
		renderModeTabs(context, mouseX, mouseY);
		renderBlockList(context, mouseX, mouseY);
		renderDetails(context, mouseX, mouseY);
		renderFooter(context, mouseX, mouseY);
	}

	private void renderSearch(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int y = panelY + 76;
		boolean hovered = hovered(mouseX, mouseY, listX, y, listX + listWidth, y + SEARCH_HEIGHT);
		GhostorTheme.panel(context, listX, y, listX + listWidth, y + SEARCH_HEIGHT,
				searchFocused || hovered ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 8);
		GhostorTheme.outline(context, listX, y, listX + listWidth, y + SEARCH_HEIGHT,
				searchFocused ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 8);
		String label = search.isBlank() ? "Search blocks..." : fit(search, listWidth - 58);
		TextRenderer.drawSmallString(label, context, listX + 14, y + 15,
				search.isBlank() ? GhostorTheme.DISABLED.getRGB() : GhostorTheme.TEXT.getRGB());
		GhostorIcons.search(context, listX + listWidth - 29, y + 11,
				searchFocused ? GhostorTheme.ACCENT_HOVER : GhostorTheme.TEXT_MUTED);
	}

	private void renderModeTabs(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int y = panelY + 76;
		int gap = 8;
		int tabWidth = (detailWidth - gap) / 2;
		renderTab(context, mouseX, mouseY, detailX, y, tabWidth, "Whitelist", !module.isBlacklist());
		renderTab(context, mouseX, mouseY, detailX + tabWidth + gap, y, detailWidth - tabWidth - gap,
				"Blacklist", module.isBlacklist());
	}

	private void renderTab(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width,
			String text, boolean selected) {
		boolean over = hovered(mouseX, mouseY, x, y, x + width, y + SEARCH_HEIGHT);
		GhostorTheme.panel(context, x, y, x + width, y + SEARCH_HEIGHT,
				selected ? GhostorTheme.ACCENT : over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 7);
		GhostorTheme.outline(context, x, y, x + width, y + SEARCH_HEIGHT,
				selected ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 7);
		TextRenderer.drawCenteredString(text, context, x + width / 2, y + 12,
				(selected ? GhostorTheme.TEXT : GhostorTheme.TEXT_MUTED).getRGB());
	}

	private void renderBlockList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		GhostorTheme.panel(context, listX, listY, listX + listWidth, listBottom,
				GhostorTheme.SURFACE, 9);
		GhostorTheme.outline(context, listX, listY, listX + listWidth, listBottom,
				GhostorTheme.BORDER, 9);
		context.enableScissor(listX + 1, listY + 1, listX + listWidth - 1, listBottom - 1);
		int contentY = listY + 7 - (int) listScroll;
		for (Block block : filteredBlocks) {
			int rowY = contentY;
			contentY += ROW_HEIGHT + ROW_GAP;
			if (rowY + ROW_HEIGHT < listY || rowY > listBottom) continue;
			boolean over = hovered(mouseX, mouseY, listX + 7, rowY, listX + listWidth - 12, rowY + ROW_HEIGHT);
			boolean listed = module.isListed(block);
			boolean focused = block == focusedBlock;
			GhostorTheme.panel(context, listX + 7, rowY, listX + listWidth - 12, rowY + ROW_HEIGHT,
					focused ? new Color(35, 30, 63, 248) : over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 7);
			GhostorTheme.outline(context, listX + 7, rowY, listX + listWidth - 12, rowY + ROW_HEIGHT,
					focused || listed ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 7);
			context.item(new ItemStack(block), listX + 16, rowY + 14);
			TextRenderer.drawString(fit(block.getName().getString(), listWidth - 120), context,
					listX + 44, rowY + 14, GhostorTheme.TEXT.getRGB());
			renderToggle(context, listX + listWidth - 54, rowY + 13, listed);
		}
		context.disableScissor();

		double maximum = maxListScroll();
		if (maximum > 0) {
			int trackTop = listY + 7;
			int trackHeight = listBottom - listY - 14;
			int thumbHeight = Math.max(28, (int) (trackHeight * (trackHeight / (double) listContentHeight())));
			int thumbY = trackTop + (int) ((trackHeight - thumbHeight) * (listScroll / maximum));
			GhostorTheme.panel(context, listX + listWidth - 7, thumbY, listX + listWidth - 4,
					thumbY + thumbHeight, GhostorTheme.ACCENT_BORDER, 2);
		}
	}

	private void renderDetails(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		GhostorTheme.panel(context, detailX, detailY, detailX + detailWidth, detailBottom,
				GhostorTheme.SURFACE, 9);
		GhostorTheme.outline(context, detailX, detailY, detailX + detailWidth, detailBottom,
				GhostorTheme.BORDER, 9);
		if (focusedBlock == null) {
			TextRenderer.drawCenteredString("Right-click a block", context, detailX + detailWidth / 2,
					detailY + 88, GhostorTheme.TEXT.getRGB());
			TextRenderer.drawSmallString("to edit individual settings", context,
					detailX + (detailWidth - TextRenderer.getSmallWidth("to edit individual settings")) / 2,
					detailY + 112, GhostorTheme.TEXT_MUTED.getRGB());
			return;
		}

		context.enableScissor(detailX + 1, detailY + 1, detailX + detailWidth - 1, detailBottom - 1);
		int y = detailY + 20 - (int) detailScroll;
		ItemStack icon = new ItemStack(focusedBlock);
		context.pose().pushMatrix();
		context.pose().translate(detailX + detailWidth / 2 - 24, y);
		context.pose().scale(3.0F, 3.0F);
		context.item(icon, 0, 0);
		context.pose().popMatrix();
		y += 62;
		String displayName = focusedBlock.getName().getString();
		TextRenderer.drawCenteredString(fit(displayName, detailWidth - 28), context,
				detailX + detailWidth / 2, y, GhostorTheme.TEXT.getRGB());
		y += 24;
		String identifier = id(focusedBlock);
		TextRenderer.drawSmallString(fit(identifier, detailWidth - 28), context, detailX + 14, y,
				GhostorTheme.TEXT_MUTED.getRGB());
		y += 30;
		context.fill(detailX + 14, y, detailX + detailWidth - 14, y + 1, GhostorTheme.BORDER.getRGB());
		y += 18;
		TextRenderer.drawSmallString("Type: " + module.blockCategory(focusedBlock), context,
				detailX + 14, y, GhostorTheme.TEXT_MUTED.getRGB());
		y += 24;
		boolean rendered = module.isRendered(focusedBlock);
		TextRenderer.drawSmallString("Status: ", context, detailX + 14, y, GhostorTheme.TEXT_MUTED.getRGB());
		TextRenderer.drawSmallString(rendered ? "Enabled" : "Disabled", context,
				detailX + 14 + TextRenderer.getSmallWidth("Status: "), y,
				(rendered ? GhostorTheme.SUCCESS : GhostorTheme.DANGER).getRGB());
		y += 32;

		if (!module.isListed(focusedBlock)) {
			TextRenderer.drawSmallString(module.isBlacklist()
						? "This block is not excluded. Toggle it to edit saved overrides."
						: "Enable this block to edit individual settings.",
					context, detailX + 14, y, GhostorTheme.TEXT_MUTED.getRGB());
			context.disableScissor();
			return;
		}

		BlockEspBlockData data = module.customData(focusedBlock);
		renderSettingToggle(context, mouseX, mouseY, y, "Use Custom Settings", data.useCustomSettings,
				true);
		y += 44;
		renderModeSetting(context, mouseX, mouseY, y, data);
		y += 44;
		renderColorEditor(context, y, "Line Color", data.lineColor, data.useCustomSettings);
		y += COLOR_HEIGHT;
		renderColorEditor(context, y, "Box Color", data.sideColor, data.useCustomSettings);
		y += COLOR_HEIGHT;
		renderSettingToggle(context, mouseX, mouseY, y, "Tracer", data.tracer, data.useCustomSettings);
		y += 44;
		renderColorEditor(context, y, "Tracer Color", data.tracerColor, data.useCustomSettings);
		context.disableScissor();

		double maximum = maxDetailScroll();
		if (maximum > 0) {
			int trackTop = detailY + 8;
			int trackHeight = detailBottom - detailY - 16;
			int thumbHeight = Math.max(28, (int) (trackHeight * (trackHeight / (double) detailContentHeight())));
			int thumbY = trackTop + (int) ((trackHeight - thumbHeight) * (detailScroll / maximum));
			GhostorTheme.panel(context, detailX + detailWidth - 7, thumbY,
					detailX + detailWidth - 4, thumbY + thumbHeight, GhostorTheme.ACCENT_BORDER, 2);
		}
	}

	private void renderSettingToggle(GuiGraphicsExtractor context, int mouseX, int mouseY, int y,
			String name, boolean value, boolean enabled) {
		Color labelColor = enabled ? GhostorTheme.TEXT : GhostorTheme.DISABLED;
		TextRenderer.drawSmallString(name, context, detailX + 14, y + 13, labelColor.getRGB());
		renderToggle(context, detailX + detailWidth - 52, y + 8, value && enabled);
	}

	private void renderModeSetting(GuiGraphicsExtractor context, int mouseX, int mouseY, int y,
			BlockEspBlockData data) {
		TextRenderer.drawSmallString("ESP Type", context, detailX + 14, y + 13,
				(data.useCustomSettings ? GhostorTheme.TEXT : GhostorTheme.DISABLED).getRGB());
		String mode = data.shapeMode.name();
		int width = TextRenderer.getSmallWidth(mode) + 18;
		GhostorTheme.panel(context, detailX + detailWidth - width - 14, y + 7,
				detailX + detailWidth - 14, y + 35,
				data.useCustomSettings ? GhostorTheme.ACCENT_SOFT : GhostorTheme.SURFACE_ELEVATED, 5);
		TextRenderer.drawSmallString(mode, context, detailX + detailWidth - width - 5, y + 13,
				(data.useCustomSettings ? GhostorTheme.ACCENT_HOVER : GhostorTheme.DISABLED).getRGB());
	}

	private void renderColorEditor(GuiGraphicsExtractor context, int y, String name, int packed,
			boolean enabled) {
		Color color = new Color(packed, true);
		Color textColor = enabled ? GhostorTheme.TEXT : GhostorTheme.DISABLED;
		TextRenderer.drawSmallString(name, context, detailX + 14, y + 7, textColor.getRGB());
		String hex = String.format("#%08X", packed);
		TextRenderer.drawSmallString(hex, context,
				detailX + detailWidth - 43 - TextRenderer.getSmallWidth(hex), y + 7,
				GhostorTheme.TEXT_MUTED.getRGB());
		GhostorTheme.panel(context, detailX + detailWidth - 36, y + 4,
				detailX + detailWidth - 16, y + 21, color, 4);
		int[] values = {color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
		Color[] channelColors = {GhostorTheme.DANGER, GhostorTheme.SUCCESS,
				new Color(92, 142, 255), GhostorTheme.TEXT_MUTED};
		int startX = detailX + 14;
		int totalWidth = detailWidth - 28;
		int gap = 5;
		int channelWidth = (totalWidth - gap * 3) / 4;
		for (int channel = 0; channel < 4; channel++) {
			int x = startX + channel * (channelWidth + gap);
			TextRenderer.drawSmallString("RGBA".substring(channel, channel + 1), context, x, y + 30,
					(enabled ? channelColors[channel] : GhostorTheme.DISABLED).getRGB());
			int trackX = x + 11;
			int trackWidth = Math.max(4, channelWidth - 11);
			context.fill(trackX, y + 34, trackX + trackWidth, y + 37, GhostorTheme.DISABLED.getRGB());
			if (enabled) context.fill(trackX, y + 34,
					trackX + Math.max(1, trackWidth * values[channel] / 255), y + 37,
					channelColors[channel].getRGB());
		}
	}

	private void renderFooter(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int y = panelY + panelHeight - 50;
		renderButton(context, mouseX, mouseY, panelX + 20, y, 86, "Back", false);
		int doneWidth = 86;
		int clearWidth = 138;
		renderButton(context, mouseX, mouseY, panelX + panelWidth - doneWidth - 20, y,
				doneWidth, "Done", false);
		renderButton(context, mouseX, mouseY,
				panelX + panelWidth - doneWidth - clearWidth - 30, y,
				clearWidth, "Clear Selected", true);
		String count = module.selectedCount() + (module.selectedCount() == 1 ? " selected" : " selected");
		TextRenderer.drawSmallString(count, context, panelX + 122, y + 14, GhostorTheme.TEXT_MUTED.getRGB());
	}

	private void renderButton(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y,
			int width, String text, boolean danger) {
		boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 36);
		Color color = danger
				? (over ? GhostorTheme.DANGER_HOVER : new Color(142, 45, 58, 238))
				: (over ? GhostorTheme.ACCENT_HOVER : GhostorTheme.ACCENT);
		GhostorTheme.panel(context, x, y, x + width, y + 36, color, 7);
		GhostorTheme.outline(context, x, y, x + width, y + 36,
				danger ? GhostorTheme.DANGER_HOVER : GhostorTheme.ACCENT_BORDER, 7);
		TextRenderer.drawCenteredString(text, context, x + width / 2, y + 11, GhostorTheme.TEXT.getRGB());
	}

	private void renderToggle(GuiGraphicsExtractor context, int x, int y, boolean enabled) {
		GhostorTheme.panel(context, x, y, x + 36, y + 20,
				enabled ? GhostorTheme.ACCENT : new Color(65, 76, 96), 10);
		RenderUtils.renderCircle(context, Color.WHITE, x + (enabled ? 26 : 10), y + 10, 7, 16);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!bounds.contains(mouseX, mouseY)) {
			searchFocused = false;
			return false;
		}
		int searchY = panelY + 76;

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& bounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE)) {
			searchFocused = false;
			bounds.beginResize(mouseX, mouseY);
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && bounds.isOverHeader(mouseX, mouseY, 66)) {
			searchFocused = false;
			bounds.beginDrag(mouseX, mouseY);
			return true;
		}

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& hovered(mouseX, mouseY, listX, searchY, listX + listWidth, searchY + SEARCH_HEIGHT)) {
			searchFocused = true;
			return true;
		}
		searchFocused = false;

		int tabGap = 8;
		int tabWidth = (detailWidth - tabGap) / 2;
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& hovered(mouseX, mouseY, detailX, searchY, detailX + tabWidth, searchY + SEARCH_HEIGHT)) {
			module.setBlacklist(false);
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& hovered(mouseX, mouseY, detailX + tabWidth + tabGap, searchY,
				detailX + detailWidth, searchY + SEARCH_HEIGHT)) {
			module.setBlacklist(true);
			return true;
		}

		Block rowBlock = blockAt(mouseX, mouseY);
		if (rowBlock != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				focusedBlock = rowBlock;
				detailScroll = 0;
				return true;
			}
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				module.toggleListed(rowBlock);
				if (focusedBlock == null) focusedBlock = rowBlock;
				return true;
			}
		}

		int footerY = panelY + panelHeight - 50;
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& hovered(mouseX, mouseY, panelX + 20, footerY, panelX + 106, footerY + 36)) {
			close();
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& hovered(mouseX, mouseY, panelX + panelWidth - 106, footerY,
				panelX + panelWidth - 20, footerY + 36)) {
			close();
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& hovered(mouseX, mouseY, panelX + panelWidth - 254, footerY,
				panelX + panelWidth - 116, footerY + 36)) {
			module.clearSelected();
			return true;
		}

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && handleDetailClick(mouseX, mouseY)) return true;
		return true;
	}

	private boolean handleDetailClick(double mouseX, double mouseY) {
		if (focusedBlock == null || !module.isListed(focusedBlock)
				|| !hovered(mouseX, mouseY, detailX, detailY, detailX + detailWidth, detailBottom)) return false;
		BlockEspBlockData data = module.customData(focusedBlock);
		int controlsY = detailY + 210 - (int) detailScroll;
		if (hovered(mouseX, mouseY, detailX + 8, controlsY, detailX + detailWidth - 8, controlsY + 44)) {
			data.useCustomSettings = !data.useCustomSettings;
			module.markConfigChanged(false);
			return true;
		}
		controlsY += 44;
		if (data.useCustomSettings && hovered(mouseX, mouseY, detailX + 8, controlsY,
				detailX + detailWidth - 8, controlsY + 44)) {
			data.shapeMode = switch (data.shapeMode) {
				case Lines -> dev.lvstrng.argon.module.modules.render.blockesp.BlockEspShapeMode.Box;
				case Box -> dev.lvstrng.argon.module.modules.render.blockesp.BlockEspShapeMode.Both;
				case Both -> dev.lvstrng.argon.module.modules.render.blockesp.BlockEspShapeMode.Lines;
			};
			module.markConfigChanged(false);
			return true;
		}
		controlsY += 44;
		if (data.useCustomSettings && beginColorDrag(mouseX, mouseY, controlsY, ColorTarget.Line)) return true;
		controlsY += COLOR_HEIGHT;
		if (data.useCustomSettings && beginColorDrag(mouseX, mouseY, controlsY, ColorTarget.Side)) return true;
		controlsY += COLOR_HEIGHT;
		if (data.useCustomSettings && hovered(mouseX, mouseY, detailX + 8, controlsY,
				detailX + detailWidth - 8, controlsY + 44)) {
			data.tracer = !data.tracer;
			module.markConfigChanged(false);
			return true;
		}
		controlsY += 44;
		return data.useCustomSettings && beginColorDrag(mouseX, mouseY, controlsY, ColorTarget.Tracer);
	}

	private boolean beginColorDrag(double mouseX, double mouseY, int y, ColorTarget target) {
		if (!hovered(mouseX, mouseY, detailX + 8, y + 24, detailX + detailWidth - 8, y + 43)) return false;
		draggingTarget = target;
		draggingChannel = colorChannelAt(mouseX);
		updateDraggedColor(mouseX);
		return true;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button) {
		if (bounds.isInteracting()) {
			Minecraft minecraft = Minecraft.getInstance();
			bounds.update(mouseX, mouseY,
					minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(),
					GhostorTheme.PANEL_MARGIN);
			layout();
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingTarget != null) {
			updateDraggedColor(mouseX);
			return true;
		}
		return false;
	}

	public boolean mouseReleased(int button) {
		if (bounds.isInteracting()) {
			if (bounds.endInteraction()) parent.saveLayout();
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingTarget != null) {
			draggingTarget = null;
			draggingChannel = -1;
			module.saveConfig();
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		if (!bounds.contains(mouseX, mouseY)) return false;
		if (hovered(mouseX, mouseY, listX, listY, listX + listWidth, listBottom)) {
			listScroll = Mth.clamp(listScroll - verticalAmount * 42.0, 0.0, maxListScroll());
			return true;
		}
		if (hovered(mouseX, mouseY, detailX, detailY, detailX + detailWidth, detailBottom)) {
			detailScroll = Mth.clamp(detailScroll - verticalAmount * 42.0, 0.0, maxDetailScroll());
			return true;
		}
		return true;
	}

	public boolean keyPressed(KeyEvent keyInput) {
		if (keyInput.key() == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		if (searchFocused) {
			if (keyInput.key() == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
				int end = search.offsetByCodePoints(search.length(), -1);
				search = search.substring(0, end);
				refreshFilter();
			}
			return true;
		}
		return false;
	}

	public boolean charTyped(CharacterEvent charInput) {
		if (searchFocused && charInput.isAllowedChatCharacter() && search.length() < 64) {
			search += charInput.codepointAsString();
			refreshFilter();
			return true;
		}
		return false;
	}

	private void close() {
		module.saveConfig();
		bounds.endInteraction();
		parent.saveLayout();
		draggingTarget = null;
		draggingChannel = -1;
		searchFocused = false;
		parent.closeBlockSelector();
	}

	public void onParentClosing() {
		module.saveConfig();
		bounds.endInteraction();
		draggingTarget = null;
		draggingChannel = -1;
		searchFocused = false;
	}

	public boolean endPointerInteraction() {
		boolean changed = bounds.endInteraction();
		if (draggingTarget != null) {
			draggingTarget = null;
			draggingChannel = -1;
			module.saveConfig();
		}
		return changed;
	}

	public boolean loseFocus() {
		searchFocused = false;
		return endPointerInteraction();
	}

	public void clearInputFocus() {
		searchFocused = false;
	}

	public boolean isInputFocused() {
		return searchFocused;
	}

	public boolean isFor(BlockESP candidate) {
		return module == candidate;
	}

	private void refreshFilter() {
		String query = search.strip().toLowerCase(Locale.ROOT);
		filteredBlocks = query.isEmpty() ? allBlocks : allBlocks.stream()
				.filter(block -> block.getName().getString().toLowerCase(Locale.ROOT).contains(query)
						|| id(block).contains(query))
				.toList();
		listScroll = 0;
	}

	private Block blockAt(double mouseX, double mouseY) {
		if (!hovered(mouseX, mouseY, listX + 7, listY, listX + listWidth - 12, listBottom)) return null;
		int relative = (int) (mouseY - listY - 7 + listScroll);
		int index = relative / (ROW_HEIGHT + ROW_GAP);
		int inside = relative % (ROW_HEIGHT + ROW_GAP);
		return index >= 0 && index < filteredBlocks.size() && inside < ROW_HEIGHT
				? filteredBlocks.get(index) : null;
	}

	private int colorChannelAt(double mouseX) {
		int startX = detailX + 14;
		int totalWidth = detailWidth - 28;
		int gap = 5;
		int channelWidth = (totalWidth - gap * 3) / 4;
		return Mth.clamp((int) ((mouseX - startX) / (channelWidth + gap)), 0, 3);
	}

	private void updateDraggedColor(double mouseX) {
		if (draggingTarget == null || draggingChannel < 0 || focusedBlock == null) return;
		int startX = detailX + 14;
		int totalWidth = detailWidth - 28;
		int gap = 5;
		int channelWidth = (totalWidth - gap * 3) / 4;
		int channelX = startX + draggingChannel * (channelWidth + gap) + 11;
		int trackWidth = Math.max(1, channelWidth - 11);
		double progress = Mth.clamp((mouseX - channelX) / trackWidth, 0.0, 1.0);
		module.customData(focusedBlock).setColorChannel(draggingTarget, draggingChannel,
				(int) Math.round(progress * 255.0));
		module.markConfigChanged(false);
	}

	private void clampScroll() {
		listScroll = Mth.clamp(listScroll, 0.0, maxListScroll());
		detailScroll = Mth.clamp(detailScroll, 0.0, maxDetailScroll());
	}

	private int listContentHeight() {
		return 14 + filteredBlocks.size() * (ROW_HEIGHT + ROW_GAP);
	}

	private double maxListScroll() {
		return Math.max(0, listContentHeight() - (listBottom - listY));
	}

	private int detailContentHeight() {
		return 210 + 44 + 44 + COLOR_HEIGHT * 3 + 44 + 18;
	}

	private double maxDetailScroll() {
		return focusedBlock == null ? 0 : Math.max(0, detailContentHeight() - (detailBottom - detailY));
	}

	private static String id(Block block) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		return id == null ? "minecraft:air" : id.toString();
	}

	private static boolean hovered(double mouseX, double mouseY, double x1, double y1, double x2, double y2) {
		return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
	}

	private static String fit(String value, int maxWidth) {
		if (TextRenderer.getSmallWidth(value) <= maxWidth) return value;
		String result = value;
		while (!result.isEmpty() && TextRenderer.getSmallWidth(result + "…") > maxWidth) {
			result = result.substring(0, result.offsetByCodePoints(0, result.codePointCount(0, result.length()) - 1));
		}
		return result + "…";
	}
}
