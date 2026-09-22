/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.api.transport.pipe.ICustomPipeConnection;

/**
 * The default {@link ICustomPipeConnection}, used for any neighbour block that has not registered (or does not
 * implement) a custom one -- consulted from {@code Pipe#updateConnections}.
 *
 * <p>{@code IBlockState#getCollisionBoundingBox(World, BlockPos)} becomes {@code BlockState#getCollisionShape(
 * Level, BlockPos)}, confirmed via {@code javap} against {@code BlockBehaviour$BlockStateBase} to still expose the
 * same pos-aware convenience overload (defaulting to {@code CollisionContext.empty()}). The returned shape is
 * still in the neighbour's own local block space ({@code [0, 1]} on every axis), not world space, exactly like
 * the 1.12.2 box it replaces -- vanilla's own collision code re-offsets a block's shape into world space itself,
 * so nothing here has to. {@code AxisAlignedBB} is {@link AABB}; a shape with no collision at all is now an empty
 * {@link VoxelShape} rather than a nullable box, checked with {@link VoxelShape#isEmpty()}.
 *
 * <p>Byte-identical on both platforms: {@code getCollisionShape}/{@code VoxelShape#bounds()} are plain vanilla
 * API, unaffected by the loader.
 */
public enum DefaultPipeConnection implements ICustomPipeConnection {
    INSTANCE;

    @Override
    public float getExtension(Level level, BlockPos pos, Direction face, BlockState state) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return 0;
        }
        AABB bb = shape.bounds();
        return switch (face) {
            case DOWN -> (float) bb.minY;
            case UP -> 1 - (float) bb.maxY;
            case NORTH -> (float) bb.minZ;
            case SOUTH -> 1 - (float) bb.maxZ;
            case WEST -> (float) bb.minX;
            case EAST -> 1 - (float) bb.maxX;
        };
    }
}
