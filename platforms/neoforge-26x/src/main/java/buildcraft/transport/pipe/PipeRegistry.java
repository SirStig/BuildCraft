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

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.IPipeRegistry;
import buildcraft.api.transport.pipe.PipeDefinition;

/**
 * Every {@link PipeDefinition} registered, and the {@link IItemPipe} each maps to. A direct port of 1.12.2's own
 * {@code PipeRegistry}.
 *
 * <p>{@link #setItemForPipe}/{@link #getItemForPipe}/{@link #getDefinition}/{@link #getAllRegisteredPipes} are
 * real, faithful ports -- {@code buildcraft.transport.item.ItemPipeHolder}'s own constructor calls
 * {@link #setItemForPipe} on itself once {@code BCTransportRegistries}' normal {@code DeferredRegister.Items}
 * pass constructs it, the same self-registering shape 1.12.2's {@code ItemPipeHolder#registerWithPipeApi} used.
 *
 * <p>{@link #createItemForPipe}/{@link #createUnnamedItemForPipe} are a genuine, deliberate divergence, not a
 * faked-out stub: 1.12.2's versions could register a brand new item at essentially any point during FML's
 * {@code preInit}, because {@code RegistrationHelper.addForcedItem} pushed straight into the vanilla item
 * registry, which was still open. Registries on this target are populated up front through
 * {@code DeferredRegister} and then frozen once every mod's {@code RegisterEvent} has fired; nothing can add a
 * new registry entry afterwards, and there is no event-bus/registrar handle available inside this call for it to
 * even attempt to hook an earlier phase. Rather than pretend to support "create a pipe item whenever you like"
 * and silently produce an item that was never actually registered (which would crash the moment anything tried
 * to place it), both methods throw, explaining why, and pointing at the real, supported path: register a new
 * {@link PipeDefinition}'s item through a module's own {@code BCRegistry}/{@code DeferredRegister.Items} up
 * front, the same way {@code BCTransportRegistries} registers the cobblestone pipe's item.
 */
public enum PipeRegistry implements IPipeRegistry {
    INSTANCE;

    private final Map<Identifier, PipeDefinition> definitions = new HashMap<>();
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
                + " every mod's RegisterEvent has fired, and this method has no registrar to add one to anyway."
                + " Register " + definition.identifier + "'s item through a BCRegistry/DeferredRegister.Items"
                + " pass up front instead, the way BCTransportRegistries registers the cobblestone pipe's item."
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
    public PipeDefinition getDefinition(Identifier identifier) {
        return definitions.get(identifier);
    }

    public PipeDefinition loadDefinition(String identifier) throws InvalidInputDataException {
        PipeDefinition def = getDefinition(Identifier.parse(identifier));
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
