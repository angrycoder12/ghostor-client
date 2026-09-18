package dev.lvstrng.argon.gui.screens;

import dev.lvstrng.argon.gui.ClickGui;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.gui.components.GhostorIcons;
import dev.lvstrng.argon.gui.layout.GuiBounds;
import dev.lvstrng.argon.module.modules.render.MobESP;
import dev.lvstrng.argon.module.modules.render.blockesp.BlockEspShapeMode;
import dev.lvstrng.argon.module.modules.render.mobesp.MobEspData;
import dev.lvstrng.argon.module.modules.render.mobesp.MobEspData.ColorTarget;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Movable BlockESP-style entity selector with native entity previews. */
public final class MobSelectorScreen {
	private static final int ROW_HEIGHT = 46;
	private static final int ROW_GAP = 5;
	private static final int COLOR_HEIGHT = 54;
	private final ClickGui parent;
	private final MobESP module;
	private final GuiBounds bounds;
	private final List<EntityType<?>> allTypes;
	private List<EntityType<?>> filteredTypes;
	private String search = "";
	private boolean searchFocused;
	private EntityType<?> focusedType;
	private LivingEntity preview;
	private double listScroll;
	private double detailScroll;
	private int panelX, panelY, panelWidth, panelHeight;
	private int listX, listY, listWidth, listBottom;
	private int detailX, detailY, detailWidth, detailBottom;
	private ColorTarget draggingTarget;
	private int draggingChannel = -1;

