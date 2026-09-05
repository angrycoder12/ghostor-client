package dev.lvstrng.argon.gui.theme;

import dev.lvstrng.argon.gui.GhostorTheme;
import dev.lvstrng.argon.gui.theme.ThemeManager.ParticleSettings;
import dev.lvstrng.argon.utils.RenderUtils;
import java.awt.Color;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Fixed-size, reused GUI-only particle pool. */
public final class ThemeParticles {
	private static final int CAPACITY = 100;
	private final Particle[] particles = new Particle[CAPACITY];
	private final Random random = new Random();
	private long lastFrame;
	private int lastWidth;
	private int lastHeight;

	public ThemeParticles() {
		for (int index = 0; index < particles.length; index++) particles[index] = new Particle();
	}

	public void render(GuiGraphicsExtractor context, int width, int height, ParticleSettings settings) {
		if (!settings.enabled || settings.amount <= 0 || width <= 0 || height <= 0) {
			lastFrame = 0L;
			return;
		}

		long now = System.nanoTime();
		double deltaSeconds = lastFrame == 0L ? 0.0D : Math.min(0.05D, (now - lastFrame) / 1_000_000_000.0D);
		lastFrame = now;
		if (width != lastWidth || height != lastHeight) {
			for (Particle particle : particles) reset(particle, width, height, false);
			lastWidth = width;
			lastHeight = height;
		}

		Color base = settings.colorMode == ThemeManager.ParticleColorMode.Custom
				? new Color(settings.customColor, true) : GhostorTheme.ACCENT_SECONDARY;
		Color color = new Color(base.getRed(), base.getGreen(), base.getBlue(),
				Math.max(1, Math.min(255, Math.round(settings.opacity * 2.55F))));
		int count = Math.min(CAPACITY, Math.max(0, settings.amount));
		for (int index = 0; index < count; index++) {
			Particle particle = particles[index];
			particle.x += particle.driftX * settings.speed * deltaSeconds;
			particle.y += particle.driftY * settings.speed * deltaSeconds;
			if (particle.x < -8 || particle.x > width + 8 || particle.y < -8 || particle.y > height + 8) {
				reset(particle, width, height, true);
			}
			RenderUtils.renderCircle(context, color, particle.x, particle.y,
					Math.max(0.5D, settings.size * particle.scale), 10);
		}
	}

	public void reset() {
		lastFrame = 0L;
	}

	private void reset(Particle particle, int width, int height, boolean atBottom) {
		particle.x = random.nextDouble() * width;
		particle.y = atBottom ? height + random.nextDouble() * 8.0D : random.nextDouble() * height;
		particle.driftX = (random.nextDouble() - 0.5D) * 0.28D;
		particle.driftY = -(0.18D + random.nextDouble() * 0.42D);
		particle.scale = 0.65D + random.nextDouble() * 0.7D;
	}

	private static final class Particle {
		double x;
		double y;
		double driftX;
		double driftY;
		double scale;
	}
}
