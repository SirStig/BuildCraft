/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.transport.pluggable.PipePluggable;

/**
 * A pipe flow that carries fluids.
 *
 * <p>The deprecated {@code simulate}-less overloads from 1.12.2 are dropped on both targets; there was no
 * reason to carry a second spelling of each method through a rewrite.
 *
 * <p>Unlike the 26.x copy, the filtered extraction stays a separate method here. It exists because
 * {@code IFluidHandler.drain} can be told one exact fluid or anything, but not "any of these", so filtering
 * still needs the tank to implement {@link IFluidHandlerAdv} -- and therefore still needs a way to say "that
 * tank does not, ask the basic method instead", which is what a null return means. 26.x can filter against any
 * handler, so there the two methods collapse into one.
 */
public interface IFlowFluid {

    /**
     * Extracts fluid from the tank on the given side and inserts it into the pipe.
     *
     * @param filter The fluid stack the extracted fluid must match, or null for any fluid.
     * @return The fluid extracted and inserted into the pipe, or null if none was.
     */
    @Nullable
    FluidStack tryExtractFluid(int millibuckets, Direction from, @Nullable FluidStack filter, boolean simulate);

    /**
     * Filtered version of {@link #tryExtractFluid}, which only works for tanks implementing
     * {@link IFluidHandlerAdv}.
     *
     * @param filter A filter to match fluids against.
     * @return The fluid extracted and inserted into the pipe; an empty stack if the tank had nothing matching,
     *         or null if the tank does not implement {@link IFluidHandlerAdv} and you should call
     *         {@link #tryExtractFluid} instead.
     *
     *         <p>1.12.2 signalled that last case with {@code ActionResult}'s {@code PASS}. A nullable return
     *         says the same thing without the wrapper, and {@code ActionResult} has itself been renamed and
     *         reworked since.
     */
    @Nullable
    FluidStack tryExtractFluidAdv(int millibuckets, Direction from, IFluidFilter filter, boolean simulate);

    /**
     * Attempts to insert a fluid directly into the pipe. This fails if the pipe currently contains a different
     * fluid type.
     *
     * @param from The side the fluid should <em>not</em> go in, or null if it may flow in any direction.
     * @return The amount of fluid that was accepted, or 0 if none was.
     */
    int insertFluidsForce(FluidStack fluid, @Nullable Direction from, boolean simulate);

    /**
     * Tries to extract fluids directly from the pipe.
     *
     * <p>NOTE: this is intended for {@link PipeBehaviour} and {@link PipePluggable} implementors ONLY. It will
     * behave very buggily if external blocks try to use it.
     *
     * @param min The minimum amount to extract. If the given section holds less than this, nothing is
     *            extracted.
     * @param section The section to extract from. Null means the centre.
     * @return The fluid extracted, or null if none was.
     */
    @Nullable
    FluidStack extractFluidsForce(int min, int max, @Nullable Direction section, boolean simulate);
}
