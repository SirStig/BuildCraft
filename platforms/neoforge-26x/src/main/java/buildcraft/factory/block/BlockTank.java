/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.factory.tile.TileTank;

/**
 * A stackable multi-block fluid reservoir; see {@link TileTank}'s own javadoc for the column-balancing logic.
 *
 * <p>Three things are deliberately dropped from 1.12.2's {@code BlockTank}, none of them new kinds of gap for this
 * port:
 *
 * <ul>
 * <li><b>{@code JOINED_BELOW}/{@code getActualState}.</b> A purely cosmetic "the block below is also a tank"
 *     blockstate, synthesised in {@code getActualState}, which no longer exists as a hook at all -- the same
 *     "no renderer, no {@code getActualState} any more" reasoning {@code TileChute}'s dropped {@code CONNECTED_MAP}
 *     already established. {@code shouldSideBeRendered}/{@code getBlockLayer} (the client-side face-culling and
 *     transparency hooks that consumed it) go with it.</li>
 * <li><b>A non-cube {@code VoxelShape}.</b> 1.12.2's real bounding box shaved two pixels off every horizontal
 *     side ({@code 2/16 .. 14/16}), matching a real, non-cube block model. Nothing in this pass exercises
 *     collision or occlusion fidelity for a tank -- there is no renderer to show the shape off, and no gameplay
 *     system in this port reads a tank's bounding box for anything other than the default full-cube placement/
 *     collision Minecraft already provides -- so this pass deliberately keeps the default full cube, the same
 *     call already made for {@code BlockPump}/{@code BlockEngineWood}'s own non-cube 1.12.2 render types. The
 *     block model below is a plain cube using the real tank textures, not the faithfully-shaped original.</li>
 * <li><b>{@code ICustomPipeConnection}/{@code getExtension}</b> (pipe-connection-shape hints). {@code
 *     buildcraft.transport} is not ported at all in this port, so there is no reader for this interface --
 *     dropped rather than stubbed, matching how {@code BlockChute}/{@code BlockPump} needed no pipe-connection
 *     hooks either.</li>
 * </ul>
 *
 * <p>{@code ITankBlockConnector} (the marker interface 1.12.2's {@code BlockTank} implemented) is dropped
 * entirely too, not just unported: 1.12.2's own {@code TileTank#getTanks()} already walked the column with a
 * direct {@code instanceof TileTank} check, never the marker interface -- the marker only ever existed for the
 * now-gone {@code getActualState}/{@code shouldSideBeRendered} pair above. With those gone, nothing is left to
 * read it.
 */
public class BlockTank extends BlockBCTile {

    public BlockTank(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileTank(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileTank tank) {
            tank.onPlacedBy();
        }
    }

    /** Confirmed via {@code javap} against {@code BlockBehaviour} on the 26.x merged jar: the modern equivalent of
     * 1.12.2's {@code hasComparatorInputOverride} is {@code protected boolean hasAnalogOutputSignal(BlockState)}. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Confirmed via {@code javap}: unlike 1.20.1's copy of this method, this target's
     * {@code getAnalogOutputSignal} takes an extra {@link Direction} parameter -- unused here, matching how
     * 1.12.2's own {@code getComparatorInputOverride} never used {@code world}/{@code pos} for anything beyond the
     * tile lookup either. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof TileTank tank ? tank.getComparatorLevel() : 0;
    }
}
