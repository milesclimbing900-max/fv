package com.deepscan;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.*;

/** Sweeps loaded chunks outward from the player and records blocks that don't belong underground. */
public final class Scanner {
	public static final class Hit {
		public final int x, y, z;
		public final double dist;
		public long t = 0; // 0 = found but the sweep line hasn't reached it yet
		Hit(int x, int y, int z, double dist) { this.x = x; this.y = y; this.z = z; this.dist = dist; }
	}

	public static final List<Hit> hits = new ArrayList<>();
	private static final Set<Long> seen = new HashSet<>();
	private static final ArrayDeque<int[]> queue = new ArrayDeque<>();
	private static final Map<Block, Boolean> cache = new HashMap<>();

	public static boolean active;
	public static double sweepR, ox, oz;
	public static int total, done;

	/** Things that generate naturally in the deepslate layer / caves / geodes / deep dark. */
	private static final Set<String> NATURAL = Set.of(
		"minecraft:air", "minecraft:cave_air", "minecraft:void_air",
		"minecraft:stone", "minecraft:deepslate", "minecraft:tuff", "minecraft:granite",
		"minecraft:diorite", "minecraft:andesite", "minecraft:bedrock", "minecraft:water",
		"minecraft:lava", "minecraft:gravel", "minecraft:dirt", "minecraft:calcite",
		"minecraft:smooth_basalt", "minecraft:basalt", "minecraft:amethyst_block",
		"minecraft:budding_amethyst", "minecraft:amethyst_cluster", "minecraft:small_amethyst_bud",
		"minecraft:medium_amethyst_bud", "minecraft:large_amethyst_bud", "minecraft:dripstone_block",
		"minecraft:pointed_dripstone", "minecraft:glow_lichen", "minecraft:moss_block",
		"minecraft:moss_carpet", "minecraft:infested_deepslate", "minecraft:infested_stone",
		"minecraft:sculk", "minecraft:sculk_vein", "minecraft:sculk_catalyst",
		"minecraft:sculk_shrieker", "minecraft:sculk_sensor", "minecraft:clay", "minecraft:sand",
		"minecraft:copper_ore", "minecraft:magma_block", "minecraft:obsidian");

	/** Ancient city filler so deep dark cities don't spam results (toggle in menu). */
	private static final Set<String> CITY = Set.of(
		"minecraft:reinforced_deepslate", "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks",
		"minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles", "minecraft:polished_deepslate",
		"minecraft:chiseled_deepslate", "minecraft:soul_sand", "minecraft:soul_lantern",
		"minecraft:candle", "minecraft:black_wool", "minecraft:gray_wool", "minecraft:light_gray_wool",
		"minecraft:blue_wool", "minecraft:cyan_wool", "minecraft:light_blue_wool", "minecraft:chest",
		"minecraft:spawner", "minecraft:deepslate_brick_stairs", "minecraft:deepslate_brick_slab",
		"minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab", "minecraft:deepslate_brick_wall",
		"minecraft:deepslate_tile_wall", "minecraft:polished_deepslate_stairs", "minecraft:polished_deepslate_slab",
		"minecraft:black_carpet", "minecraft:gray_carpet", "minecraft:light_gray_carpet", "minecraft:lightning_rod");

	public static void invalidate() { cache.clear(); }

	public static void clear() { hits.clear(); seen.clear(); }

	private static boolean isTarget(Block b) {
		return cache.computeIfAbsent(b, k -> {
			Config c = Config.I;
			String id = BuiltInRegistries.BLOCK.getKey(k).toString();
			if (c.customList) return c.targets.contains(id);
			if (NATURAL.contains(id) || id.endsWith("_ore") || c.ignored.contains(id)) return false;
			return !(c.ignoreAncientCity && CITY.contains(id));
		});
	}

	public static void start(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		Config c = Config.I;
		if (!c.keepHighlighted) clear();
		invalidate();
		ox = mc.player.getX();
		oz = mc.player.getZ();
		sweepR = 0;
		queue.clear();
		int pcx = Mth.floor(ox) >> 4, pcz = Mth.floor(oz) >> 4, r = c.radiusChunks;
		List<int[]> list = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++)
			for (int dz = -r; dz <= r; dz++)
				if (dx * dx + dz * dz <= r * r) list.add(new int[]{pcx + dx, pcz + dz, dx * dx + dz * dz});
		list.sort(Comparator.comparingInt(a -> a[2]));
		queue.addAll(list);
		total = list.size();
		done = 0;
		active = true;
	}

	public static void tick(Minecraft mc) {
		Config c = Config.I;
		long now = System.nanoTime() / 1_000_000L;
		if (mc.level == null || mc.player == null) { active = false; return; }

		// reveal hits once the sweep line has passed them
		for (Hit h : hits) if (h.t == 0 && (!active || sweepR >= h.dist)) h.t = now;
		if (!c.keepHighlighted) {
			hits.removeIf(h -> {
				boolean dead = h.t != 0 && now - h.t > c.pulseMs;
				if (dead) seen.remove(BlockPos.asLong(h.x, h.y, h.z));
				return dead;
			});
		}
		if (!active) return;

		sweepR += c.sweepSpeed;
		int budget = 6;
		while (!queue.isEmpty() && budget-- > 0) {
			int[] ch = queue.peek();
			double dx = ch[0] * 16 + 8 - ox, dz = ch[1] * 16 + 8 - oz;
			if (Math.sqrt(dx * dx + dz * dz) > sweepR + 8) break;
			queue.poll();
			scanChunk(mc.level, ch[0], ch[1]);
			done++;
		}
		if (queue.isEmpty() && sweepR > c.radiusChunks * 16 + 16) active = false;
	}

	private static void scanChunk(Level level, int cx, int cz) {
		Config c = Config.I;
		ChunkAccess ch = level.getChunk(cx, cz, ChunkStatus.FULL, false);
		if (ch == null) return;
		int y0 = Math.max(c.minY, level.getMinY()), y1 = Math.min(c.maxY, level.getMaxY());
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		Set<Long> cand = new HashSet<>();
		for (int y = y0; y <= y1; y++)
			for (int x = 0; x < 16; x++)
				for (int z = 0; z < 16; z++) {
					p.set((cx << 4) + x, y, (cz << 4) + z);
					BlockState s = ch.getBlockState(p);
					if (s.isAir()) continue;
					if (isTarget(s.getBlock())) cand.add(p.asLong());
				}
		int minN = (10 - c.sensitivity) / 3; // sens 10..1 -> 0..3 neighbours required
		for (long key : cand) {
			int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
			if (minN > 0) {
				int n = 0;
				for (int dx = -1; dx <= 1; dx++)
					for (int dy = -1; dy <= 1; dy++)
						for (int dz = -1; dz <= 1; dz++)
							if ((dx | dy | dz) != 0 && cand.contains(BlockPos.asLong(x + dx, y + dy, z + dz))) n++;
				if (n < minN) continue;
			}
			if (seen.add(key)) {
				double ddx = x + 0.5 - ox, ddz = z + 0.5 - oz;
				hits.add(new Hit(x, y, z, Math.sqrt(ddx * ddx + ddz * ddz)));
			}
		}
	}
}
