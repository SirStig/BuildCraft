/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;

/**
 * One wire octant at one block position: the unit a wire network is built out of.
 *
 * <p>{@code EnumFacing.getFrontOffsetX()} is {@code Direction.getStepX()}, and {@code EnumFacing.VALUES} is
 * {@code Direction.values()}.
 */
public final class WireNode {

    public final BlockPos pos;
    public final EnumWirePart part;

    private final int hash;

    public WireNode(BlockPos pos, EnumWirePart part) {
        this.pos = pos;
        this.part = part;
        this.hash = pos.hashCode() * 31 + part.hashCode();
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        WireNode other = (WireNode) obj;
        return part == other.part && pos.equals(other.pos);
    }

    @Override
    public String toString() {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ", " + part + ")";
    }

    public WireNode offset(Direction face) {
        int nx = (part.x == AxisDirection.POSITIVE ? 1 : 0) + face.getStepX();
        int ny = (part.y == AxisDirection.POSITIVE ? 1 : 0) + face.getStepY();
        int nz = (part.z == AxisDirection.POSITIVE ? 1 : 0) + face.getStepZ();
        EnumWirePart nPart = EnumWirePart.get(nx, ny, nz);
        if (nx < 0 || ny < 0 || nz < 0 || nx > 1 || ny > 1 || nz > 1) {
            return new WireNode(pos.relative(face), nPart);
        }
        return new WireNode(pos, nPart);
    }

    public Map<Direction, WireNode> getAllPossibleConnections() {
        Map<Direction, WireNode> map = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            map.put(face, offset(face));
        }
        return map;
    }
}
