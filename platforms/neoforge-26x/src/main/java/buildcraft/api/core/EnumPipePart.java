/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/**
 * The six faces of a pipe plus its centre.
 *
 * <p>Two things changed in the port. {@code IStringSerializable.getName()} became
 * {@link StringRepresentable#getSerializedName()}, which matters because blockstate properties are keyed off it.
 * And the pair of raw {@code NBTBase} methods is gone: 26.x serialises through {@code ValueInput}/{@code ValueOutput},
 * which work in terms of codecs, so {@link #CODEC} replaces {@code readFromNBT}/{@code writeToNBT}. It reads the
 * same lowercase names the 1.12.2 string form wrote, so existing saves still load.
 *
 * <p>{@code EnumFacing.VALUES} and {@code .HORIZONTALS} no longer exist on {@link Direction}; the equivalents are
 * {@code Direction.values()} and {@code Direction.Plane.HORIZONTAL}.
 */
public enum EnumPipePart implements StringRepresentable {
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST),
    /** CENTER, UNKNOWN and ALL are all valid uses of this. */
    CENTER(null);

    public static final EnumPipePart[] VALUES = values();
    public static final EnumPipePart[] FACES;
    public static final EnumPipePart[] HORIZONTALS;

    /** Reads and writes the lowercase serialised name, falling back to {@link #CENTER} for anything unknown. */
    public static final Codec<EnumPipePart> CODEC = StringRepresentable.fromEnum(EnumPipePart::values);

    private static final Map<Direction, EnumPipePart> FACING_MAP = new EnumMap<>(Direction.class);
    private static final Map<String, EnumPipePart> NAME_MAP = new HashMap<>();

    @Nullable
    public final Direction face;

    static {
        for (EnumPipePart part : VALUES) {
            NAME_MAP.put(part.name(), part);
            NAME_MAP.put(part.getSerializedName(), part);
            if (part.face != null) {
                FACING_MAP.put(part.face, part);
            }
        }
        FACES = fromFacingArray(Direction.values());

        // Plane is Iterable on both targets, but only 1.21+ gives it a length(), so collect rather than size up front.
        List<Direction> horizontals = new ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            horizontals.add(dir);
        }
        HORIZONTALS = fromFacingArray(horizontals.toArray(new Direction[0]));
    }

    EnumPipePart(@Nullable Direction face) {
        this.face = face;
    }

    private static EnumPipePart[] fromFacingArray(Direction... faces) {
        EnumPipePart[] arr = new EnumPipePart[faces.length];
        for (int i = 0; i < faces.length; i++) {
            arr[i] = fromFacing(faces[i]);
        }
        return arr;
    }

    public static int ordinal(@Nullable Direction face) {
        return face == null ? 6 : face.ordinal();
    }

    public static EnumPipePart fromFacing(@Nullable Direction face) {
        if (face == null) {
            return CENTER;
        }
        return FACING_MAP.get(face);
    }

    public static EnumPipePart[] validFaces() {
        return FACES;
    }

    /**
     * 1.12.2 stored this in block metadata. Metadata is gone, but the same index is still used as a compact wire
     * and array form, so the lookup stays.
     */
    public static EnumPipePart fromIndex(int index) {
        if (index < 0 || index >= VALUES.length) {
            return CENTER;
        }
        return VALUES[index];
    }

    /** Looks up a part by either its constant name or its serialised name. Unknown names give {@link #CENTER}. */
    public static EnumPipePart fromName(@Nullable String name) {
        if (name == null) {
            return CENTER;
        }
        return NAME_MAP.getOrDefault(name, CENTER);
    }

    /** @return The index used by {@link #fromIndex(int)}: the face's 3D data value, or 6 for the centre. */
    public int getIndex() {
        return face == null ? 6 : face.get3DDataValue();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public EnumPipePart next() {
        return switch (this) {
            case DOWN -> EAST;
            case EAST -> NORTH;
            case NORTH -> SOUTH;
            case SOUTH -> UP;
            case UP -> WEST;
            case WEST -> DOWN;
            default -> DOWN;
        };
    }

    public EnumPipePart opposite() {
        if (this == CENTER) {
            return CENTER;
        }
        return fromFacing(face.getOpposite());
    }

    /** Convenience for the common "read a name, get a part" shape, for callers that are not using {@link #CODEC}. */
    public static Function<String, EnumPipePart> nameLookup() {
        return EnumPipePart::fromName;
    }
}
