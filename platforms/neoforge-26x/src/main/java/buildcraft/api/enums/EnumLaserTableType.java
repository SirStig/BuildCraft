/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.enums;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * The tables a laser can power.
 *
 * <p>1.12.2's {@code getName()} returned {@code name()} -- upper case with underscores. A blockstate property value
 * has to be lower case now, or the blockstate JSON cannot name it, so this returns the lower-case form. The
 * blockstate and model files for these tables have to be written to match.
 */
public enum EnumLaserTableType implements StringRepresentable {
    ASSEMBLY_TABLE,
    ADVANCED_CRAFTING_TABLE,
    INTEGRATION_TABLE,
    CHARGING_TABLE,
    PROGRAMMING_TABLE;

    public static final EnumLaserTableType[] VALUES = values();

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
