/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

/** When a {@link PipeEventHandler} runs relative to BuildCraft's own handlers, which all use {@link #NORMAL}. */
public enum PipeEventPriority {
    PRE,
    NORMAL,
    POST;

    public static final PipeEventPriority[] VALUES = values();
}
