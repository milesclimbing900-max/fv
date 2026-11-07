package com.deepscan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** All settings. Saved to config/deepscan.json. Colours are ARGB ints. */
public final class Config {
	// scanner
	public int radiusChunks = 6;
	public float sweepSpeed = 3.5f;        // blocks per tick the radius line travels
	public int minY = -64;
	public int maxY = 0;                   // default: everything below stone level
	public int sensitivity = 7;            // 1 (needs clusters) .. 10 (lone blocks)
	public boolean keepHighlighted = true;
	public float opacity = 0.55f;          // opacity of kept highlights
	public int pulseMs = 1000;             // how long a fresh find glows
	// blocks
	public boolean customList = false;     // false = "anything unnatural", true = only `targets`
	public boolean ignoreAncientCity = true;
	public Set<String> targets = new LinkedHashSet<>();
	public Set<String> ignored = new LinkedHashSet<>();
	// visuals
	public boolean showEsp = true;
	public boolean showRing = true;
	public boolean rainbow = false;
	public float glowStrength = 0.8f;
	public int glowSize = 6;
	public float animSpeed = 1f;
	public int maxDraw = 800;
	public int foundColor = 0xFF35E0FF;
	public int keptColor = 0xFFFF4DD8;
	public int ringColor = 0xFF4DA3FF;
	public int accentColor = 0xFF3D8BFF;
	// radar / map
	public boolean showRadar = true;
	public int radarSize = 120;
	public int radarZoom = 96;             // blocks from centre to edge
	public boolean radarRotate = true;
	public int radarMargin = 10;

	public static Config I = new Config();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("deepscan.json");
	}

	public static void load() {
		try {
			Path p = path();
			if (Files.exists(p)) {
				Config c = GSON.fromJson(Files.readString(p), Config.class);
				if (c != null) I = c;
			}
		} catch (Exception e) {
			System.err.println("[DeepScan] could not read config: " + e);
		}
		save();
	}

	public static void save() {
		try {
			Files.writeString(path(), GSON.toJson(I));
		} catch (Exception e) {
			System.err.println("[DeepScan] could not save config: " + e);
		}
	}
}
