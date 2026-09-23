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
import net.minecraft.world.level.levelgen.feature.Feature;

import buildcraft.api.enums.EnumSpring;

import buildcraft.BCEnergyRegistries;

/**
 * Places a thin oil vein reaching from underground bedrock to the surface -- the oil counterpart of
 * {@code buildcraft.core.gen.SpringGenerator}, replacing this port's honest cut of 1.12.2's real oil world-gen
 * system: {@code buildcraft.energy.generation.OilGenerator}/{@code OilGenStructure} (a multi-shape "spout, lake,
 * flat-pattern, terrain-height" placer with two dedicated custom oil biomes registered via {@code BiomeDictionary}
 * and a {@code TerrainGen}-bus event handler). None of that has any equivalent left on either target -- custom
 * {@code Biome} registration and {@code IWorldGenerator}/{@code GenerationStage} are both gone, replaced entirely
 * by the datapack/{@link Feature} system {@code SpringGenerator}'s own javadoc already worked out in detail for
 * this port's *water* spring -- and reproducing the full multi-shape oil-lake system on top of that new
 * infrastructure is real, separate design work well beyond a "world-gen: yes/no" checkbox for this pass. This
 * class instead reuses {@code SpringGenerator}'s exact, already-verified shape (a self-contained {@code record
 * Feature}, no custom biome, attached to every overworld biome through an ordinary {@code neoforge:add_features}
 * biome modifier) with two deliberate, documented differences from the water copy:
 *
 * <ul>
 * <li>It places {@code BCEnergyRegistries.SPRING_OIL}/crude oil rather than {@code SPRING_WATER}/water, and reads
 * {@link EnumSpring#OIL}'s own {@code canGen} flag (independently toggleable from water's).</li>
 * <li>Its {@code placed_feature} JSON uses a rarer {@code rarity_filter} ({@code "chance": 200}, versus water's
 * {@code 40}) -- a deliberate simplification standing in for 1.12.2's own much lower spout/lake density (governed
 * there by two custom biomes plus a config-tunable per-chunk roll), not a measured translation of the original
 * odds; see PORTING.md's progress entry for this pass for the exact number and why it was chosen this way.</li>
 * </ul>
 *
 * <p>Everything else -- the bedrock scan, the "flat bedrock maps" early-return, and the fill-upward-until-non-air
 * loop -- is identical in shape to {@code SpringGenerator}; see that class's own javadoc for the full account of
 * the modern {@code Feature}/datapack system both classes are built on.
 */
public record OilSpringGenerator() implements Feature {
    public static final MapCodec<OilSpringGenerator> CODEC = MapCodec.unit(OilSpringGenerator::new);

    private static final int SCAN_HEIGHT = 5;

    @Override
    public MapCodec<OilSpringGenerator> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (!EnumSpring.OIL.canGen || EnumSpring.OIL.liquidBlock == null) {
            return false;
        }

        int minY = level.getMinY();
        for (int i = 0; i < SCAN_HEIGHT; i++) {
            BlockPos pos = new BlockPos(origin.getX(), minY + i, origin.getZ());
            if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
                continue;
            }
            if (i == 0) {
                return false;
            }

            level.setBlock(pos, BCEnergyRegistries.SPRING_OIL.get().defaultBlockState(), 2);

            for (int j = pos.getY() + 2; j < level.getMaxY(); j++) {
                BlockPos above = new BlockPos(pos.getX(), j, pos.getZ());
                if (level.isEmptyBlock(above)) {
                    break;
                }
                level.setBlock(above, EnumSpring.OIL.liquidBlock, 2);
            }
            return true;
        }
        return false;
    }
}
