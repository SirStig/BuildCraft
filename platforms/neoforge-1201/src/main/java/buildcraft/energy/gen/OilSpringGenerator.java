/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import buildcraft.api.enums.EnumSpring;

import buildcraft.BCEnergyRegistries;

/**
 * Places a thin oil vein reaching from underground bedrock to the surface -- see the 26.x copy of this class for
 * the full account of why this replaces 1.12.2's real oil world-gen system ({@code OilGenerator}/
 * {@code OilGenStructure}, two custom biomes, a config-tunable spout/lake placer) with
 * {@code buildcraft.core.gen.SpringGenerator}'s much simpler, already-verified shape instead, and for the exact
 * placement-rarity choice ({@code "chance": 200} in this feature's {@code placed_feature} JSON).
 */
public class OilSpringGenerator extends Feature<NoneFeatureConfiguration> {

    private static final int SCAN_HEIGHT = 5;

    public OilSpringGenerator() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!EnumSpring.OIL.canGen || EnumSpring.OIL.liquidBlock == null) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int minY = level.getMinBuildHeight();

        for (int i = 0; i < SCAN_HEIGHT; i++) {
            BlockPos pos = new BlockPos(origin.getX(), minY + i, origin.getZ());
            if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
                continue;
            }
            if (i == 0) {
                return false;
            }

            level.setBlock(pos, BCEnergyRegistries.SPRING_OIL.get().defaultBlockState(), 2);

            for (int j = pos.getY() + 2; j < level.getMaxBuildHeight(); j++) {
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
