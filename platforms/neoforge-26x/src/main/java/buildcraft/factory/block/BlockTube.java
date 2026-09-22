/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.factory.tile.TileMiner;

/**
 * The cosmetic shaft {@link TileMiner} places below itself as it digs, protecting the shaft from being mined out
 * from under it while the miner above is still working.
 *
 * <p>1.12.2's protection was two mechanisms layered together: {@code setBlockUnbreakable()} (hardness -1, so a
 * survival-mode break could never even start) and a {@code removedByPlayer} override that additionally refused
 * creative-mode insta-mine specifically while a {@code TileMiner} still stood somewhere above the shaft
 * (insta-mine bypasses hardness entirely). {@code removedByPlayer} does not exist on this target at all any
 * more -- confirmed via {@code javap} against both 26.x's and 1.20.1's {@code Block}/{@code BlockBehaviour}: zero
 * matches, the hook is gone outright, not renamed. There is nothing left to reattach the conditional half of the
 * old behaviour to, so this port collapses to the unconditional half only: {@code strength(-1.0F, ...)}, matching
 * {@code BlockSpringWater}'s already-ported "always unbreakable" precedent, set where this block is registered.
 * This is an accepted simplification, not an oversight -- {@code TileMiner}'s own shaft-retraction logic (see
 * that class's javadoc) already turns every tube block back to air by itself, the moment the owning miner is
 * removed or shortens its dig, so there is no normal-play scenario where a tube block is ever left orphaned (with
 * no {@code TileMiner} above it) for a player to need to clean up by hand -- the only case where 1.12.2's
 * conditional half of {@code removedByPlayer} ever actually mattered.
 *
 * <p>No block entity, unlike {@link BlockMiningWell}: this is a passive shaft segment with nothing to tick or
 * persist beyond its own blockstate, so it extends {@link Block} directly rather than {@code BlockBCTile}
 * (matching {@code BlockSpringWater}'s own precedent for a tile-less block). It is also registered with no
 * {@code BlockItem} at all (see {@code BCRegistry#addBlock}) and an empty loot table
 * ({@code Properties#noLootTable()}) -- a player is never meant to obtain this block directly, matching 1.12.2's
 * own blockstate JSON (which never referenced an item model either).
 *
 * <p>{@code isOpaqueCube()}/{@code isFullCube() -> false} is {@code noOcclusion()}, set alongside
 * {@code noLootTable()} where this block is registered.
 */
public class BlockTube extends Block {
    private static final VoxelShape SHAPE = Shapes.box(4 / 16D, 0, 4 / 16D, 12 / 16D, 1, 12 / 16D);

    public BlockTube(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
