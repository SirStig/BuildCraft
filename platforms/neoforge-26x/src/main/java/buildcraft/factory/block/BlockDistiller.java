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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.block.IBlockWithFacing;

import buildcraft.factory.tile.TileDistiller;

/**
 * 1.12.2's {@code BlockDistiller}: a horizontal-facing tile block that opens the distiller GUI on right-click.
 * 1.12.2's {@code BlockBCBase_Neptune#getStateForPlacement} faced an {@link IBlockWithFacing} block towards the
 * placer ({@code placer.getHorizontalFacing().getOpposite()}), reproduced here. {@code isOpaqueCube()/
 * isFullCube() -> false} and the {@code CUTOUT} layer become {@code noOcclusion()} (in {@code BCFactoryRegistries})
 * and the textures' own alpha (26.x derives the chunk layer from the sprites).
 */
public class BlockDistiller extends BlockBCTile implements IBlockWithFacing {

    public BlockDistiller(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING, Direction.WEST));
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
        return new TileDistiller(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileDistiller tile) {
                tile.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileDistiller tile) {
            player.openMenu(tile);
        }
        return InteractionResult.SUCCESS;
    }
}