	public MobSelectorScreen(ClickGui parent, MobESP module) {
		this.parent = parent;
		this.module = module;
		this.bounds = parent.mobSelectorBounds();
		this.allTypes = module.selectableTypes();
		this.filteredTypes = allTypes;
	}

	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		layout();
		GhostorTheme.glowOutline(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight, GhostorTheme.RADIUS);
		GhostorTheme.panel(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight, GhostorTheme.SURFACE_GLASS, GhostorTheme.RADIUS);
		GhostorTheme.outline(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight, GhostorTheme.ACCENT_BORDER, GhostorTheme.RADIUS);
		TextRenderer.drawString("Select Mobs", context, panelX + 20, panelY + 22, GhostorTheme.TEXT.getRGB());
		TextRenderer.drawSmallString("Choose living entities to highlight", context, panelX + 20, panelY + 44, GhostorTheme.TEXT_MUTED.getRGB());
		renderSearch(context, mouseX, mouseY);
		renderList(context, mouseX, mouseY);
		renderDetails(context, mouseX, mouseY);
		renderFooter(context, mouseX, mouseY);
		GhostorTheme.resizeHandle(context, bounds.right(), bounds.bottom(), bounds.isResizing()
				|| bounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE));
	}

	private void layout() {
		Minecraft minecraft = Minecraft.getInstance();
		bounds.clampToScreen(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), GhostorTheme.PANEL_MARGIN);
		panelX = bounds.x(); panelY = bounds.y(); panelWidth = bounds.width(); panelHeight = bounds.height();
		int contentWidth = panelWidth - 40;
		listWidth = Math.max(280, (int) (contentWidth * 0.57D));
		detailWidth = contentWidth - listWidth - 14;
		if (detailWidth < 270) { detailWidth = 270; listWidth = contentWidth - detailWidth - 14; }
		listX = panelX + 20; detailX = listX + listWidth + 14;
		listY = detailY = panelY + 132;
		listBottom = detailBottom = panelY + panelHeight - 78;
		listScroll = Mth.clamp(listScroll, 0, maxListScroll());
		detailScroll = Mth.clamp(detailScroll, 0, maxDetailScroll());
	}

	private void renderSearch(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int y = panelY + 76;
		boolean over = hovered(mouseX, mouseY, listX, y, listX + listWidth, y + 40);
		GhostorTheme.panel(context, listX, y, listX + listWidth, y + 40,
				searchFocused || over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 8);
		GhostorTheme.outline(context, listX, y, listX + listWidth, y + 40,
				searchFocused ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 8);
		String text = search.isBlank() ? "Search mobs..." : fit(search, listWidth - 58);
		TextRenderer.drawSmallString(text, context, listX + 14, y + 15,
				search.isBlank() ? GhostorTheme.DISABLED.getRGB() : GhostorTheme.TEXT.getRGB());
		GhostorIcons.search(context, listX + listWidth - 29, y + 11,
				searchFocused ? GhostorTheme.ACCENT_HOVER : GhostorTheme.TEXT_MUTED);
		GhostorTheme.panel(context, detailX, y, detailX + detailWidth, y + 40, GhostorTheme.ACCENT_SOFT, 8);
		TextRenderer.drawCenteredString(module.selectedCount() + " selected", context,
				detailX + detailWidth / 2, y + 12, GhostorTheme.ACCENT_HOVER.getRGB());
	}

	private void renderList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		GhostorTheme.panel(context, listX, listY, listX + listWidth, listBottom, GhostorTheme.SURFACE, 9);
		GhostorTheme.outline(context, listX, listY, listX + listWidth, listBottom, GhostorTheme.BORDER, 9);
		context.enableScissor(listX + 1, listY + 1, listX + listWidth - 1, listBottom - 1);
		int y = listY + 7 - (int) listScroll;
		for (EntityType<?> type : filteredTypes) {
			int rowY = y; y += ROW_HEIGHT + ROW_GAP;
			if (rowY + ROW_HEIGHT < listY || rowY > listBottom) continue;
			boolean over = hovered(mouseX, mouseY, listX + 7, rowY, listX + listWidth - 12, rowY + ROW_HEIGHT);
			boolean selected = module.isSelected(type);
			boolean focused = focusedType == type;
			GhostorTheme.panel(context, listX + 7, rowY, listX + listWidth - 12, rowY + ROW_HEIGHT,
					focused ? GhostorTheme.ACTIVE_SURFACE : over ? GhostorTheme.SURFACE_HOVER : GhostorTheme.SURFACE_ELEVATED, 7);
			GhostorTheme.outline(context, listX + 7, rowY, listX + listWidth - 12, rowY + ROW_HEIGHT,
					focused || selected ? GhostorTheme.ACCENT_BORDER : GhostorTheme.BORDER, 7);
			context.item(icon(type), listX + 16, rowY + 14);
			TextRenderer.drawString(fit(type.getDescription().getString(), listWidth - 120), context,
					listX + 44, rowY + 14, GhostorTheme.TEXT.getRGB());
			renderToggle(context, listX + listWidth - 54, rowY + 13, selected);
		}
		context.disableScissor();
	}

	private void renderDetails(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		GhostorTheme.panel(context, detailX, detailY, detailX + detailWidth, detailBottom, GhostorTheme.SURFACE, 9);
		GhostorTheme.outline(context, detailX, detailY, detailX + detailWidth, detailBottom, GhostorTheme.BORDER, 9);
		if (focusedType == null) {
			TextRenderer.drawCenteredString("Right-click a mob", context, detailX + detailWidth / 2,
					detailY + 88, GhostorTheme.TEXT.getRGB());
			TextRenderer.drawSmallString("to edit individual settings", context,
					detailX + (detailWidth - TextRenderer.getSmallWidth("to edit individual settings")) / 2,
					detailY + 112, GhostorTheme.TEXT_MUTED.getRGB());
			return;
		}
		context.enableScissor(detailX + 1, detailY + 1, detailX + detailWidth - 1, detailBottom - 1);
		int y = detailY + 14 - (int) detailScroll;
		if (preview != null) {
			int previewHeight = 92;
			int scale = Math.max(20, Math.min(54, (int) (52.0D / Math.max(0.5D,
					Math.max(preview.getBbWidth(), preview.getBbHeight())))));
			InventoryScreen.extractEntityInInventoryFollowsMouse(context, detailX + 18, y,
					detailX + detailWidth - 18, y + previewHeight, scale, 0.0F,
					detailX + detailWidth / 2.0F, y + 38.0F, preview);
		}
		y += 98;
		TextRenderer.drawCenteredString(fit(focusedType.getDescription().getString(), detailWidth - 28),
				context, detailX + detailWidth / 2, y, GhostorTheme.TEXT.getRGB());
		y += 23;
		TextRenderer.drawSmallString(fit(MobESP.id(focusedType), detailWidth - 28), context,
				detailX + 14, y, GhostorTheme.TEXT_MUTED.getRGB());
		y += 28;
		context.fill(detailX + 14, y, detailX + detailWidth - 14, y + 1, GhostorTheme.BORDER.getRGB());
		y += 16;
		TextRenderer.drawSmallString("Type: Living Entity", context, detailX + 14, y, GhostorTheme.TEXT_MUTED.getRGB());
		y += 23;
		TextRenderer.drawSmallString("Status: ", context, detailX + 14, y, GhostorTheme.TEXT_MUTED.getRGB());
		TextRenderer.drawSmallString(module.isSelected(focusedType) ? "Enabled" : "Disabled", context,
				detailX + 14 + TextRenderer.getSmallWidth("Status: "), y,
				(module.isSelected(focusedType) ? GhostorTheme.SUCCESS : GhostorTheme.DANGER).getRGB());
		y += 30;
		if (!module.isSelected(focusedType)) {
			TextRenderer.drawSmallString("Enable this mob to edit individual settings.", context,
					detailX + 14, y, GhostorTheme.TEXT_MUTED.getRGB());
			context.disableScissor(); return;
		}
		MobEspData data = module.customData(focusedType);
		renderSettingToggle(context, y, "Use Custom Settings", data.useCustomSettings, true); y += 44;
		renderMode(context, y, data); y += 44;
		renderColor(context, y, "Line Color", data.lineColor, data.useCustomSettings); y += COLOR_HEIGHT;
		renderColor(context, y, "Fill Color", data.sideColor, data.useCustomSettings); y += COLOR_HEIGHT;
		renderSettingToggle(context, y, "Tracers", data.tracer, data.useCustomSettings); y += 44;
		renderColor(context, y, "Tracer Color", data.tracerColor, data.useCustomSettings);
		context.disableScissor();
	}

	private void renderSettingToggle(GuiGraphicsExtractor context, int y, String name, boolean value, boolean enabled) {
		TextRenderer.drawSmallString(name, context, detailX + 14, y + 13,
				(enabled ? GhostorTheme.TEXT : GhostorTheme.DISABLED).getRGB());
		renderToggle(context, detailX + detailWidth - 52, y + 8, value && enabled);
	}

	private void renderMode(GuiGraphicsExtractor context, int y, MobEspData data) {
		TextRenderer.drawSmallString("ESP Type", context, detailX + 14, y + 13,
				(data.useCustomSettings ? GhostorTheme.TEXT : GhostorTheme.DISABLED).getRGB());
		String mode = data.shapeMode.name();
		int width = TextRenderer.getSmallWidth(mode) + 18;
		GhostorTheme.panel(context, detailX + detailWidth - width - 14, y + 7,
				detailX + detailWidth - 14, y + 35, data.useCustomSettings ? GhostorTheme.ACCENT_SOFT : GhostorTheme.SURFACE_ELEVATED, 5);
		TextRenderer.drawSmallString(mode, context, detailX + detailWidth - width - 5, y + 13,
				(data.useCustomSettings ? GhostorTheme.ACCENT_HOVER : GhostorTheme.DISABLED).getRGB());
	}

	private void renderColor(GuiGraphicsExtractor context, int y, String name, int packed, boolean enabled) {
		Color color = new Color(packed, true);
		TextRenderer.drawSmallString(name, context, detailX + 14, y + 7,
				(enabled ? GhostorTheme.TEXT : GhostorTheme.DISABLED).getRGB());
		GhostorTheme.panel(context, detailX + detailWidth - 36, y + 4, detailX + detailWidth - 16, y + 21, color, 4);
		int[] values = {color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
		Color[] colors = {GhostorTheme.DANGER, GhostorTheme.SUCCESS, new Color(92, 142, 255), GhostorTheme.TEXT_MUTED};
		int startX = detailX + 14, totalWidth = detailWidth - 28, gap = 5;
		int channelWidth = (totalWidth - gap * 3) / 4;
		for (int channel = 0; channel < 4; channel++) {
			int x = startX + channel * (channelWidth + gap);
			TextRenderer.drawSmallString("RGBA".substring(channel, channel + 1), context, x, y + 30,
					(enabled ? colors[channel] : GhostorTheme.DISABLED).getRGB());
			int trackX = x + 11, trackWidth = Math.max(4, channelWidth - 11);
			context.fill(trackX, y + 34, trackX + trackWidth, y + 37, GhostorTheme.DISABLED.getRGB());
			if (enabled) context.fill(trackX, y + 34, trackX + Math.max(1, trackWidth * values[channel] / 255),
					y + 37, colors[channel].getRGB());
		}
	}

	private void renderFooter(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int y = panelY + panelHeight - 50;
		renderButton(context, mouseX, mouseY, panelX + 20, y, 86, "Back", false);
		renderButton(context, mouseX, mouseY, panelX + panelWidth - 106, y, 86, "Done", false);
		renderButton(context, mouseX, mouseY, panelX + panelWidth - 254, y, 138, "Clear Selected", true);
	}

	private void renderButton(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String text, boolean danger) {
		boolean over = hovered(mouseX, mouseY, x, y, x + width, y + 36);
		Color color = danger ? (over ? GhostorTheme.DANGER_HOVER : new Color(142, 45, 58, 238))
				: (over ? GhostorTheme.ACCENT_HOVER : GhostorTheme.ACCENT);
		GhostorTheme.panel(context, x, y, x + width, y + 36, color, 7);
		TextRenderer.drawCenteredString(text, context, x + width / 2, y + 11, GhostorTheme.TEXT.getRGB());
	}

	private void renderToggle(GuiGraphicsExtractor context, int x, int y, boolean enabled) {
		GhostorTheme.panel(context, x, y, x + 36, y + 20,
				enabled ? GhostorTheme.ACCENT : GhostorTheme.DISABLED_TOGGLE, 10);
		RenderUtils.renderCircle(context, Color.WHITE, x + (enabled ? 26 : 10), y + 10, 7, 16);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!bounds.contains(mouseX, mouseY)) { searchFocused = false; return false; }
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && bounds.isOverResizeHandle(mouseX, mouseY, GhostorTheme.RESIZE_HANDLE)) {
			searchFocused = false; bounds.beginResize(mouseX, mouseY); return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && bounds.isOverHeader(mouseX, mouseY, 66)) {
			searchFocused = false; bounds.beginDrag(mouseX, mouseY); return true;
		}
		int searchY = panelY + 76;
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hovered(mouseX, mouseY, listX, searchY, listX + listWidth, searchY + 40)) {
			searchFocused = true; return true;
		}
		searchFocused = false;
		EntityType<?> row = typeAt(mouseX, mouseY);
		if (row != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { focus(row); return true; }
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { module.toggle(row); if (focusedType == null) focus(row); return true; }
		}
		int footerY = panelY + panelHeight - 50;
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (hovered(mouseX, mouseY, panelX + 20, footerY, panelX + 106, footerY + 36)
				|| hovered(mouseX, mouseY, panelX + panelWidth - 106, footerY, panelX + panelWidth - 20, footerY + 36))) {
			close(); return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hovered(mouseX, mouseY,
				panelX + panelWidth - 254, footerY, panelX + panelWidth - 116, footerY + 36)) {
			module.clearSelected(); return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) handleDetailClick(mouseX, mouseY);
		return true;
	}

	private void focus(EntityType<?> type) {
		focusedType = type; detailScroll = 0; preview = null;
		if (Minecraft.getInstance().level == null) return;
		try {
			Entity entity = type.create(Minecraft.getInstance().level, EntitySpawnReason.LOAD);
			if (entity instanceof LivingEntity living) preview = living;
		} catch (RuntimeException ignored) { preview = null; }
	}

	private boolean handleDetailClick(double mouseX, double mouseY) {
		if (focusedType == null || !module.isSelected(focusedType)
				|| !hovered(mouseX, mouseY, detailX, detailY, detailX + detailWidth, detailBottom)) return false;
		MobEspData data = module.customData(focusedType);
		int y = detailY + 232 - (int) detailScroll;
		if (hovered(mouseX, mouseY, detailX + 8, y, detailX + detailWidth - 8, y + 44)) {
			data.useCustomSettings = !data.useCustomSettings; module.markConfigChanged(); return true;
		}
		y += 44;
		if (data.useCustomSettings && hovered(mouseX, mouseY, detailX + 8, y, detailX + detailWidth - 8, y + 44)) {
			data.shapeMode = switch (data.shapeMode) { case Lines -> BlockEspShapeMode.Box; case Box -> BlockEspShapeMode.Both; case Both -> BlockEspShapeMode.Lines; };
			module.markConfigChanged(); return true;
		}
		y += 44;
		if (data.useCustomSettings && beginColor(mouseX, mouseY, y, ColorTarget.Line)) return true;
		y += COLOR_HEIGHT;
		if (data.useCustomSettings && beginColor(mouseX, mouseY, y, ColorTarget.Side)) return true;
		y += COLOR_HEIGHT;
		if (data.useCustomSettings && hovered(mouseX, mouseY, detailX + 8, y, detailX + detailWidth - 8, y + 44)) {
			data.tracer = !data.tracer; module.markConfigChanged(); return true;
		}
		y += 44;
		return data.useCustomSettings && beginColor(mouseX, mouseY, y, ColorTarget.Tracer);
	}

	private boolean beginColor(double mouseX, double mouseY, int y, ColorTarget target) {
		if (!hovered(mouseX, mouseY, detailX + 8, y + 24, detailX + detailWidth - 8, y + 43)) return false;
		draggingTarget = target; draggingChannel = colorChannelAt(mouseX); updateColor(mouseX); return true;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button) {
		if (bounds.isInteracting()) {
			Minecraft minecraft = Minecraft.getInstance();
			bounds.update(mouseX, mouseY, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), GhostorTheme.PANEL_MARGIN);
			layout(); return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingTarget != null) { updateColor(mouseX); return true; }
		return false;
	}
	public boolean mouseReleased(int button) {
		if (bounds.isInteracting()) { if (bounds.endInteraction()) parent.saveLayout(); return true; }
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingTarget != null) {
			draggingTarget = null; draggingChannel = -1; return true;
		}
		return false;
	}
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!bounds.contains(mouseX, mouseY)) return false;
		if (hovered(mouseX, mouseY, listX, listY, listX + listWidth, listBottom))
			listScroll = Mth.clamp(listScroll - amount * 42.0D, 0, maxListScroll());
		else if (hovered(mouseX, mouseY, detailX, detailY, detailX + detailWidth, detailBottom))
			detailScroll = Mth.clamp(detailScroll - amount * 42.0D, 0, maxDetailScroll());
		return true;
	}
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
		if (searchFocused && event.key() == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
			search = search.substring(0, search.offsetByCodePoints(search.length(), -1)); refreshFilter(); return true;
		}
		return searchFocused;
	}
	public boolean charTyped(CharacterEvent event) {
		if (!searchFocused || !event.isAllowedChatCharacter() || search.length() >= 64) return false;
		search += event.codepointAsString(); refreshFilter(); return true;
	}
	public boolean loseFocus() { searchFocused = false; return endInteraction(); }
	public void clearInputFocus() { searchFocused = false; }
	public boolean isInputFocused() { return searchFocused; }
	public boolean isFor(MobESP candidate) { return module == candidate; }
	public void onParentClosing() { searchFocused = false; draggingTarget = null; preview = null; bounds.endInteraction(); }
	private boolean endInteraction() { draggingTarget = null; draggingChannel = -1; return bounds.endInteraction(); }
	private void close() { onParentClosing(); parent.saveLayout(); parent.closeMobSelector(); }

	private void refreshFilter() {
		String query = search.strip().toLowerCase(Locale.ROOT);
		filteredTypes = query.isEmpty() ? allTypes : allTypes.stream().filter(type ->
				type.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)
						|| MobESP.id(type).contains(query)).toList();
		listScroll = 0;
	}
	private EntityType<?> typeAt(double mouseX, double mouseY) {
		if (!hovered(mouseX, mouseY, listX + 7, listY, listX + listWidth - 12, listBottom)) return null;
		int relative = (int) (mouseY - listY - 7 + listScroll);
		int index = relative / (ROW_HEIGHT + ROW_GAP), inside = relative % (ROW_HEIGHT + ROW_GAP);
		return index >= 0 && index < filteredTypes.size() && inside < ROW_HEIGHT ? filteredTypes.get(index) : null;
	}
	private ItemStack icon(EntityType<?> type) {
		return SpawnEggItem.byId(type).map(holder -> new ItemStack(holder.value())).orElseGet(() -> new ItemStack(Items.SPAWNER));
	}
	private int colorChannelAt(double mouseX) {
		int startX = detailX + 14, totalWidth = detailWidth - 28, gap = 5, width = (totalWidth - gap * 3) / 4;
		return Mth.clamp((int) ((mouseX - startX) / (width + gap)), 0, 3);
	}
	private void updateColor(double mouseX) {
		if (draggingTarget == null || focusedType == null) return;
		int startX = detailX + 14, totalWidth = detailWidth - 28, gap = 5, width = (totalWidth - gap * 3) / 4;
		int channelX = startX + draggingChannel * (width + gap) + 11;
		double progress = Mth.clamp((mouseX - channelX) / Math.max(1, width - 11), 0, 1);
		module.customData(focusedType).setColorChannel(draggingTarget, draggingChannel, (int) Math.round(progress * 255));
		module.markConfigChanged();
	}
	private double maxListScroll() { return Math.max(0, 14 + filteredTypes.size() * (ROW_HEIGHT + ROW_GAP) - (listBottom - listY)); }
	private double maxDetailScroll() { return focusedType == null ? 0 : Math.max(0, 232 + 44 * 3 + COLOR_HEIGHT * 3 + 18 - (detailBottom - detailY)); }
	private static boolean hovered(double x, double y, double x1, double y1, double x2, double y2) { return x >= x1 && x <= x2 && y >= y1 && y <= y2; }
	private static String fit(String value, int maxWidth) {
		if (TextRenderer.getSmallWidth(value) <= maxWidth) return value;
		String result = value;
		while (!result.isEmpty() && TextRenderer.getSmallWidth(result + "…") > maxWidth)
			result = result.substring(0, result.offsetByCodePoints(0, result.codePointCount(0, result.length()) - 1));
		return result + "…";
	}
}
