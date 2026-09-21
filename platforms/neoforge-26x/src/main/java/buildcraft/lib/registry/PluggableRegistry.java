/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;

import buildcraft.api.transport.pluggable.IPluggableRegistry;
import buildcraft.api.transport.pluggable.PluggableDefinition;

/**
 * The concrete {@link IPluggableRegistry}.
 *
 * <p>{@link ConcurrentHashMap} rather than {@link HashMap}: mod loading is parallel now, so two mods
 * registering a pluggable at the same time is ordinary rather than impossible.
 */
public enum PluggableRegistry implements IPluggableRegistry {
    INSTANCE;

    private final Map<Identifier, PluggableDefinition> registered = new ConcurrentHashMap<>();

    @Override
    public void register(Identifier id, PluggableDefinition definition) {
        registered.put(id, definition);
    }

    @Override
    @Nullable
    public PluggableDefinition getDefinition(Identifier identifier) {
        return registered.get(identifier);
    }
}
