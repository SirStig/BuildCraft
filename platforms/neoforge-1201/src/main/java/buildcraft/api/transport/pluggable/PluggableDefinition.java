/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pluggable;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.transport.pipe.IPipeHolder;

/** How one kind of pluggable is identified and reconstructed, from disk or from the network. */
public final class PluggableDefinition {

    public final ResourceLocation identifier;

    public final IPluggableNetLoader loader;
    public final IPluggableNbtReader reader;

    @Nullable
    public final IPluggableCreator creator;

    public PluggableDefinition(ResourceLocation identifier, IPluggableNbtReader reader, IPluggableNetLoader loader) {
        this.identifier = identifier;
        this.reader = reader;
        this.loader = loader;
        this.creator = null;
    }

    public PluggableDefinition(ResourceLocation identifier, IPluggableCreator creator) {
        this.identifier = identifier;
        this.reader = creator;
        this.loader = creator;
        this.creator = creator;
    }

    public PipePluggable readFromNbt(
        IPipeHolder holder,
        Direction side,
        CompoundTag nbt,
        HolderLookup.Provider registries
    ) {
        return reader.readFromNbt(this, holder, side, nbt, registries);
    }

    public PipePluggable loadFromBuffer(IPipeHolder holder, Direction side, FriendlyByteBuf buffer)
        throws InvalidInputDataException {
        return loader.loadFromBuffer(this, holder, side, buffer);
    }

    @FunctionalInterface
    public interface IPluggableNbtReader {
        /**
         * Reads the pipe pluggable from NBT. Unlike {@link IPluggableNetLoader}, which is allowed to fail and
         * throw if the wrong data is given, this should make a best effort to read the pluggable or fall back
         * to sensible defaults.
         */
        PipePluggable readFromNbt(
            PluggableDefinition definition,
            IPipeHolder holder,
            Direction side,
            CompoundTag nbt,
            HolderLookup.Provider registries
        );
    }

    @FunctionalInterface
    public interface IPluggableNetLoader {
        PipePluggable loadFromBuffer(
            PluggableDefinition definition,
            IPipeHolder holder,
            Direction side,
            FriendlyByteBuf buffer
        ) throws InvalidInputDataException;
    }

    /** A pluggable with no state of its own, so neither disk nor network form carries anything. */
    @FunctionalInterface
    public interface IPluggableCreator extends IPluggableNbtReader, IPluggableNetLoader {
        @Override
        default PipePluggable loadFromBuffer(
            PluggableDefinition definition,
            IPipeHolder holder,
            Direction side,
            FriendlyByteBuf buffer
        ) {
            return createSimplePluggable(definition, holder, side);
        }

        @Override
        default PipePluggable readFromNbt(
            PluggableDefinition definition,
            IPipeHolder holder,
            Direction side,
            CompoundTag nbt,
            HolderLookup.Provider registries
        ) {
            return createSimplePluggable(definition, holder, side);
        }

        PipePluggable createSimplePluggable(PluggableDefinition definition, IPipeHolder holder, Direction side);
    }
}
