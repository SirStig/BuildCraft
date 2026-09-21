/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** What a pipe carries: items, fluids, power. */
public final class PipeFlowType {

    public final IFlowCreator creator;
    public final IFlowLoader loader;

    /**
     * The default colour type, if none is given in {@link PipeDefinition}. If this is also null then the final
     * fallback is {@link EnumPipeColourType#TRANSLUCENT}.
     */
    @Nullable
    public EnumPipeColourType fallbackColourType;

    public PipeFlowType(IFlowCreator creator, IFlowLoader loader) {
        this(creator, loader, null);
    }

    public PipeFlowType(IFlowCreator creator, IFlowLoader loader, @Nullable EnumPipeColourType colourType) {
        this.creator = creator;
        this.loader = loader;
        this.fallbackColourType = colourType;
    }

    @FunctionalInterface
    public interface IFlowCreator {
        PipeFlow createFlow(IPipe pipe);
    }

    @FunctionalInterface
    public interface IFlowLoader {
        /**
         * {@code NBTTagCompound} is {@link CompoundTag}, and a {@link HolderLookup.Provider} is threaded
         * through because anything reading an item or fluid back out needs registry access on this target.
         */
        PipeFlow loadFlow(IPipe pipe, CompoundTag tag, HolderLookup.Provider registries);
    }
}
