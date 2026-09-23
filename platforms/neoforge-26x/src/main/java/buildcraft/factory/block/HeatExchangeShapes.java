/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.factory.block;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.factory.block.BlockHeatExchange.EnumExchangePart;

/**
 * The real per-part, per-facing Heat Exchanger shape -- until now {@link BlockHeatExchange} had no
 * {@code getShape}/{@code getCollisionShape} override at all, so every section collided as a full cube despite
 * the block's own model (already real, multi-element geometry built by an earlier batch: `heat_exchange_start`/
 * `_middle`/`_end`, each a hollow pipe inset {@code 2/16} on two axes) looking nothing like one.
 *
 * <p>Bounding boxes taken directly from those model files' own elements (the overall envelope of each part's
 * geometry, not a per-face cutout), then rotated the same way {@code blockstates/heat_exchange.json} rotates the
 * models themselves -- {@code WEST} unrotated, {@code NORTH}/{@code EAST}/{@code SOUTH} at 90/180/270 degrees
 * around Y -- confirmed by reading that blockstate file rather than assumed. `cap_left`/`cap_right` (the two
 * middle-section end-cap variants) share `MIDDLE`'s own bounding box exactly, so no separate entry is needed for
 * them.
 */
public final class HeatExchangeShapes {

    private static final Map<EnumExchangePart, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(EnumExchangePart.class);

    static {
        put(EnumExchangePart.START,
            Direction.WEST, box(2, 0, 2, 14, 14, 16),
            Direction.NORTH, box(2, 0, 2, 16, 14, 14),
            Direction.EAST, box(2, 0, 0, 14, 14, 14),
            Direction.SOUTH, box(0, 0, 2, 14, 14, 14));
        put(EnumExchangePart.MIDDLE,
            Direction.WEST, box(2, 2, 0, 14, 14, 16),
            Direction.NORTH, box(0, 2, 2, 16, 14, 14),
            Direction.EAST, box(2, 2, 0, 14, 14, 16),
            Direction.SOUTH, box(0, 2, 2, 16, 14, 14));
        put(EnumExchangePart.END,
            Direction.WEST, box(2, 2, 0, 14, 16, 14),
            Direction.NORTH, box(0, 2, 2, 14, 16, 14),
            Direction.EAST, box(2, 2, 2, 14, 16, 16),
            Direction.SOUTH, box(2, 2, 2, 16, 16, 14));
    }

    private HeatExchangeShapes() {}

    public static VoxelShape get(EnumExchangePart part, Direction facing) {
        return SHAPES.get(part).get(facing);
    }

    private static void put(EnumExchangePart part, Object... facingsAndShapes) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (int i = 0; i < facingsAndShapes.length; i += 2) {
            map.put((Direction) facingsAndShapes[i], (VoxelShape) facingsAndShapes[i + 1]);
        }
        SHAPES.put(part, map);
    }

    private static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }
}
