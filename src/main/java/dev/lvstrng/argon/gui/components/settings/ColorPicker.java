package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.ColorSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.TextRenderer;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Compact inline RGBA editor used by normal Ghostor module settings. */
public final class ColorPicker extends RenderableSetting {
	private static final Color[] CHANNEL_COLORS = {
			new Color(241, 80, 91), new Color(79, 205, 132),
			new Color(92, 142, 255), new Color(214, 220, 232)
	};
	private final ColorSetting colorSetting;
	private int draggingChannel = -1;

	public ColorPicker(ModuleButton parent, Setting<?> setting, int offset) {
		super(parent, setting, offset);
		this.colorSetting = (ColorSetting) setting;
	}

	@Override
	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int y = parentY() + parentOffset() + offset;
		Color color = colorSetting.getColor();
		TextRenderer.drawString(colorSetting.getName(), context, parentX() + 20, y + 6,
				GhostorTheme.TEXT.getRGB());
		String hex = String.format("#%08X", colorSetting.getArgb());
		int hexX = parentX() + parentWidth() - 46 - TextRenderer.getSmallWidth(hex);
		TextRenderer.drawSmallString(hex, context, hexX, y + 8, GhostorTheme.TEXT_MUTED.getRGB());
		GhostorTheme.panel(context, parentX() + parentWidth() - 38, y + 5,
				parentX() + parentWidth() - 18, y + 21, color, 4);
		GhostorTheme.outline(context, parentX() + parentWidth() - 38, y + 5,
				parentX() + parentWidth() - 18, y + 21, GhostorTheme.BORDER, 4);

		int[] values = {color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
		int startX = parentX() + 20;
		int totalWidth = parentWidth() - 40;
		int gap = 6;
		int channelWidth = (totalWidth - gap * 3) / 4;
		for (int channel = 0; channel < 4; channel++) {
			int x = startX + channel * (channelWidth + gap);
			TextRenderer.drawSmallString("RGBA".substring(channel, channel + 1), context, x, y + 25,
					CHANNEL_COLORS[channel].getRGB());
			int trackX = x + 12;
			int trackWidth = channelWidth - 12;
			context.fill(trackX, y + 29, trackX + trackWidth, y + 32, GhostorTheme.DISABLED.getRGB());
			context.fill(trackX, y + 29, trackX + Math.max(1, trackWidth * values[channel] / 255), y + 32,
					CHANNEL_COLORS[channel].getRGB());
		}
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (isHovered(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			colorSetting.reset();
			return;
		}
		if (isHovered(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			draggingChannel = channelAt(mouseX);
			updateChannel(mouseX);
		}
	}

	@Override
	public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingChannel >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			updateChannel(mouseX);
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) draggingChannel = -1;
	}

	private int channelAt(double mouseX) {
		int startX = parentX() + 20;
		int totalWidth = parentWidth() - 40;
		int gap = 6;
		int channelWidth = (totalWidth - gap * 3) / 4;
		return Mth.clamp((int) ((mouseX - startX) / (channelWidth + gap)), 0, 3);
	}

	private void updateChannel(double mouseX) {
		if (draggingChannel < 0) return;
		int startX = parentX() + 20;
		int totalWidth = parentWidth() - 40;
		int gap = 6;
		int channelWidth = (totalWidth - gap * 3) / 4;
		int channelX = startX + draggingChannel * (channelWidth + gap) + 12;
		int trackWidth = Math.max(1, channelWidth - 12);
		double progress = Mth.clamp((mouseX - channelX) / trackWidth, 0.0, 1.0);
		colorSetting.setChannel(draggingChannel, (int) Math.round(progress * 255.0));
	}
}
