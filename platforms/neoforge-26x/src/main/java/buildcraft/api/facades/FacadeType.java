/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.facades;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * Whether a facade shows one block or switches between several on a gate signal.
 *
 * <p>The constants were {@code Basic} and {@code Phased} in 1.12.2 -- mixed case, unusually for an enum. They
 * are upper case now so they can serialise to a lower-case name like every other enum in the API.
 */
public enum FacadeType implements StringRepresentable {
    BASIC,
    PHASED;

    public static final FacadeType[] VALUES = values();

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    public static FacadeType fromOrdinal(int ordinal) {
        return ordinal == 1 ? PHASED : BASIC;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
