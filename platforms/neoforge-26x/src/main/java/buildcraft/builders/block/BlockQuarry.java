/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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

import buildcraft.builders.tile.TileQuarry;

/**
 * Ported from 1.12.2's {@code BlockQuarry}. The frame-cleanup half of {@code breakBlock} moves to
 * {@code TileQuarry#preRemoveSideEffects} (this target's replacement for {@code TileBC_Neptune#onRemove()}, called
 * on the tile itself while it is still valid -- see {@code TileMiner}'s own javadoc for the same hook already
 * established), rather than living on this class. {@code getActualState}'s per-side {@code CONNECTED_MAP} (whether
 * each face visually touches an inventory) has no modern equivalent and nothing reads it for gameplay, so it is
 * dropped -- the same call already made for {@code BlockChute}'s own cosmetic connection indicator. The
 * "shaping_the_world" advancement unlock is dropped with it (see {@code TileQuarry}'s own javadoc).
 */
public class BlockQuarry extends BlockBCTile implements IBlockWithFacing {

    public BlockQuarry(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BuildCraftProperties.BLOCK_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileQuarry(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileQuarry quarry) {
                quarry.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileQuarry quarry) {
            quarry.onPlacedBy(placer);
        }
    }

    @Override
    public SoundType getSoundType(BlockState state) {
        return SoundType.ANVIL;
    }

    // IBlockWithFacing

    /** The quarry's facing (and the area it claimed from it) is fixed at placement -- matches 1.12.2's own
     * {@code canBeRotated -> false} override. */
    @Override
    public boolean canBeRotated(Level level, BlockPos pos, BlockState state) {
        return false;
    }
}
