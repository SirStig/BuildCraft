/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.enums;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/** How hot an engine is running, which drives both its model and whether it is about to explode. */
public enum EnumPowerStage implements StringRepresentable {
    BLUE,
    GREEN,
    YELLOW,
    RED,
    OVERHEAT,
    BLACK;

    public static final EnumPowerStage[] VALUES = values();

    private final String modelName = name().toLowerCase(Locale.ROOT);

    public String getModelName() {
        return modelName;
    }

    @Override
    public String getSerializedName() {
        return modelName;
    }
}
