/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.IPipeRegistry;
import buildcraft.api.transport.pipe.PipeDefinition;

/**
 * Every {@link PipeDefinition} registered, and the {@link IItemPipe} each maps to. A direct port of 1.12.2's own
 * {@code PipeRegistry}. See the 26.x copy of this class for the full account of why
 * {@link #createItemForPipe}/{@link #createUnnamedItemForPipe} throw rather than dynamically registering a new
 * item -- the same reasoning applies unchanged here: 1.20.1's {@code DeferredRegister} freezes its registry once
 * every mod's register event has fired, exactly like 26.x's own.
 */
public enum PipeRegistry implements IPipeRegistry {
    INSTANCE;

    private final Map<ResourceLocation, PipeDefinition> definitions = new HashMap<>();
    private final Map<PipeDefinition, IItemPipe> pipeItems = new IdentityHashMap<>();

    @Override
    public void registerPipe(PipeDefinition definition) {
        definitions.put(definition.identifier, definition);
    }

    @Override
    public void setItemForPipe(PipeDefinition definition, @Nullable IItemPipe item) {
        if (definition == null) {
            throw new NullPointerException("definition");
        }
        if (item == null) {
            pipeItems.remove(definition);
        } else {
            pipeItems.put(definition, item);
        }
    }

    @Override
    public IItemPipe createItemForPipe(PipeDefinition definition) {
        throw new UnsupportedOperationException(
            "Cannot dynamically register a new pipe item at runtime on this target: registries are frozen once"
                + " every mod's register event has fired, and this method has no registrar to add one to anyway."
                + " Register " + definition.identifier + "'s item through a BCRegistry/DeferredRegister pass up"
                + " front instead, the way BCTransportRegistries registers the cobblestone pipe's item."
        );
    }

    @Override
    public IItemPipe createUnnamedItemForPipe(PipeDefinition definition, Consumer<Item> postCreate) {
        throw new UnsupportedOperationException(
            "Cannot dynamically register a new pipe item at runtime on this target -- see createItemForPipe's own"
                + " javadoc for why. Register " + definition.identifier + "'s item up front instead."
        );
    }

    @Override
    public IItemPipe getItemForPipe(PipeDefinition definition) {
        return pipeItems.get(definition);
    }

    @Override
    @Nullable
    public PipeDefinition getDefinition(ResourceLocation identifier) {
        return definitions.get(identifier);
    }

    public PipeDefinition loadDefinition(String identifier) throws InvalidInputDataException {
        PipeDefinition def = getDefinition(new ResourceLocation(identifier));
        if (def == null) {
            throw new InvalidInputDataException("Unknown pipe definition " + identifier);
        }
        return def;
    }

    @Override
    public Iterable<PipeDefinition> getAllRegisteredPipes() {
        return ImmutableList.copyOf(definitions.values());
    }
}
