/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.AABB;

/**
 * One of the eight octants of a block that a length of pipe wire can occupy.
 *
 * <p>{@code AxisAlignedBB} is {@link AABB} and {@code AxisDirection.getOffset()} is {@code getStep()}. The
 * {@code Vec3d} pair the second box was built from was only ever used for its components, so the box is
 * constructed from the numbers directly.
 */
public enum EnumWirePart {
    EAST_UP_SOUTH(true, true, true),
    EAST_UP_NORTH(true, true, false),
    EAST_DOWN_SOUTH(true, false, true),
    EAST_DOWN_NORTH(true, false, false),
    WEST_UP_SOUTH(false, true, true),
    WEST_UP_NORTH(false, true, false),
    WEST_DOWN_SOUTH(false, false, true),
    WEST_DOWN_NORTH(false, false, false);

    public static final EnumWirePart[] VALUES = values();

    public final AxisDirection x;
    public final AxisDirection y;
    public final AxisDirection z;

    /** The bounding box for rendering a wire, or selecting an already-placed one. */
    public final AABB boundingBox;

    /** The bounding box used when adding pipe wire to a pipe. */
    public final AABB boundingBoxPossible;

    EnumWirePart(boolean x, boolean y, boolean z) {
        this.x = x ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;
        this.y = y ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;
        this.z = z ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;

        double x1 = this.x.getStep() * (5 / 16.0) + 0.5;
        double y1 = this.y.getStep() * (5 / 16.0) + 0.5;
        double z1 = this.z.getStep() * (5 / 16.0) + 0.5;
        double x2 = this.x.getStep() * (4 / 16.0) + 0.5;
        double y2 = this.y.getStep() * (4 / 16.0) + 0.5;
        double z2 = this.z.getStep() * (4 / 16.0) + 0.5;
        this.boundingBox = new AABB(x1, y1, z1, x2, y2, z2);

        this.boundingBoxPossible = new AABB(
            0.5, 0.5, 0.5,
            x ? 0.75 : 0.25, y ? 0.75 : 0.25, z ? 0.75 : 0.25
        );
    }

    @Nullable
    public AxisDirection getDirection(Axis axis) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    public static EnumWirePart get(int x, int y, int z) {
        boolean bx = (x % 2 + 2) % 2 == 1;
        boolean by = (y % 2 + 2) % 2 == 1;
        boolean bz = (z % 2 + 2) % 2 == 1;
        return get(bx, by, bz);
    }

    public static EnumWirePart get(boolean x, boolean y, boolean z) {
        if (x) {
            if (y) {
                return z ? EAST_UP_SOUTH : EAST_UP_NORTH;
            }
            return z ? EAST_DOWN_SOUTH : EAST_DOWN_NORTH;
        }
        if (y) {
            return z ? WEST_UP_SOUTH : WEST_UP_NORTH;
        }
        return z ? WEST_DOWN_SOUTH : WEST_DOWN_NORTH;
    }

    /** Kept for callers that still think in {@link Direction} rather than {@link Axis}. */
    @Nullable
    public AxisDirection getDirection(Direction face) {
        return getDirection(face.getAxis());
    }
}
