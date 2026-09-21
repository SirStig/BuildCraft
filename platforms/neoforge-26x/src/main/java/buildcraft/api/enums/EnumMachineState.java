/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.enums;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.properties.BuildCraftProperties;

/**
 * Whether a machine is idle, working, or finished.
 *
 * <p>As with the other property enums, {@code getName()} returned the upper-case constant name in 1.12.2. A
 * blockstate property value must be lower case, so this returns the lower-case form and the blockstate JSON has to
 * match.
 */
public enum EnumMachineState implements StringRepresentable {
    OFF,
    ON,
    DONE;

    public static final EnumMachineState[] VALUES = values();

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    public static EnumMachineState getType(BlockState state) {
        return state.getValue(BuildCraftProperties.MACHINE_STATE);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
