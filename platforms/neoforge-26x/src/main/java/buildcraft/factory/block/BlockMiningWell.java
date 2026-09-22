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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.block.IBlockWithFacing;

import buildcraft.factory.tile.TileMiningWell;

/**
 * Digs straight down from its own position, one block at a time, powered by MJ; see {@link TileMiningWell}'s own
 * javadoc for the machine logic and its removal, and {@link BlockTube}'s for the shaft it digs through.
 *
 * <p>The facing property is carried over purely for parity, not function: 1.12.2's {@code BlockBCBase_Neptune}
 * automatically added a facing property to <em>every</em> {@code IBlockWithFacing} block, and 1.12.2's
 * {@code BlockMiningWell} implemented that interface without overriding anything about it -- the well always digs
 * straight down regardless of which way it faces. Kept here so the block remains wrench-rotatable like its
 * 1.12.2 counterpart, even though nothing on this target reads the resulting {@link Direction} either.
 */
public class BlockMiningWell extends BlockBCTile implements IBlockWithFacing {

    public BlockMiningWell(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BuildCraftProperties.BLOCK_FACING);
    }

    /** Faces away from the placing player by default, matching vanilla's own convention for a horizontal-facing
     * block (furnaces, dispensers, ...) -- 1.12.2's placement logic lived in the unported
     * {@code BlockBCBase_Neptune#getStateForPlacement}, so there is no original behaviour to match here beyond
     * "pick something reasonable", the same call {@code BlockChute}/{@code BlockMarkerBase} already made for
     * their own placement defaults. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileMiningWell(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileMiningWell well) {
                well.serverTick();
            }
        };
    }
}
