/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.gen;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

import buildcraft.api.enums.EnumSpring;

/**
 * Places a shallow, irregularly-shaped surface pool of crude oil with a thin oil "spout" rising above its centre
 * -- this port's honestly-scoped stand-in for the specific piece of 1.12.2's real oil world-gen system that
 * {@link OilSpringGenerator}'s own javadoc calls out as a real, undone cut: {@code OilGenStructure}'s
 * {@code GenType.LAKE} case (an above-ground oil pool, distinct from the individual vein
 * {@code OilSpringGenerator} already ports) plus its {@code Spout} companion (a tapering vertical oil column).
 * Both are ported here as one self-contained {@link Feature} because they were always visually paired in the
 * original -- a surface deposit with an oil geyser standing over it -- even though 1.12.2 only ever built a
 * "spout" for its *underground* LARGE/MEDIUM well types, never for a surface LAKE; see the two deliberate
 * simplifications documented below.
 *
 * <h2>What is a faithful port</h2>
 * <ul>
 * <li>The pool's shape is copied from {@code OilGenerator#createTendril}: a filled circle of {@code lakeRadius}
 * at the centre, then a probabilistic "grow outward" pass ({@code fillPatternIfProba}, copied unchanged) that
 * lets the blob sprout ragged, organic-looking tendrils rather than a plain circle.</li>
 * <li>Each pool column is carved the same way {@code OilGenStructure.PatternTerrainHeight#generateWithin} did:
 * find the surface (this port's {@link Heightmap.Types#WORLD_SURFACE_WG}, the modern equivalent of 1.12.2's
 * {@code World#getHeight}, including its quirk of stopping at a water surface rather than the seabed -- so, like
 * the original, a pool centred over ocean floats its oil on top of the water instead of sinking to the bottom),
 * clear five blocks of air above it, then unconditionally overwrite {@code depth} (1 or 2) blocks downward with
 * oil -- {@code ReplaceType.ALWAYS} in the original, so this does the same with no "is this solid?" guard.</li>
 * </ul>
 *
 * <h2>Deliberate simplifications, both documented in PORTING.md's progress entry for this pass</h2>
 * <ul>
 * <li><b>The spout is a single thin column, not the original's two-stage tapering cone.</b> 1.12.2's
 * {@code OilGenStructure.Spout} built a radius-1 tube for one segment then a radius-0 tube above it, and only
 * ever appeared over an underground well, gated by its own {@code enableOilSpouts} config flag and a
 * {@code LARGE}/{@code MEDIUM}-only well-type roll this port never reproduces (see below). Here it is always a
 * single {@code radius = 0} column of random height rising from the pool's own centre column, replacing whatever
 * terrain is in its way exactly as the original tube did -- a proportionate visual echo of "an oil geyser over
 * the pool", not a translation of the original's two well types and their four config-driven height ranges.</li>
 * <li><b>No underground sphere/tube/well roll.</b> The original always paired its surface pool with an
 * underground spherical deposit for its {@code LARGE}/{@code MEDIUM} types, but the standalone {@code LAKE} type
 * this class actually corresponds to never had one -- so leaving it out here is a faithful match to that
 * specific {@code GenType}, not a cut.</li>
 * </ul>
 *
 * <p>Registered and placed like {@code OilSpringGenerator} (a {@code neoforge:add_features} biome modifier plus
 * a {@code minecraft:rarity_filter} placement) but restricted to {@code minecraft:desert} and
 * {@code #minecraft:is_ocean} rather than every overworld biome -- see
 * {@code data/buildcraft/neoforge/biome_modifier/oil_lake.json} and PORTING.md's progress entry for this pass
 * for the full reasoning on why those two vanilla biomes are the chosen stand-in for 1.12.2's cut
 * {@code BiomeOilDesert}/{@code BiomeOilOcean} (a {@code BiomeDesert} subclass and a {@code BiomeOcean} subclass
 * respectively), and why this port does not attempt real custom biomes at all: confirmed via this session's own
 * read of the real merged jar's bundled {@code data/minecraft/worldgen/biome/*.json} that modern biome
 * definition is pure datapack JSON, with no {@code Biome} Java subclass left to port to, and biome *modification*
 * (adding features to existing biomes) goes through {@code BiomeModifier}/{@code data/<mod>/.../biome_modifier}
 * instead -- a genuinely different mechanism from 1.12.2's per-biome Java class plus
 * {@code GameRegistry.register}/{@code BiomeDictionary} model, not a 1:1 structural translation.
 */
