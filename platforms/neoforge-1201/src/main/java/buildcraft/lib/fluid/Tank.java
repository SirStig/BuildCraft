/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.material.Fluid;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;

/**
 * Trimmed, single-tank port of 1.12.2's {@code Tank}. See the 26.x copy of this class for the full account of what
 * is not ported (the GUI/render/old-network-cache machinery) and why -- identical reasoning applies unchanged
 * here.
 *
 * <p>Unlike 26.x (which has no {@code FluidTank}-shaped base class to extend at all -- see that copy's javadoc),
 * this target keeps a real, direct analogue of 1.12.2's base class, just moved package: confirmed via
 * {@code javap} against the Forge 1.20.1 universal jar, {@code net.minecraftforge.fluids.capability.templates.
 * FluidTank} (not 1.12.2's {@code net.minecraftforge.fluids.FluidTank} -- the package changed, the shape mostly
 * didn't) implements {@code IFluidHandler}/{@code IFluidTank} with the same {@code fill}/{@code drain}/
 * {@code isFluidValid}/{@code getCapacity}/{@code onContentsChanged} surface 1.12.2's version had, modulo the
 * {@code boolean doFill}/{@code doDrain} -> {@link FluidAction} rename already established elsewhere in this
 * port's fluid code (see {@code FluidUtilBC}'s own javadoc). {@code canFillFluidType} is simply
 * {@link #isFluidValid(FluidStack)} now (the modern base already gates {@code fill} through it, so there is
 * nothing left for this class to override in {@code fill} itself); {@code onContentsChanged}'s
 * {@code markChunkDirty()} call becomes a constructor-supplied {@link #onChange} callback, the same replacement
 * the 26.x copy uses.
 *
 * <p>This class still implements {@link IFluidHandlerAdv} (unlike 26.x, which drops the interface entirely --
 * see that copy's javadoc for why): 1.20.1's {@code IFluidHandler} has no transaction-scoped way to introspect a
 * handler's slots from outside, so filtered draining still needs its own method here, exactly like 1.12.2 had.
 *
 * <p>{@link #fillInternal(FluidStack)} is new: {@code TilePump} needs to fill its own tank directly while
 * {@link #setCanFill(boolean)} blocks every externally-driven fill -- see the 26.x copy's javadoc for the full
 * rationale. Forge's {@code FluidTank} has no such bypass of its own (its {@code fill} always checks
 * {@code isFluidValid}), so this writes {@link #fluid} directly instead.
 */
public class Tank extends FluidTank implements IFluidHandlerAdv {

    @Nullable
    private final Runnable onChange;

    private Predicate<FluidStack> filter = stack -> true;
    private boolean canFill = true;

    public Tank(int capacityMb) {
        this(capacityMb, null);
    }

    /** @param onChange Called (if non-null) every time this tank's contents change -- typically the owning
     *            tile's {@code markDirtyAndSync()}. */
    public Tank(int capacityMb, @Nullable Runnable onChange) {
        super(capacityMb);
        this.onChange = onChange;
    }

    public void setFilter(@Nullable Predicate<FluidStack> filter) {
        this.filter = filter == null ? stack -> true : filter;
    }

    /** See the 26.x copy's javadoc for why {@code TilePump} needs this. */
    public void setCanFill(boolean canFill) {
        this.canFill = canFill;
    }

    @Override
    public boolean isFluidValid(FluidStack stack) {
        return canFill && super.isFluidValid(stack) && filter.test(stack);
    }

    @Override
    protected void onContentsChanged() {
        super.onContentsChanged();
        if (onChange != null) {
            onChange.run();
        }
    }

    public boolean isEmpty() {
        return fluid.isEmpty();
    }

    public boolean isFull() {
        return getFluidAmount() >= getCapacity();
    }

    @Nullable
    public Fluid getFluidType() {
        return fluid.isEmpty() ? null : fluid.getFluid();
    }

    /** Directly sets this tank's contents, bypassing {@link #isFluidValid}/{@link #setCanFill}. Adds onto the
     * existing amount when the incoming fluid matches what is already stored, otherwise replaces it outright --
     * see the 26.x copy's javadoc for why. */
    public void fillInternal(FluidStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!fluid.isEmpty() && fluid.isFluidEqual(stack)) {
            fluid.grow(stack.getAmount());
        } else {
            fluid = stack.copy();
        }
        if (fluid.getAmount() > getCapacity()) {
            fluid.setAmount(getCapacity());
        }
        onContentsChanged();
    }

    @Override
    public FluidStack drain(IFluidFilter drainFilter, int maxDrain, FluidAction action) {
        if (drainFilter == null || fluid.isEmpty() || !drainFilter.matches(fluid)) {
            return FluidStack.EMPTY;
        }
        return drain(maxDrain, action);
    }

    public String getDebugString() {
        return getFluidAmount() + " / " + getCapacity() + " mB of " + (fluid.isEmpty() ? "n/a" : fluid.getFluid());
    }

    public String getContentsString() {
        return getDebugString();
    }

    @Override
    public String toString() {
        return "Tank [" + getContentsString() + "]";
    }
}
