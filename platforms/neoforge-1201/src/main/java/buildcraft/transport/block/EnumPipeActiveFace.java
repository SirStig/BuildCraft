/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.transport.block;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/**
 * The active ("filled") face of a directional pipe (wood, iron), as a blockstate property value -- a
 * {@link Direction} plus {@link #NONE}, since a directional pipe with no valid face has no active face at all and
 * vanilla's {@code BlockStateProperties} has no "nullable direction" property to reuse. Drives which arm model
 * variant ({@code pipe_holder_arm_<material>} or {@code pipe_holder_arm_<material>_filled}) the
 * {@code pipe_holder.json} multipart picks for each direction, reproducing 1.12.2's own clear/filled face textures
 * ({@code PipeBehaviourWood#getTextureData}, {@code PipeBehaviourIron#getTextureIndex}) without a custom model.
 * Always {@link #NONE} for every non-directional material. Kept local to {@code buildcraft.transport} for the same
 * single-consumer reason {@link EnumPipeMaterial} gives.
 */
public enum EnumPipeActiveFace implements StringRepresentable {
    NONE(null),
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST);

    @Nullable
    public final Direction face;
    private final String serializedName;

    EnumPipeActiveFace(@Nullable Direction face) {
        this.face = face;
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static EnumPipeActiveFace fromFacing(@Nullable Direction face) {
        if (face == null) {
            return NONE;
        }
        for (EnumPipeActiveFace value : values()) {
            if (value.face == face) {
                return value;
            }
        }
        return NONE;
    }
}
