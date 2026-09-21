/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

/** How a pipe's dye colour is drawn onto its model. */
public enum EnumPipeColourType {
    TRANSLUCENT,
    BORDER_OUTER,
    BORDER_INNER,
    CUSTOM;

    public static final EnumPipeColourType[] VALUES = values();
}
