package com.deepscan;


import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/** HUD layer: glowing highlights (drawn over everything, so visible through walls), sweep ring and radar. */
public final class Overlay {
	private Overlay() {}

	// ---------- colour helpers ----------
	public static int alpha(int c, float k) {
		int a = (int) Math.min(255, Math.max(0, (c >>> 24) * k));
		return (a << 24) | (c & 0xFFFFFF);
	}

	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		int i = (int) (h * 6);
		float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		float[] rgb = switch (i % 6) {
			case 0 -> new float[]{v, t, p};
			case 1 -> new float[]{q, v, p};
			case 2 -> new float[]{p, v, t};
			case 3 -> new float[]{p, q, v};
			case 4 -> new float[]{t, p, v};
			default -> new float[]{v, p, q};
		};
		return 0xFF000000 | ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255);
	}

	public static float hueOf(int c) {
		float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		if (d < 1e-4f) return 0f;
		float h = max == r ? ((g - b) / d) % 6 : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
		h /= 6f;
		return h < 0 ? h + 1 : h;
	}

	static int hitColor(Scanner.Hit h, long now) {
		Config c = Config.I;
		long age = now - h.t;
		boolean pulse = age < c.pulseMs;
		int base = pulse ? c.foundColor : c.keptColor;
		if (c.rainbow) base = hsv(now * 0.0002f * c.animSpeed + h.x * 0.013f + h.z * 0.007f, 0.7f, 1f);
		float a = pulse ? 0.55f + 0.45f * (1f - age / (float) c.pulseMs)
			: c.opacity * (0.85f + 0.15f * (float) Math.sin(now * 0.004 * c.animSpeed));
		return alpha(base, a);
	}

	static float hitGlow(Scanner.Hit h, long now) {
		Config c = Config.I;
		long age = now - h.t;
		if (age < c.pulseMs) return (1f - age / (float) c.pulseMs) * (1.2f + 0.5f * (float) Math.sin(now * 0.03)) + 0.25f;
		return 0.3f * (0.8f + 0.2f * (float) Math.sin(now * 0.004 * c.animSpeed));
	}

	private static void rect(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int col) {
		g.fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), col);
	}

	// ---------- projection (camera basis from yaw/pitch/fov, no matrices needed) ----------
	private static float[] project(Vec3 eye, float yaw, float pitch, double tanHalf, int w, int h, double x, double y, double z) {
		double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
		double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
		double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
		double rx = -Math.cos(yr), rz = -Math.sin(yr);
		double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
		double depth = dx * fx + dy * fy + dz * fz;
		if (depth < 0.3) return null;
		double sx = (dx * rx + dz * rz) / depth, sy = (dx * ux + dy * uy + dz * uz) / depth;
		float px = (float) (w / 2.0 + sx / (tanHalf * w / h) * (w / 2.0));
		float py = (float) (h / 2.0 - sy / tanHalf * (h / 2.0));
		float size = (float) (0.5 / depth / tanHalf * (h / 2.0));
		return new float[]{px, py, size};
	}

	// ---------- entry point ----------
	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui().get()) return;
		Config c = Config.I;
		float pt = dt.getGameTimeDeltaPartialTick(false);
		long now = System.nanoTime() / 1_000_000L;
		int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();

		if (c.showEsp || c.showRing) {
			Vec3 eye = mc.player.getEyePosition(pt);
			float yaw = mc.player.getViewYRot(pt), pitch = mc.player.getViewXRot(pt);
			double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
			if (c.showRing) ring(g, mc, eye, yaw, pitch, tan, w, h, now);
			if (c.showEsp) esp(g, eye, yaw, pitch, tan, w, h, now);
		}
		if (c.showRadar && !(mc.gui.screen() instanceof MenuScreen)) {
			int r = c.radarSize / 2;
			drawRadar(g, mc, w - c.radarMargin - r, c.radarMargin + r, r, c.radarZoom, now, pt);
			String s = Scanner.active ? "SCANNING " + (Scanner.total == 0 ? 0 : 100 * Scanner.done / Scanner.total) + "%"
				: "READY";
			g.text(mc.font, "DEEPSCAN  " + s + "  " + Scanner.hits.size(), w - c.radarMargin - 2 * r, c.radarMargin + 2 * r + 4, 0xFFDDE8FF, true);
		}
	}

	private static void esp(GuiGraphicsExtractor g, Vec3 eye, float yaw, float pitch, double tan, int w, int h, long now) {
		Config c = Config.I;
		int drawn = 0;
		for (Scanner.Hit hit : Scanner.hits) {
			if (hit.t == 0) continue;
			if (drawn >= c.maxDraw) break;
			float[] p = project(eye, yaw, pitch, tan, w, h, hit.x + 0.5, hit.y + 0.5, hit.z + 0.5);
			if (p == null || p[0] < -50 || p[0] > w + 50 || p[1] < -50 || p[1] > h + 50) continue;
			drawn++;
			int col = hitColor(hit, now);
			float glow = hitGlow(hit, now) * c.glowStrength;
			float s = Math.max(2.5f, p[2]);
			int L = c.glowSize;
			for (int i = L; i >= 1; i--) {
				float k = 1f - (float) i / (L + 1);
				rect(g, p[0] - s - i, p[1] - s - i, p[0] + s + i, p[1] + s + i, alpha(col, 0.22f * glow * k * k));
			}
			rect(g, p[0] - s, p[1] - s, p[0] + s, p[1] + s, alpha(col, 0.35f));
			int o = alpha(col, 1.6f);
			rect(g, p[0] - s, p[1] - s, p[0] + s, p[1] - s + 1, o);
			rect(g, p[0] - s, p[1] + s - 1, p[0] + s, p[1] + s, o);
			rect(g, p[0] - s, p[1] - s, p[0] - s + 1, p[1] + s, o);
			rect(g, p[0] + s - 1, p[1] - s, p[0] + s, p[1] + s, o);
		}
	}

	private static void ring(GuiGraphicsExtractor g, Minecraft mc, Vec3 eye, float yaw, float pitch, double tan, int w, int h, long now) {
		if (!Scanner.active) return;
		Config c = Config.I;
		double r = Scanner.sweepR;
		int n = (int) Math.min(240, Math.max(48, r * 1.5));
		float pulse = 0.75f + 0.25f * (float) Math.sin(now * 0.01);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			float[] p = project(eye, yaw, pitch, tan, w, h, Scanner.ox + Math.cos(a) * r, mc.player.getY() + 0.1, Scanner.oz + Math.sin(a) * r);
			if (p == null) continue;
			rect(g, p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3, alpha(c.ringColor, 0.12f * pulse));
			rect(g, p[0] - 1, p[1] - 1, p[0] + 1, p[1] + 1, alpha(c.ringColor, pulse));
		}
	}

	// ---------- radar / map (used by HUD and the menu's map tab) ----------
	private static void dots(GuiGraphicsExtractor g, int cx, int cy, float r, int col, int size) {
		int n = Math.max(24, (int) (r * 5));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			int x = (int) Math.round(cx + Math.cos(a) * r), y = (int) Math.round(cy + Math.sin(a) * r);
			g.fill(x, y, x + size, y + size, col);
		}
	}

	public static void drawRadar(GuiGraphicsExtractor g, Minecraft mc, int cx, int cy, int R, int zoom, long now, float pt) {
		Config c = Config.I;
		int acc = c.accentColor;
		// soft outer glow + disc
		for (int i = 5; i >= 1; i--) dots(g, cx, cy, R + i, alpha(acc, 0.05f * (6 - i)), 2);
		for (int dy = -R; dy <= R; dy++) {
			int half = (int) Math.sqrt((double) R * R - (double) dy * dy);
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, 0xC0071326);
		}
		dots(g, cx, cy, R / 3f, alpha(acc, 0.35f), 1);
		dots(g, cx, cy, R * 2 / 3f, alpha(acc, 0.35f), 1);
		g.fill(cx - R, cy, cx + R + 1, cy + 1, alpha(acc, 0.15f));
		g.fill(cx, cy - R, cx + 1, cy + R + 1, alpha(acc, 0.15f));
		dots(g, cx, cy, R, alpha(0xFFFFFFFF, 0.9f), 1);

		// rotating sweep with fading trail
		double a0 = (now * 0.0018 * c.animSpeed) % (Math.PI * 2);
		for (int i = 0; i < 24; i++) {
			double a = a0 - i * 0.05;
			float k = 1f - i / 24f;
			for (int rr = 3; rr < R; rr += 3) {
				int x = (int) Math.round(cx + Math.cos(a) * rr), y = (int) Math.round(cy + Math.sin(a) * rr);
				g.fill(x, y, x + 3, y + 3, alpha(acc, 0.22f * k * k));
			}
		}
		for (int rr = 1; rr < R; rr++)
			g.fill((int) Math.round(cx + Math.cos(a0) * rr), (int) Math.round(cy + Math.sin(a0) * rr),
				(int) Math.round(cx + Math.cos(a0) * rr) + 1, (int) Math.round(cy + Math.sin(a0) * rr) + 1, alpha(0xFFFFFFFF, 0.85f));

		// scan radius line
		if (Scanner.active && mc.player != null) {
			float rr = (float) (Scanner.sweepR * R / zoom);
			if (rr <= R) dots(g, cx, cy, rr, alpha(c.ringColor, 0.9f), 1);
		}

		// blocks
		if (mc.player != null) {
			double px = mc.player.getX(), pz = mc.player.getZ();
			double yr = Math.toRadians(mc.player.getViewYRot(pt));
			double scale = (double) R / zoom;
			for (Scanner.Hit hit : Scanner.hits) {
				if (hit.t == 0) continue;
				double dx = hit.x + 0.5 - px, dz = hit.z + 0.5 - pz, sx, sy;
				if (c.radarRotate) {
					sx = (dx * -Math.cos(yr) + dz * -Math.sin(yr)) * scale;
					sy = -(dx * -Math.sin(yr) + dz * Math.cos(yr)) * scale;
				} else { sx = dx * scale; sy = dz * scale; }
				if (sx * sx + sy * sy > (double) (R - 2) * (R - 2)) continue;
				int x = cx + (int) Math.round(sx), y = cy + (int) Math.round(sy);
				int col = hitColor(hit, now);
				float glow = hitGlow(hit, now) * c.glowStrength;
				g.fill(x - 3, y - 3, x + 4, y + 4, alpha(col, 0.18f * glow));
				g.fill(x - 2, y - 2, x + 3, y + 3, alpha(col, 0.35f * glow + 0.15f));
				g.fill(x - 1, y - 1, x + 2, y + 2, col);
			}
		}
		g.fill(cx - 3, cy - 3, cx + 4, cy + 4, alpha(acc, 0.5f));
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
	}
}