public record OilLakeGenerator() implements Feature {
    public static final MapCodec<OilLakeGenerator> CODEC = MapCodec.unit(OilLakeGenerator::new);

    @Override
    public MapCodec<OilLakeGenerator> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (!EnumSpring.OIL.canGen || EnumSpring.OIL.liquidBlock == null) {
            return false;
        }

        int lakeRadius = 4 + random.nextInt(3);
        int tendrilRadius = 10 + random.nextInt(8);
        int depth = random.nextBoolean() ? 1 : 2;
        boolean[][] pattern = buildPattern(random, lakeRadius, tendrilRadius);
        int diameter = tendrilRadius * 2 + 1;
        BlockPos start = origin.offset(-tendrilRadius, 0, -tendrilRadius);

        boolean placedAny = false;
        for (int dx = 0; dx < diameter; dx++) {
            for (int dz = 0; dz < diameter; dz++) {
                if (!pattern[dx][dz]) {
                    continue;
                }
                BlockPos column = start.offset(dx, 0, dz);
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, column).below();
                for (int y = 0; y < 5; y++) {
                    level.setBlock(surface.above(y), Blocks.AIR.defaultBlockState(), 2);
                }
                for (int y = 0; y < depth; y++) {
                    level.setBlock(surface.below(y), EnumSpring.OIL.liquidBlock, 2);
                }
                placedAny = true;
            }
        }

        if (placedAny) {
            BlockPos centreSurface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, origin);
            int height = 3 + random.nextInt(6);
            for (int y = 0; y < height; y++) {
                level.setBlock(centreSurface.above(y), EnumSpring.OIL.liquidBlock, 2);
            }
        }

        return placedAny;
    }

    /** A direct port of {@code OilGenerator#createTendril}'s pattern-building loop. */
    private static boolean[][] buildPattern(RandomSource rand, int lakeRadius, int radius) {
        int diameter = radius * 2 + 1;
        boolean[][] pattern = new boolean[diameter][diameter];

        int x = radius;
        int z = radius;
        for (int dx = -lakeRadius; dx <= lakeRadius; dx++) {
            for (int dz = -lakeRadius; dz <= lakeRadius; dz++) {
                pattern[x + dx][z + dz] = dx * dx + dz * dz <= lakeRadius * lakeRadius;
            }
        }

        for (int w = 1; w < radius; w++) {
            float proba = (float) (radius - w + 4) / (float) (radius + 4);

            fillPatternIfProba(rand, proba, x, z + w, pattern);
            fillPatternIfProba(rand, proba, x, z - w, pattern);
            fillPatternIfProba(rand, proba, x + w, z, pattern);
            fillPatternIfProba(rand, proba, x - w, z, pattern);

            for (int i = 1; i <= w; i++) {
                fillPatternIfProba(rand, proba, x + i, z + w, pattern);
                fillPatternIfProba(rand, proba, x + i, z - w, pattern);
                fillPatternIfProba(rand, proba, x + w, z + i, pattern);
                fillPatternIfProba(rand, proba, x - w, z + i, pattern);

                fillPatternIfProba(rand, proba, x - i, z + w, pattern);
                fillPatternIfProba(rand, proba, x - i, z - w, pattern);
                fillPatternIfProba(rand, proba, x + w, z - i, pattern);
                fillPatternIfProba(rand, proba, x - w, z - i, pattern);
            }
        }
        return pattern;
    }

    private static void fillPatternIfProba(RandomSource rand, float proba, int x, int z, boolean[][] pattern) {
        if (rand.nextFloat() <= proba) {
            pattern[x][z] = isSet(pattern, x, z - 1) | isSet(pattern, x, z + 1) //
                | isSet(pattern, x - 1, z) | isSet(pattern, x + 1, z);
        }
    }

    private static boolean isSet(boolean[][] pattern, int x, int z) {
        if (x < 0 || x >= pattern.length) {
            return false;
        }
        if (z < 0 || z >= pattern[x].length) {
            return false;
        }
        return pattern[x][z];
    }
}
