package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.gui.ClickGui;
import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import dev.lvstrng.argon.utils.Utils;
import java.awt.*;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class HUD extends Module implements HudListener {
	private static final CharSequence argon = EncryptedString.of("Ghostor Client |");
	// Original Ghostor Module HUD visual constants (d93b016 / 9a2134a).
	private static final Color INFO_BACKGROUND = new Color(17, 22, 31, 248);
	private static final Color INFO_BORDER = new Color(76, 89, 112, 105);
	private static final Color MODULE_BACKGROUND = new Color(0, 0, 0, 175);
	private static final int MODULE_START_Y = 55;
	private static final int MODULE_TEXT_X_CUSTOM = 5;
	private static final int MODULE_TEXT_X_VANILLA = 8;
	private static final int MODULE_ROW_GAP = 0;
	private final BooleanSetting info = new BooleanSetting(EncryptedString.of("Info"), true);
	private final BooleanSetting modules = new BooleanSetting("Modules", true)
			.setDescription(EncryptedString.of("Renders module array list"));

	public HUD() {
		super(EncryptedString.of("HUD"),
				EncryptedString.of("Renders the client version and enabled modules on the HUD"),
				-1,
				Category.RENDER);
		addSettings(info, modules);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.gui.screen() != Argon.INSTANCE.clickGui) {
			final List<Module> enabledModules = Argon.INSTANCE.
					getModuleManager().
					getEnabledModules().
					stream().
					sorted((module1, module2) -> {
						CharSequence name1 = module1.getName();
						CharSequence name2 = module2.getName();

						int filteredLength1 = TextRenderer.getWidth(name1);
						int filteredLength2 = TextRenderer.getWidth(name2);

						return Integer.compare(filteredLength2, filteredLength1);
					}).
					toList();

			GuiGraphicsExtractor context = event.context;
			boolean customFont = ClickGUI.customFont.getValue();

			if (!(mc.gui.screen() instanceof ClickGui)) {

				if (info.getValue() && mc.player != null) {
					RenderUtils.unscaledProjection(context);
					int argonOffset = 10;
					int argonOffset2 = 10 + TextRenderer.getWidth(argon);

					String ping = "Ping: "; // shrimple null check
					String fps = "FPS: " + mc.getFps() + " |";
					String server = mc.getCurrentServer() == null ? "None" : mc.getCurrentServer().ip;
					if (mc != null && mc.player != null && mc.getConnection() != null) {
						PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
						if (entry != null) {
							ping += entry.getLatency() + " |";
						} else {
							ping += "N/A |";
						}
					} else {
						ping += "N/A |";
					}

				GhostorTheme.panel(context, 5, 6, argonOffset2 + TextRenderer.getWidth(fps) + TextRenderer.getWidth(ping) + TextRenderer.getWidth(server) + 35, 30, INFO_BACKGROUND, 6);
				GhostorTheme.outline(context, 5, 6, argonOffset2 + TextRenderer.getWidth(fps) + TextRenderer.getWidth(ping) + TextRenderer.getWidth(server) + 35, 30, INFO_BORDER, 6);

					TextRenderer.drawString(argon, context, argonOffset, 12, Utils.getMainColor(255, 4).getRGB());
					argonOffset += TextRenderer.getWidth(argon);

					TextRenderer.drawString(fps, context, argonOffset + 10, 12, Utils.getMainColor(255, 3).getRGB());
					TextRenderer.drawString(ping, context, (argonOffset + 10) + TextRenderer.getWidth(fps) + 10, 12, Utils.getMainColor(255, 2).getRGB());
					TextRenderer.drawString(server, context, (argonOffset + 10) + TextRenderer.getWidth(fps) + TextRenderer.getWidth(ping) + 20, 12, Utils.getMainColor(255, 1).getRGB());

					RenderUtils.scaledProjection(context);
				}
				if (modules.getValue()) {
					int offset = MODULE_START_Y;
					for (int index = 0; index < enabledModules.size(); index++) {
						Module module = enabledModules.get(index);
						RenderUtils.unscaledProjection(context);
						int charOffset = 6 + TextRenderer.getWidth(module.getName());

			RenderUtils.renderRoundedQuad(context, MODULE_BACKGROUND, 0, offset - 4, (charOffset + 5), offset + (mc.font.lineHeight * 2) - 1, 0, 0, 0, 5, 10);
						context.fillGradient(0, offset - 4, 2, offset + (mc.font.lineHeight * 2), Utils.getMainColor(255, index).getRGB(), Utils.getMainColor(255, index + 1).getRGB());

						int charOffset2 = customFont ? MODULE_TEXT_X_CUSTOM : MODULE_TEXT_X_VANILLA;

						TextRenderer.drawString(module.getName(), context, charOffset2, offset + (customFont ? 1 : 0), Utils.getMainColor(255, index).getRGB());

						offset += (mc.font.lineHeight * 2) + MODULE_ROW_GAP;
						RenderUtils.scaledProjection(context);
					}
				}
			}
		}
	}
}
