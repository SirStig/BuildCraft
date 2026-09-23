/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.factory.tile.TileTank;

/**
 * A stackable multi-block fluid reservoir. Mirrors the 26.x class of the same name -- see that one's javadoc for
 * the full account of what is deliberately dropped from 1.12.2 ({@code JOINED_BELOW}/{@code getActualState},
 * {@code ICustomPipeConnection}) and the now-real non-cube {@code VoxelShape} (see the 26.x copy's javadoc for
 * why it is no longer deferred), and why {@code ITankBlockConnector} needs no port at
 * all. This file differs only in the usual place: every {@code BlockBehaviour} hook here is {@code public} rather
 * than {@code protected}, and {@code getAnalogOutputSignal} takes no {@code Direction} parameter -- confirmed via
 * {@code javap} against {@code BlockBehaviour} on the Forge 1.20.1 merged jar, a real signature divergence from
 * 26.x, not an oversight.
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

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TileTank tank ? tank.getComparatorLevel() : 0;
    }

    /** See the 26.x copy of this class's own javadoc. Public rather than protected -- the usual 1.20.1
     * {@code BlockBehaviour} visibility divergence already documented above. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    private static final VoxelShape SHAPE = Shapes.box(2 / 16.0, 0, 2 / 16.0, 14 / 16.0, 1, 14 / 16.0);
}
