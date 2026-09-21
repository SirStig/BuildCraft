/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */

package buildcraft.api.core;

/**
 * The order in which registered handlers are consulted. {@link #HIGHEST} runs first.
 *
 * <p>Unchanged from 1.12.2: this is plain Java, and the iteration order the callers rely on is the declaration
 * order, so the constants must stay in this sequence.
 */
public enum EnumHandlerPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST;

    public static final EnumHandlerPriority[] VALUES = values();
}
