package com.deepscan;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Fully custom-drawn, animated settings app. Input is polled so it doesn't depend on Screen mouse-method signatures. */
public class MenuScreen extends Screen {
	private static final String[] TABS = {"SCANNER", "VISUALS", "BLOCKS", "RADAR"};
	private static final String[] PRESETS = {
		"minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:ender_chest",
		"minecraft:shulker_box", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:crafting_table",
		"minecraft:hopper", "minecraft:rail", "minecraft:torch", "minecraft:wall_torch",
		"minecraft:oak_planks", "minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:netherrack",
		"minecraft:obsidian", "minecraft:tnt", "minecraft:white_bed", "minecraft:oak_door",
		"minecraft:glass", "minecraft:iron_block", "minecraft:oak_sign", "minecraft:ladder"};
	private static final int W = 400, H = 252;

	private final long openMs = Util.getMillis();
	private final Map<String, Float> anim = new HashMap<>();
	private int tab, page, mx, my;
	private boolean down, wasDown, clicked;
	private String drag;

	public MenuScreen() { super(Component.literal("DeepScan")); }

	@Override public boolean isPauseScreen() { return false; }

	@Override public void onClose() {
		Config.save();
		Scanner.invalidate();
		super.onClose();
	}

	// ---------- small widget toolkit ----------
	private boolean in(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

	private float lerp(String id, boolean target) {
		float v = anim.getOrDefault(id, target ? 1f : 0f);
		v += ((target ? 1f : 0f) - v) * 0.25f;
		anim.put(id, v);
		return v;
	}

	private int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	private void label(GuiGraphicsExtractor g, String s, int x, int y) {
		g.text(this.font, s, x, y, 0xFFE6EEFF, true);
	}

	private void row(GuiGraphicsExtractor g, String id, int x, int y, int w, boolean hover) {
		float h = lerp(id, hover);
		g.fill(x, y, x + w, y + 19, mix(0xFF111C33, 0xFF1A2A4D, h));
		g.fill(x, y, x + 2, y + 19, Overlay.alpha(Config.I.accentColor, 0.3f + 0.7f * h));
	}

	private void toggle(GuiGraphicsExtractor g, String id, String text, int x, int y, int w, BooleanSupplier get, Consumer<Boolean> set) {
		boolean hover = in(x, y, w, 19);
		row(g, id, x, y, w, hover);
		if (hover && clicked) set.accept(!get.getAsBoolean());
		float k = lerp(id + "k", get.getAsBoolean());
		label(g, text, x + 7, y + 6);
		int tx = x + w - 30;
		g.fill(tx, y + 5, tx + 24, y + 14, mix(0xFF2A3350, Config.I.accentColor, k));
		int kx = tx + 1 + (int) (k * 14);
		g.fill(kx, y + 6, kx + 8, y + 13, 0xFFFFFFFF);
	}

	private void slider(GuiGraphicsExtractor g, String id, String text, int x, int y, int w, double min, double max,
						DoubleSupplier get, DoubleConsumer set, String fmt, double shown) {
		boolean hover = in(x, y, w, 19);
		row(g, id, x, y, w, hover || id.equals(drag));
		int tx = x + w / 2 - 6, tw = w / 2 - 50;
		if (hover && clicked && mx >= tx - 4) drag = id;
		if (id.equals(drag) && down) {
			double t = Math.max(0, Math.min(1, (mx - tx) / (double) tw));
			set.accept(min + t * (max - min));
		}
		double t = (get.getAsDouble() - min) / (max - min);
		label(g, text, x + 7, y + 6);
		g.fill(tx, y + 9, tx + tw, y + 11, 0xFF2A3350);
		g.fill(tx, y + 9, tx + (int) (tw * t), y + 11, Config.I.accentColor);
		int kx = tx + (int) (tw * t);
		g.fill(kx - 3, y + 5, kx + 3, y + 14, 0xFFFFFFFF);
		g.text(this.font, String.format(fmt, get.getAsDouble() * shown), x + w - 42, y + 6, 0xFF9FB6E8, true);
	}

	private void colorRow(GuiGraphicsExtractor g, String id, String text, int x, int y, int w, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
		boolean hover = in(x, y, w, 19);
		row(g, id, x, y, w, hover || id.equals(drag));
		label(g, text, x + 7, y + 6);
		int tx = x + w / 2 - 6, tw = w / 2 - 50;
		for (int i = 0; i < tw; i += 2) g.fill(tx + i, y + 7, tx + i + 2, y + 12, Overlay.hsv(i / (float) tw, 0.75f, 1f));
		if (hover && clicked && mx >= tx - 4 && mx <= tx + tw + 4) drag = id;
		if (id.equals(drag) && down) set.accept(Overlay.hsv(Math.max(0, Math.min(1, (mx - tx) / (float) tw)), 0.75f, 1f));
		int kx = tx + (int) (Overlay.hueOf(get.getAsInt()) * tw);
		g.fill(kx - 2, y + 4, kx + 2, y + 15, 0xFFFFFFFF);
		g.fill(x + w - 40, y + 5, x + w - 26, y + 14, get.getAsInt());
		if (in(x + w - 22, y, 20, 19)) { g.fill(x + w - 22, y + 3, x + w - 4, y + 16, 0x66FFFFFF); if (clicked) set.accept(0xFFFFFFFF); }
		g.text(this.font, "W", x + w - 16, y + 6, 0xFFFFFFFF, true);
	}

	private void button(GuiGraphicsExtractor g, String id, String text, int x, int y, int w, Runnable run) {
		boolean hover = in(x, y, w, 19);
		float h = lerp(id, hover);
		g.fill(x - 1, y - 1, x + w + 1, y + 20, Overlay.alpha(Config.I.accentColor, 0.25f * h));
		g.fill(x, y, x + w, y + 19, mix(0xFF1B3A78, Config.I.accentColor, h));
		g.text(this.font, text, x + (w - this.font.width(text)) / 2, y + 6, 0xFFFFFFFF, true);
		if (hover && clicked) run.run();
	}

	// ---------- main render ----------
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		mx = mouseX; my = mouseY;
		down = this.minecraft.mouseHandler.isLeftPressed();
		clicked = down && !wasDown;
		wasDown = down;
		if (!down) drag = null;

		Config c = Config.I;
		long now = Util.getMillis();
		float t = Math.min(1f, (now - openMs) / 260f), e = 1f - (1f - t) * (1f - t) * (1f - t);
		g.fill(0, 0, this.width, this.height, Overlay.alpha(0xFF020812, 0.6f * e));
		int px = (this.width - W) / 2, py = (this.height - H) / 2 + (int) ((1 - e) * 14);

		for (int i = 7; i >= 1; i--)
			g.fill(px - i, py - i, px + W + i, py + H + i, Overlay.alpha(c.accentColor, 0.045f * e * (8 - i) / 7f));
		g.fill(px, py, px + W, py + H, Overlay.alpha(0xFF0A1220, 0.97f * e));
		g.fill(px, py, px + 88, py + H, Overlay.alpha(0xFF060C18, 0.97f * e));

		// title + animated accent line
		g.text(this.font, "D E E P S C A N", px + 8, py + 10, 0xFFFFFFFF, true);
		for (int i = 0; i < 88; i += 2) {
			float k = 0.5f + 0.5f * (float) Math.sin(now * 0.004 * c.animSpeed + i * 0.12);
			g.fill(px + i, py + 26, px + i + 2, py + 28, mix(0xFF1B3A78, c.accentColor, k));
		}

		// tabs
		for (int i = 0; i < TABS.length; i++) {
			int ty = py + 40 + i * 26;
			boolean sel = tab == i, hover = in(px + 6, ty, 76, 22);
			float k = lerp("tab" + i, sel || hover);
			g.fill(px + 6, ty, px + 82, ty + 22, Overlay.alpha(c.accentColor, 0.1f + 0.4f * k * (sel ? 1f : 0.5f)));
			if (sel) g.fill(px + 6, ty, px + 8, ty + 22, c.accentColor);
			g.text(this.font, TABS[i], px + 16 + (int) (k * 3), ty + 7, sel ? 0xFFFFFFFF : 0xFF9FB6E8, true);
			if (hover && clicked) { tab = i; page = 0; }
		}
		g.text(this.font, "RSHIFT / ESC", px + 8, py + H - 14, 0xFF5B6E96, true);

		int x = px + 98, y = py + 12, w = W - 108, p = 21;
		switch (tab) {
			case 0 -> {
				slider(g, "rad", "Radius (chunks)", x, y, w, 1, 16, () -> c.radiusChunks, v -> c.radiusChunks = (int) Math.round(v), "%.0f", 1);
				slider(g, "spd", "Sweep speed", x, y + p, w, 1, 12, () -> c.sweepSpeed, v -> c.sweepSpeed = (float) v, "%.1f", 1);
				slider(g, "miny", "Min Y", x, y + p * 2, w, -64, 0, () -> c.minY, v -> c.minY = (int) Math.round(v), "%.0f", 1);
				slider(g, "maxy", "Max Y", x, y + p * 3, w, -64, 16, () -> c.maxY, v -> c.maxY = (int) Math.round(v), "%.0f", 1);
				slider(g, "sens", "Sensitivity", x, y + p * 4, w, 1, 10, () -> c.sensitivity, v -> c.sensitivity = (int) Math.round(v), "%.0f", 1);
				toggle(g, "keep", "Keep highlighted", x, y + p * 5, w, () -> c.keepHighlighted, v -> c.keepHighlighted = v);
				slider(g, "op", "Kept opacity", x, y + p * 6, w, 0.05, 1, () -> c.opacity, v -> c.opacity = (float) v, "%.0f%%", 100);
				slider(g, "pulse", "Glow time (ms)", x, y + p * 7, w, 300, 3000, () -> c.pulseMs, v -> c.pulseMs = (int) Math.round(v), "%.0f", 1);
				button(g, "scan", "SCAN NOW", x, y + p * 8 + 6, w / 2 - 3, () -> { onClose(); Scanner.start(this.minecraft); });
				button(g, "clr", "CLEAR HIGHLIGHTS", x + w / 2 + 3, y + p * 8 + 6, w / 2 - 3, Scanner::clear);
			}
			case 1 -> {
				slider(g, "gl", "Glow strength", x, y, w, 0, 2, () -> c.glowStrength, v -> c.glowStrength = (float) v, "%.0f%%", 100);
				slider(g, "gs", "Glow size", x, y + p, w, 1, 16, () -> c.glowSize, v -> c.glowSize = (int) Math.round(v), "%.0f", 1);
				slider(g, "an", "Animation speed", x, y + p * 2, w, 0.2, 3, () -> c.animSpeed, v -> c.animSpeed = (float) v, "%.1f", 1);
				toggle(g, "esp", "Highlights over screen (see-through)", x, y + p * 3, w, () -> c.showEsp, v -> c.showEsp = v);
				toggle(g, "ring", "World radius line", x, y + p * 4, w, () -> c.showRing, v -> c.showRing = v);
				toggle(g, "rb", "Rainbow tint", x, y + p * 5, w, () -> c.rainbow, v -> c.rainbow = v);
				colorRow(g, "c1", "Found colour", x, y + p * 6, w, () -> c.foundColor, v -> c.foundColor = v);
				colorRow(g, "c2", "Kept colour", x, y + p * 7, w, () -> c.keptColor, v -> c.keptColor = v);
				colorRow(g, "c3", "Radius line colour", x, y + p * 8, w, () -> c.ringColor, v -> c.ringColor = v);
				colorRow(g, "c4", "UI accent", x, y + p * 9, w, () -> c.accentColor, v -> c.accentColor = v);
			}
			case 2 -> {
				toggle(g, "cl", "Custom list only (off = anything unnatural)", x, y, w, () -> c.customList, v -> { c.customList = v; Scanner.invalidate(); });
				toggle(g, "ac", "Ignore ancient city blocks", x, y + p, w, () -> c.ignoreAncientCity, v -> { c.ignoreAncientCity = v; Scanner.invalidate(); });
				int per = 12, pages = (PRESETS.length + per - 1) / per, cw = w / 2 - 2;
				for (int i = 0; i < per; i++) {
					int idx = page * per + i;
					if (idx >= PRESETS.length) break;
					String id = PRESETS[idx];
					toggle(g, "b" + idx, id.substring(10), x + (i % 2) * (cw + 4), y + p * 2 + 4 + (i / 2) * p, cw,
						() -> c.targets.contains(id), v -> { if (v) c.targets.add(id); else c.targets.remove(id); Scanner.invalidate(); });
				}
				int by = y + p * 8 + 10;
				button(g, "pg-", "<", x, by, 30, () -> page = Math.max(0, page - 1));
				button(g, "pg+", ">", x + 34, by, 30, () -> page = Math.min(pages - 1, page + 1));
				g.text(this.font, "page " + (page + 1) + "/" + pages + "   any block id: config/deepscan.json", x + 72, by + 6, 0xFF7F93BD, true);
			}
			default -> {
				toggle(g, "sr", "Show radar HUD", x, y, w / 2 + 20, () -> c.showRadar, v -> c.showRadar = v);
				toggle(g, "rr", "Rotate with view", x, y + p, w / 2 + 20, () -> c.radarRotate, v -> c.radarRotate = v);
				slider(g, "rs", "Size", x, y + p * 2, w / 2 + 20, 60, 200, () -> c.radarSize, v -> c.radarSize = (int) Math.round(v), "%.0f", 1);
				slider(g, "rz", "Zoom (blocks)", x, y + p * 3, w / 2 + 20, 16, 256, () -> c.radarZoom, v -> c.radarZoom = (int) Math.round(v), "%.0f", 1);
				button(g, "rz-", "ZOOM IN", x, y + p * 4 + 4, (w / 2 + 20) / 2 - 2, () -> c.radarZoom = Math.max(16, c.radarZoom / 2));
				button(g, "rz+", "ZOOM OUT", x + (w / 2 + 20) / 2 + 2, y + p * 4 + 4, (w / 2 + 20) / 2 - 2, () -> c.radarZoom = Math.min(256, c.radarZoom * 2));
				Overlay.drawRadar(g, this.minecraft, px + W - 74, py + 100, 62, c.radarZoom, now, delta);
				g.text(this.font, Scanner.hits.size() + " highlighted", px + W - 120, py + 170, 0xFFDDE8FF, true);
			}
		}
	}
}
