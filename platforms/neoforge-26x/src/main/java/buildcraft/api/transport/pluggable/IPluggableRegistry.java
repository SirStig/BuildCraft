/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pluggable;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;

/** Every pluggable kind BuildCraft and other mods have registered. */
public interface IPluggableRegistry {
    default void register(PluggableDefinition definition) {
        register(definition.identifier, definition);
    }

    void register(Identifier identifier, PluggableDefinition definition);

    @Nullable
    PluggableDefinition getDefinition(Identifier identifier);
}
