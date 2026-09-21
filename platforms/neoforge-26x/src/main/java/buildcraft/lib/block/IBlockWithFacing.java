/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import buildcraft.api.blocks.ICustomRotationHandler;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.lib.misc.RotationUtil;

/**
 * Marker interface for a block that carries a {@link Direction} property, so its owner can wrench-rotate it
 * generically.
 *
 * <p>1.12.2 read the two facing properties off {@code BlockBCBase_Neptune}, and rotated by rebuilding the
 * blockstate with {@code withProperty}/{@code getMetaFromState}-style meta plumbing. {@code BlockBCBase_Neptune}
 * is not ported -- its job (holding these two property constants) is already covered by
 * {@link BuildCraftProperties#BLOCK_FACING}/{@link BuildCraftProperties#BLOCK_FACING_6} -- and
 * {@code withProperty} is {@code setValue} on an immutable {@link BlockState} now.
 */
public interface IBlockWithFacing extends ICustomRotationHandler {
    default boolean canFaceVertically() {
        return false;
    }

    default EnumProperty<Direction> getFacingProperty() {
        return canFaceVertically() ? BuildCraftProperties.BLOCK_FACING_6 : BuildCraftProperties.BLOCK_FACING;
    }

    default boolean canBeRotated(Level level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    default InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (!canBeRotated(level, pos, state)) {
            return InteractionResult.FAIL;
        }
        Direction currentFacing = state.getValue(getFacingProperty());
        Direction newFacing = canFaceVertically() ? RotationUtil.rotateAll(currentFacing) : currentFacing.getClockWise();
        level.setBlockAndUpdate(pos, state.setValue(getFacingProperty(), newFacing));
        return InteractionResult.SUCCESS;
    }
}
