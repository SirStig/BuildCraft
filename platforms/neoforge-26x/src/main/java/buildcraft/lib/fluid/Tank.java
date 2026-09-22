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

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * Trimmed, single-slot port of 1.12.2's 369-line {@code Tank}: capacity/fill/drain/filter, NBT round-trip and the
 * debug-string helpers, matching the "port only what's called" discipline already established for
 * {@code ItemHandlerSimple}/{@code BlockUtil}. Not ported: {@code onGuiClicked}/{@code transferStackToTank}/
 * {@code map}/{@code FluidGetResult} (all {@code ContainerBC_Neptune}-dependent, and there is still no GUI/
 * container framework anywhere in this port), {@code writeToBuffer}/{@code readFromBuffer}/{@code clientFluid}/
 * {@code clientAmount}/{@code getFluidForRender}/{@code colorRenderCache}/{@code refreshTooltip}/{@code toolTip}/
 * {@code helpInfo}/{@code ElementHelpInfo} (old-network-cache/tooltip machinery; {@code TileMiningWell}'s dropped
 * render fields used the same reasoning). Now that {@code buildcraft.factory.tile.RenderTileTank} exists, note that
 * it does <em>not</em> resurrect {@code clientFluid}/{@code clientAmount}/{@code getFluidForRender} -- it reads
 * {@link #getResource(int)}/{@link #getAmountAsInt(int)} directly every frame instead, since {@link #onChange}
 * already fires a full sync on every content change (see that renderer's own javadoc). {@code onContentsChanged}'s
 * {@code markChunkDirty()} call becomes a caller-supplied {@link #onChange}
 * callback instead ({@code TileBC#markDirtyAndSync()}, wired up by whichever tile owns the tank) -- this class has
 * no {@code TileEntity} reference of its own to call back into any more, unlike 1.12.2's constructor-injected
 * {@code tile} field.
 *
 * <p><b>Real per-platform design, not a mechanical trim.</b> Confirmed via {@code javap} against the real merged
 * jar: {@code net.neoforged.neoforge.fluids.FluidTank} (the direct rename target 1.12.2's own
 * {@code net.minecraftforge.fluids.FluidTank} superclass would suggest) does not exist on this target at all --
 * "class not found". What NeoForge actually ships in its place is {@code FluidStacksResourceHandler}, the fluid
 * counterpart of {@link buildcraft.lib.tile.item.ItemHandlerSimple}'s own {@code ItemStacksResourceHandler} base
 * (same {@code StacksResourceHandler<S, T extends Resource>} family, confirmed via {@code javap}): a real,
 * ready-made single-or-multi-slot {@link net.neoforged.neoforge.transfer.ResourceHandler}{@code <}
 * {@link FluidResource}{@code >} implementation with transaction-safe {@code insert}/{@code extract} and
 * {@code ValueIOSerializable} NBT persistence already built in, needing only a slot count and a capacity. This
 * class is therefore a one-slot {@code FluidStacksResourceHandler}, exactly the same relationship
 * {@code ItemHandlerSimple} already has to its own base -- not a from-scratch {@code ResourceHandler}
 * implementation, which the capacity trimming for this port originally assumed would be necessary before this
 * base class was found.
 *
 * <p>{@code canFillFluidType}/{@code fill(FluidStack, boolean)} become {@link #isValid(int, FluidResource)},
 * called automatically by the inherited {@code insert} -- the same "peek what's already correct" delegation
 * {@code ItemHandlerSimple#isValid} already uses. There is no {@code drain(IFluidFilter, ...)} override here the
 * way 1.20.1's copy of this class needs: {@code IFluidHandlerAdv}'s whole reason to exist (filtered extraction) is
 * already covered by this target's {@code ResourceHandler} being slot-introspectable from the outside -- see
 * {@code IFluidFilter}'s own class javadoc, and {@link buildcraft.lib.misc.FluidUtilBC#move} for the worked
 * example. {@link #fillInternal(FluidStack)} is the one genuinely new method: 1.12.2's {@code FluidTank} exposed a
 * {@code fillInternal} that bypassed {@code canFill}/the validator for a tile's own internal production (what
 * {@code TilePump} needs when it mines fluid into a tank whose {@link #setCanFill} is otherwise {@code false});
 * {@code StacksResourceHandler}'s public {@code set(int, T, int)} is the direct modern equivalent of that same
 * unchecked write.
 */
public class Tank extends FluidStacksResourceHandler {

    @Nullable
    private final Runnable onChange;

    private Predicate<FluidResource> filter = fluid -> true;
    private boolean canFill = true;

    public Tank(int capacityMb) {
        this(capacityMb, null);
    }

    /** @param onChange Called (if non-null) every time this tank's contents change -- typically the owning tile's
     *            {@code markDirtyAndSync()}. Replaces 1.12.2's {@code onContentsChanged}/{@code markChunkDirty}
     *            pair; see the class javadoc. */
    public Tank(int capacityMb, @Nullable Runnable onChange) {
        super(1, capacityMb);
        this.onChange = onChange;
    }

    public void setFilter(@Nullable Predicate<FluidResource> filter) {
        this.filter = filter == null ? fluid -> true : filter;
    }

    /** Was 1.12.2's {@code FluidTank#setCanFill} -- blocks external (capability-driven) fills while leaving
     * {@link #fillInternal(FluidStack)} unaffected, exactly what {@code TilePump} needs: it drains fluid into its
     * own tank directly, but never wants a neighbour pouring a bucket back in through the exposed capability. */
    public void setCanFill(boolean canFill) {
        this.canFill = canFill;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return canFill && filter.test(resource);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        super.onContentsChanged(index, previousContents);
        if (onChange != null) {
            onChange.run();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public FluidResource getFluidType() {
        return getResource(0);
    }

    public boolean isEmpty() {
        return getResource(0).isEmpty();
    }

    public boolean isFull() {
        return getAmountAsInt(0) >= capacity;
    }

    /** Directly sets this tank's contents, bypassing {@link #isValid}/{@link #setCanFill} -- see the class
     * javadoc for why {@code TilePump} needs this. Adds onto the existing amount when the incoming fluid matches
     * what is already stored (mirrors {@code FluidTank#fill}'s own "same fluid stacks" behaviour); otherwise
     * replaces the tank's contents outright, which in practice only happens if a caller drains the tank empty and
     * starts filling a different fluid, since {@code TilePump}'s search only ever queues one fluid type at a
     * time. */
    public void fillInternal(FluidStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        FluidResource resource = FluidResource.of(stack);
        FluidResource current = getResource(0);
        int existing = current.isEmpty() || current.equals(resource) ? getAmountAsInt(0) : 0;
        set(0, resource, Math.min(capacity, existing + stack.getAmount()));
    }

    public String getDebugString() {
        FluidResource f = getResource(0);
        return getAmountAsInt(0) + " / " + capacity + " mB of " + (f.isEmpty() ? "n/a" : f.getFluid());
    }

    public String getContentsString() {
        FluidResource f = getResource(0);
        if (f.isEmpty()) {
            return "0 / " + capacity + " mB";
        }
        return f.getFluid() + " " + getAmountAsInt(0) + " / " + capacity + " mB";
    }

    @Override
    public String toString() {
        return "Tank [" + getContentsString() + "]";
    }
}
