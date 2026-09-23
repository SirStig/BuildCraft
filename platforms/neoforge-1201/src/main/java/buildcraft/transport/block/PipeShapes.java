/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.transport.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * The real per-instance pipe collision/outline shape -- until now {@link BlockPipeHolder} had no
 * {@code getShape}/{@code getCollisionShape} override at all, so every pipe collided and highlighted as a full
 * 1x1x1 cube no matter how few of its six arms were actually connected. Found live: a real client session showed
 * the hover outline and collision box on a pipe with only one or two connections were still the full block.
 *
 * <p>Ported from 1.12.2's real {@code BlockPipeHolder#addCollisionBoxToList} ({@code BOX_CENTER}, a
 * {@code 4/16..12/16} cube on every axis, plus one box per connected face) rather than invented: each connected
 * face contributes a box running from the block's edge on that face to {@code 0.25 + conSize/2} past centre,
 * {@code conSize} pixels-as-a-fraction thick along the connection axis and {@code 0.25..0.75} (the same width as
 * the centre cube) on the other two -- exactly {@link Pipe#getConnectedDist(Direction)}'s own meaning, the same
 * value {@code RenderTilePipeHolder} already reads to size the connection arm it draws, so the collision box and
 * the visible arm always agree.
 *
 * <p>Not yet included: pluggable and wire-part boxes (1.12.2 unions those in too). Those are a real, smaller
 * follow-up -- the core pipe arm/centre shape, the part that was flagrantly wrong (a whole extra block of
 * collision/outline around a mostly-empty pipe), is what this fixes.
 */
public final class PipeShapes {

    private static final VoxelShape CENTER = Shapes.box(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);

    private PipeShapes() {}

    public static VoxelShape get(TilePipeHolder tile) {
        Object pipeObj = tile.getPipe();
        if (!(pipeObj instanceof Pipe pipe)) {
            return Shapes.block();
        }
        VoxelShape shape = CENTER;
        for (Direction face : Direction.values()) {
            float conSize = pipe.getConnectedDist(face);
            if (conSize > 0) {
                shape = Shapes.join(shape, armBox(face, conSize), BooleanOp.OR);
            }
        }
        return shape;
    }

    private static VoxelShape armBox(Direction face, float conSize) {
        double centreOffset = 0.25 + conSize / 2.0;
        double cx = 0.5 + face.getStepX() * centreOffset;
        double cy = 0.5 + face.getStepY() * centreOffset;
        double cz = 0.5 + face.getStepZ() * centreOffset;
        double rx = face.getAxis() == Direction.Axis.X ? conSize / 2.0 : 0.25;
        double ry = face.getAxis() == Direction.Axis.Y ? conSize / 2.0 : 0.25;
        double rz = face.getAxis() == Direction.Axis.Z ? conSize / 2.0 : 0.25;
        return Shapes.box(cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz);
    }
}
