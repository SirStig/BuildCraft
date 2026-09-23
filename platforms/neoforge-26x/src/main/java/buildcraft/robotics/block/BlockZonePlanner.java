/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.block.IBlockWithFacing;

import buildcraft.robotics.tile.TileZonePlanner;

/**
 * Renamed from 1.12.2's {@code BlockZonePlanner} -- right-click always opens {@link TileZonePlanner}'s GUI, and
 * facing is wrench-rotated for free through {@link IBlockWithFacing}'s own default {@code attemptRotation}
 * (horizontal-only, {@code canFaceVertically()} left at its default {@code false} -- matching 1.12.2, which never
 * overrode it either). Mirrors {@code BlockEngineStone}'s own placement/GUI-opening shape.
 */
public class BlockZonePlanner extends BlockBCTile implements IBlockWithFacing {

    public BlockZonePlanner(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(getFacingProperty(), Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(getFacingProperty());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileZonePlanner(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileZonePlanner tile) {
            player.openMenu(tile);
        }
        return InteractionResult.SUCCESS;
    }
}
