/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.transport.pluggable.PipePluggable;

/**
 * A pipe flow that carries fluids.
 *
 * <p>1.12.2 had two extraction methods and two deprecated overloads of them -- four in all. Both halves of
 * that are gone:
 *
 * <ul>
 * <li>The deprecated {@code simulate}-less overloads are dropped; simulation is a transaction now, so there is
 *     no version of these that does not take one.</li>
 * <li>{@code tryExtractFluidAdv} existed only because 1.12's {@code IFluidHandler.drain} could be told one
 *     exact fluid or anything, but not "any of these" -- so filtered extraction needed tanks to implement
 *     BuildCraft's extra {@code IFluidHandlerAdv}, and the method had to return {@code PASS} when they did
 *     not. {@link ResourceHandler} exposes the resource in each slot, so filtering works against any handler
 *     (see {@code buildcraft.api.core.FluidFilters}) and the {@code PASS} case cannot arise. The two methods
 *     collapse into one that takes an {@link IFluidFilter}, with a null filter meaning any fluid.</li>
 * </ul>
 */
public interface IFlowFluid {

    /**
     * Extracts fluid from the tank on the given side and inserts it into the pipe.
     *
     * @param millibuckets The maximum amount to extract.
     * @param from The side to extract from.
     * @param filter The filter the extracted fluid must match, or null for any fluid.
     * @param transaction The enclosing transaction. This method never commits.
     * @return What was extracted and inserted into the pipe, or null if nothing was.
     */
    @Nullable
    ResourceStack<FluidResource> tryExtractFluid(
        int millibuckets,
        Direction from,
        @Nullable IFluidFilter filter,
        TransactionContext transaction
    );

    /**
     * Attempts to insert a fluid directly into the pipe. This fails if the pipe currently contains a different
     * fluid type.
     *
     * @param from The side the fluid should <em>not</em> go in, or null if it may flow in any direction.
     * @param transaction The enclosing transaction. This method never commits.
     * @return The amount of fluid that was accepted, or 0 if none was.
     */
    int insertFluidsForce(
        FluidResource fluid,
        int amount,
        @Nullable Direction from,
        TransactionContext transaction
    );

    /**
     * Tries to extract fluids directly from the pipe.
     *
     * <p>NOTE: this is intended for {@link PipeBehaviour} and {@link PipePluggable} implementors ONLY. It will
     * behave very buggily if external blocks try to use it.
     *
     * @param min The minimum amount to extract. If the given section holds less than this, nothing is
     *            extracted.
     * @param section The section to extract from. Null means the centre.
     * @param transaction The enclosing transaction. This method never commits.
     * @return What was extracted, or null if nothing was.
     */
    @Nullable
    ResourceStack<FluidResource> extractFluidsForce(
        int min,
        int max,
        @Nullable Direction section,
        TransactionContext transaction
    );
}
