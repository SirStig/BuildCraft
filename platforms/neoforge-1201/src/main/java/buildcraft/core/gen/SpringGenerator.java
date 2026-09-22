/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import buildcraft.BCCoreRegistries;
import buildcraft.api.enums.EnumSpring;

/**
 * Replaces 1.12.2's {@code SpringPopulate}, a {@code @SubscribeEvent}-driven {@code PopulateChunkEvent.Post}
 * handler that called {@code TerrainGen.populate} to ask permission and then placed blocks directly with
 * {@code World#setBlockState}. That whole imperative, cancellable-event style of world generation is gone on
 * both targets -- world-gen is entirely datapack/{@link Feature}-driven now.
 *
 * <p>1.20.1 still has the classic three-tier system: this class extends {@code Feature<NoneFeatureConfiguration>}
 * (registered in code under {@code Registries.FEATURE} via {@code buildcraft.BCCoreFeatures}, matching how
 * every other static registry -- blocks, items -- gets a {@code DeferredRegister} in this port), which gets
 * wrapped in a {@code ConfiguredFeature<NoneFeatureConfiguration, SpringGenerator>} (pure datapack JSON,
 * {@code data/buildcraft/worldgen/configured_feature/spring_water.json}, needed even though this feature has
 * no real configuration -- {@code NoneFeatureConfiguration} is vanilla's own "no config" marker type, and its
 * JSON still needs an explicit empty {@code "config": {}} block, confirmed against vanilla's own
 * {@code void_start_platform.json}), which is itself wrapped in a {@code PlacedFeature}
 * ({@code data/buildcraft/worldgen/placed_feature/spring_water.json}, also datapack JSON). See the 26.x copy
 * of this class for the full account of how 26.x collapsed this three-tier system into two: there,
 * {@code ConfiguredFeature} is gone entirely and {@code Feature} became an interface implemented directly by
 * a record that carries its own configuration -- a genuine paradigm shift on that target, not just a rename,
 * confirmed via {@code javap} against the real 26.x merged jar ("class not found" for {@code ConfiguredFeature}).
 *
 * <p>Three further design points, each verified rather than assumed:
 * <ul>
 * <li><b>Nether/End exclusion.</b> 1.12.2 checked {@code dimId == -1 || dimId == 1} in Java. The modern,
 * idiomatic equivalent is to simply never attach this feature's {@code PlacedFeature} to a Nether/End biome
 * in the first place, via the {@code #minecraft:is_overworld} biome tag (confirmed present in the real
 * bundled vanilla data) on the {@code forge:add_features} biome-modifier JSON
 * ({@code data/buildcraft/forge/biome_modifier/spring_water.json}) that attaches this feature to biomes -- so
 * there is no runtime dimension check left to write here at all.</li>
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
 * replacement on this target is {@link WorldGenLevel#getMinBuildHeight()}/{@link
 * WorldGenLevel#getMaxBuildHeight()} (26.x renamed these {@code getMinY()}/{@code getMaxY()} -- see the new
 * PORTING.md divergence-table row), used below in place of both the original's implicit {@code y=0} floor
 * and its {@code world.getHeight()} ceiling.</li>
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
 * guaranteed to tolerate an out-of-range write the same way on a modern level, so this port reproduces the
 * same outcome -- that attempt places nothing -- as an explicit early return instead of reproducing the
 * out-of-bounds write itself.
 */
public class SpringGenerator extends Feature<NoneFeatureConfiguration> {

    private static final int SCAN_HEIGHT = 5;

    public SpringGenerator() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!EnumSpring.WATER.canGen) {
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
                // See the class javadoc's account of the original's "flat bedrock maps" workaround.
                return false;
            }

            level.setBlock(pos, BCCoreRegistries.SPRING_WATER.get().defaultBlockState(), 2);

            for (int j = pos.getY() + 2; j < level.getMaxBuildHeight(); j++) {
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
