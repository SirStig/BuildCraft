/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.enums;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.StringRepresentable;

/**
 * Version of {@link EnumSnapshotType} with a {@link #NONE} value, for the blockstate property on blocks that may
 * or may not hold a snapshot. Prefer {@link EnumSnapshotType} wherever the absent case cannot occur.
 */
public enum EnumOptionalSnapshotType implements StringRepresentable {
    NONE(null),
    TEMPLATE(EnumSnapshotType.TEMPLATE),
    BLUEPRINT(EnumSnapshotType.BLUEPRINT);

    public static final EnumOptionalSnapshotType[] VALUES = values();

    @Nullable
    public final EnumSnapshotType type;

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    EnumOptionalSnapshotType(@Nullable EnumSnapshotType type) {
        this.type = type;
    }

    public static EnumOptionalSnapshotType fromNullable(@Nullable EnumSnapshotType type) {
        if (type == null) {
            return NONE;
        }
        return switch (type) {
            case TEMPLATE -> TEMPLATE;
            case BLUEPRINT -> BLUEPRINT;
        };
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
