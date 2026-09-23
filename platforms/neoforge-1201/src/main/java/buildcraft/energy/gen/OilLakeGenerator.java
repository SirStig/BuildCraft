/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import buildcraft.api.enums.EnumSpring;

/**
 * Places a shallow, irregularly-shaped surface pool of crude oil with a thin oil "spout" rising above its centre
 * -- see the 26.x copy of this class for the full account of what this replaces (1.12.2's {@code OilGenStructure}
 * {@code GenType.LAKE}/{@code Spout} pair, and the two custom oil biomes -- {@code BiomeOilDesert}/
 * {@code BiomeOilOcean} -- this port cannot carry over, since modern biome definition is pure datapack JSON with
 * no {@code Biome} Java subclass left to port to), what is a faithful port (the {@code createTendril} blob-growth
 * algorithm and the {@code PatternTerrainHeight} surface-carve behaviour, including its "floats oil on ocean
 * surface" quirk), and the two deliberate simplifications (a single thin column instead of the original's
 * two-stage tapering spout, and no underground sphere -- a faithful match to {@code GenType.LAKE} itself, which
 * never had one either).
 */
public class OilLakeGenerator extends Feature<NoneFeatureConfiguration> {

    public OilLakeGenerator() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!EnumSpring.OIL.canGen || EnumSpring.OIL.liquidBlock == null) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

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
