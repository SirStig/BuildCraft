/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.gen;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

import buildcraft.BCCoreRegistries;
import buildcraft.api.enums.EnumSpring;

/**
 * Replaces 1.12.2's {@code SpringPopulate}, a {@code @SubscribeEvent}-driven {@code PopulateChunkEvent.Post}
 * handler that called {@code TerrainGen.populate} to ask permission and then placed blocks directly with
 * {@code World#setBlockState}. That whole imperative, cancellable-event style of world generation is gone on
 * both targets -- world-gen is entirely datapack/{@link Feature}-driven now -- but the shape of the
 * replacement itself is <em>not</em> the same between 1.20.1 and 26.x, which is worth spelling out here for
 * whoever next touches world-gen in this port (an oil spring, an ore vein, ...):
 *
 * <ul>
 * <li>On 1.20.1, the classic three-tier system applies: an abstract {@code Feature<FC>} (with
 * {@code place(FeaturePlaceContext<FC>)}) is wrapped in a {@code ConfiguredFeature<FC, Feature<FC>>} (pairs
 * the feature with one concrete {@code FC} config instance), registered under
 * {@code Registries.CONFIGURED_FEATURE}, which is itself wrapped in a {@code PlacedFeature} (a
 * {@code Holder<ConfiguredFeature<?, ?>>} plus a list of {@code PlacementModifier}s) under
 * {@code Registries.PLACED_FEATURE}. The {@code Feature<FC>} type itself is the one piece registered in code
 * ({@code Registries.FEATURE}, a static registry); the configured/placed pair are pure datapack JSON.</li>
 * <li>On 26.x, {@code ConfiguredFeature} does not exist as a class any more (confirmed via {@code javap}:
 * "class not found"), and {@link Feature} itself is a plain interface, with
 * {@code place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)} -- no {@code FeaturePlaceContext}
 * wrapper either. A concrete feature is a <em>record implementing {@code Feature} directly</em>, carrying its
 * own configuration as record fields and its own {@code codec()} -- vanilla's own {@code SpringFeature} is
 * exactly this shape ({@code record SpringFeature(FluidState, boolean, int, int, HolderSet<Block>) implements
 * Feature}), and this class follows the same pattern. The "configured feature" concept has folded into the
 * feature instance itself: what used to be two registries ({@code Registries.FEATURE} holding the type,
 * {@code Registries.CONFIGURED_FEATURE} holding a type+config pair) is now {@code Registries.FEATURE_TYPE}
 * (a <em>static</em> code registry holding {@code MapCodec<? extends Feature>} -- see
 * {@code buildcraft.BCCoreFeatures}, which is where this class's codec is registered) and
 * {@code Registries.FEATURE} (a <em>dynamic</em>, datapack registry holding actual, fully-configured
 * {@code Feature} instances, loaded from {@code data/buildcraft/worldgen/feature/spring_water.json}, which
 * just references this class's type id since it has no configurable fields at all). {@code PlacedFeature}
 * is unchanged in role on both targets (a {@code Holder<Feature>} + placement modifiers, under
 * {@code Registries.PLACED_FEATURE}, still pure datapack JSON) -- confirmed via {@code javap} against the
 * real 26.x merged jar and vanilla's own decompiled {@code FeatureTypes}/{@code SpringFeature} sources.</li>
 * </ul>
 *
 * <p>Three further design points, each verified rather than assumed:
 * <ul>
 * <li><b>Nether/End exclusion.</b> 1.12.2 checked {@code dimId == -1 || dimId == 1} in Java. The modern,
 * idiomatic equivalent is to simply never attach this feature's {@code PlacedFeature} to a Nether/End biome
 * in the first place, via the {@code #minecraft:is_overworld} biome tag (confirmed present in the real
 * merged jar's bundled data) on the {@code neoforge:add_features} biome-modifier JSON
 * ({@code data/buildcraft/neoforge/biome_modifier/spring_water.json}) that attaches this feature to biomes --
 * so there is no runtime dimension check left to write here at all.</li>
 * <li><b>The 2.5%-per-chunk generation chance ("every 40th chunk").</b> Expressed declaratively as a
 * {@code minecraft:rarity_filter} {@code PlacementModifier} ({@code "chance": 40}) in this feature's
 * {@code placed_feature} JSON, rather than hand-rolled with {@code random.nextFloat()} here -- confirmed via
 * decompiled source that {@code RarityFilter#shouldPlace} computes exactly {@code random.nextFloat() < 1.0F /
 * chance}, the same 1-in-40 odds the original had. The per-chunk column (x/z) is likewise chosen by a
 * {@code minecraft:in_square} modifier rather than {@code random.nextInt(16)} here. Both are pure
 * probability/position decisions with no need to inspect world state, so they belong in the declarative
 * placement chain; the bedrock scan below does need to inspect world state, so it stays in Java.</li>
 * <li><b>{@code World#getHeight()} as a Y-coordinate loop bound</b> is the trap PORTING.md's structural-changes
 * list documents as item 15: it only ever worked because 1.12.2's world floor was always Y=0. The modern
 * replacement is {@link WorldGenLevel#getMinY()}/{@link WorldGenLevel#getMaxY()} (inherited from
 * {@code LevelHeightAccessor}), used below in place of both the original's implicit {@code y=0} floor and its
 * {@code world.getHeight()} ceiling.</li>
 * </ul>
 *
 * <p>{@link EnumSpring#WATER}'s {@code canGen} flag is checked here, at the point the feature actually runs,
 * matching 1.12.2's own guard in its event handler -- {@code canGen} is a plain mutable field, not
 * config-driven yet (see its own javadoc), so there is no declarative way to gate on it from JSON.
 *
 * <p>The bedrock scan and water-fill loop below are otherwise a direct port of {@code SpringPopulate
 * #doPopulate}'s body, including its odd "handle flat bedrock maps" special case: the original, on finding
 * bedrock at the very first scanned layer (world floor, always solid bedrock on every world type), shifted
 * the target position one layer *below* the floor -- which 1.12.2's Y&gt;=0 chunk storage silently discarded,
 * making that specific roll a no-op rather than a crash. Chunk storage below the world floor is not
 * guaranteed to tolerate an out-of-range write the same way on a modern level (26.x in particular gives every
 * dimension a real, configurable minimum Y, rather than a hard-coded 0), so this port reproduces the same
 * outcome -- that attempt places nothing -- as an explicit early return instead of reproducing the
 * out-of-bounds write itself.
 */
public record SpringGenerator() implements Feature {
    public static final MapCodec<SpringGenerator> CODEC = MapCodec.unit(SpringGenerator::new);

    private static final int SCAN_HEIGHT = 5;

    @Override
    public MapCodec<SpringGenerator> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (!EnumSpring.WATER.canGen) {
            return false;
        }

        int minY = level.getMinY();
        for (int i = 0; i < SCAN_HEIGHT; i++) {
            BlockPos pos = new BlockPos(origin.getX(), minY + i, origin.getZ());
            if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
                continue;
            }
            if (i == 0) {
                // See the class javadoc's account of the original's "flat bedrock maps" workaround.
                return false;
            }

            level.setBlock(pos, BCCoreRegistries.SPRING_WATER.get().defaultBlockState(), 2);

            for (int j = pos.getY() + 2; j < level.getMaxY(); j++) {
                BlockPos above = new BlockPos(pos.getX(), j, pos.getZ());
                if (level.isEmptyBlock(above)) {
                    break;
                }
                level.setBlock(above, Blocks.WATER.defaultBlockState(), 2);
            }
            return true;
        }
        return false;
    }
}
