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
 * The cosmetic shaft {@link TileMiner} places below itself as it digs. Mirrors the 26.x class of the same name --
 * see that one's javadoc for why {@code removedByPlayer}'s conditional protection collapses to an unconditional
 * {@code strength(-1.0F, ...)}, why this block carries no {@code BlockItem}, and why its loot table is
 * {@code Properties#noLootTable()} rather than a JSON file. This file differs only in the one place every
 * {@code BlockBehaviour} hook here does: {@code public} rather than 26.x's {@code protected}.
 */
public class BlockTube extends Block {
    private static final VoxelShape SHAPE = Shapes.box(4 / 16D, 0, 4 / 16D, 12 / 16D, 1, 12 / 16D);

    public BlockTube(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
