/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.tile.TileBC;

import buildcraft.BCFactoryRegistries;

/**
 * A block that, stacked vertically with others of its own kind, behaves as one combined fluid reservoir. Mirrors
 * the 26.x class of the same name -- see that one's javadoc for the full account of the column-walking algorithm
 * ({@link #getConnectedTanks()}), the fill/drain direction flip for gaseous fluids, why no ticker exists, and what
 * is dropped from 1.12.2 (render/old-network/GUI machinery).
 *
 * <p>This file differs from 26.x in exactly the way {@code Tank} itself already does (see that class's own
 * javadoc): 1.20.1 keeps a real {@code IFluidHandler} shape, so {@link #tank} is a genuine
 * {@code IFluidHandlerAdv}, and this tile implements {@link IFluidHandlerAdv} directly too -- the same
 * relationship 1.12.2's own {@code TileTank} had to {@code IFluidHandlerAdv}, just with {@code boolean doFill}/
 * {@code doDrain} replaced by {@link FluidAction} throughout. There is no {@code IFluidTankProperties}/
 * {@code FluidTankProperties} on this target at all (confirmed via {@code javap} against the Forge 1.20.1
 * universal jar -- neither type exists any more): the modern {@code IFluidHandler} surface replaces the single
 * {@code getTankProperties()} array with {@code getTanks()}/{@code getFluidInTank(int)}/{@code getTankCapacity
 * (int)}/{@code isFluidValid(int, FluidStack)} directly, so this class implements those four instead, still
 * presenting the whole column as tank slot {@code 0} to match 1.12.2's own "one combined properties entry, however
 * many physical tanks make it up" behaviour.
 *
 * <p><b>The rename from {@code getTanks()} to {@link #getConnectedTanks()} is not optional here</b> (unlike 26.x,
 * where it is just done for consistency): {@link IFluidHandler} itself declares {@code int getTanks()}, an
 * unrelated "how many tank slots does this handler have" query that this class also has to implement -- Java does
 * not allow two same-parameter-list methods differing only in return type, so 1.12.2's private
 * {@code List<TileTank> getTanks()} would collide outright with the interface method of the same name.
 *
 * <p>Exposed through {@link #getCapability}/{@link #invalidateCaps} for {@link ForgeCapabilities#FLUID_HANDLER},
 * the same per-instance pattern {@code TilePump}/{@code TileChute} already established on this target, returning
 * {@code this} rather than a wrapped field.
 */
public class TileTank extends TileBC implements IFluidHandlerAdv, IDebuggable {
    private static final int TANK_CAPACITY = 16 * FluidType.BUCKET_VOLUME;

    public final Tank tank = new Tank(TANK_CAPACITY, this::onTankChanged);
    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> this);

    public TileTank(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.TANK_TYPE.get(), pos, state);
    }

    private void onTankChanged() {
        markDirtyAndSync();
    }

    public int getComparatorLevel() {
        int amount = tank.getFluidAmount();
        int cap = tank.getCapacity();
        return amount * 14 / cap + (amount > 0 ? 1 : 0);
    }

    /** Driven by {@link buildcraft.factory.block.BlockTank#setPlacedBy}. See the 26.x copy of this class for why
     * this re-settles the whole column immediately. */
    public void onPlacedBy() {
        balanceTankFluids();
    }

    /** Moves fluids around to their preferred positions. (For gaseous fluids this will move everything as high as
     * possible, for liquid fluids this will move everything as low as possible.) */
    public void balanceTankFluids() {
        List<TileTank> tanks = getConnectedTanks();
        FluidStack fluid = FluidStack.EMPTY;
        for (TileTank tile : tanks) {
            FluidStack held = tile.tank.getFluid();
            if (held.isEmpty()) {
                continue;
            }
            if (fluid.isEmpty()) {
                fluid = held;
            } else if (!fluid.isFluidEqual(held)) {
                return;
            }
        }
        if (fluid.isEmpty()) {
            return;
        }
        if (isGaseous(fluid)) {
            Collections.reverse(tanks);
        }
        TileTank prev = null;
        for (TileTank tile : tanks) {
            if (prev != null) {
                FluidUtilBC.move(tile.tank, prev.tank);
            }
            prev = tile;
        }
    }

    /** See the 26.x copy's javadoc: no {@code Fluid#isGaseous(FluidStack)}-shaped method exists on this target
     * either (confirmed via {@code javap} against {@code Fluid} on the Forge 1.20.1 merged jar), so this uses the
     * same negative-{@code FluidType#getDensity()} convention {@code TilePump} already established here. */
    private static boolean isGaseous(FluidStack stack) {
        return stack.getFluid().getFluidType().getDensity() < 0;
    }

    // Tank helper methods

    /** See the 26.x copy's javadoc. */
    public boolean canConnectTo(TileTank other, Direction direction) {
        return true;
    }

    /** See the 26.x copy's javadoc. */
    public static boolean canTanksConnect(TileTank from, TileTank to, Direction direction) {
        return from.canConnectTo(to, direction) && to.canConnectTo(from, direction.getOpposite());
    }

    /** @return A list of all connected tanks around this block, ordered by position from bottom to top. See the
     *         class javadoc for why this is not simply called {@code getTanks()}. */
    private List<TileTank> getConnectedTanks() {
        Deque<TileTank> tanks = new ArrayDeque<>();
        tanks.add(this);
        TileTank prevTank = this;
        while (true) {
            BlockEntity tileAbove = level.getBlockEntity(prevTank.worldPosition.above());
            if (!(tileAbove instanceof TileTank tankUp) || !canTanksConnect(prevTank, tankUp, Direction.UP)) {
                break;
            }
            tanks.addLast(tankUp);
            prevTank = tankUp;
        }
        prevTank = this;
        while (true) {
            BlockEntity tileBelow = level.getBlockEntity(prevTank.worldPosition.below());
            if (!(tileBelow instanceof TileTank tankDown) || !canTanksConnect(prevTank, tankDown, Direction.DOWN)) {
                break;
            }
            tanks.addFirst(tankDown);
            prevTank = tankDown;
        }
        return new ArrayList<>(tanks);
    }

    // IFluidHandler

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tankIndex) {
        List<TileTank> tanks = getConnectedTanks();
        FluidStack total = tanks.get(0).tank.getFluid();
        if (total.isEmpty()) {
            total = tanks.get(tanks.size() - 1).tank.getFluid();
        }
        if (total.isEmpty()) {
            return FluidStack.EMPTY;
        }
        total = total.copy();
        total.setAmount(0);
        for (TileTank t : tanks) {
            FluidStack other = t.tank.getFluid();
            if (!other.isEmpty()) {
                total.grow(other.getAmount());
            }
        }
        return total;
    }

    @Override
    public int getTankCapacity(int tankIndex) {
        int capacity = 0;
        for (TileTank t : getConnectedTanks()) {
            capacity += t.tank.getCapacity();
        }
        return capacity;
    }

    @Override
    public boolean isFluidValid(int tankIndex, FluidStack stack) {
        for (TileTank t : getConnectedTanks()) {
            FluidStack current = t.tank.getFluid();
            if (!current.isEmpty() && !current.isFluidEqual(stack)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        List<TileTank> tanks = getConnectedTanks();
        for (TileTank t : tanks) {
            FluidStack current = t.tank.getFluid();
            if (!current.isEmpty() && !current.isFluidEqual(resource)) {
                return 0;
            }
        }
        if (isGaseous(resource)) {
            Collections.reverse(tanks);
        }
        FluidStack toFill = resource.copy();
        int filled = 0;
        for (TileTank t : tanks) {
            int tankFilled = t.tank.fill(toFill, action);
            if (tankFilled > 0) {
                toFill.shrink(tankFilled);
                filled += tankFilled;
                if (toFill.isEmpty()) {
                    break;
                }
            }
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return drain(resource::isFluidEqual, resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return drain(fluid -> true, maxDrain, action);
    }

    // IFluidHandlerAdv

    @Override
    public FluidStack drain(IFluidFilter filter, int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        List<TileTank> tanks = getConnectedTanks();
        boolean gas = false;
        for (TileTank tile : tanks) {
            FluidStack fluid = tile.tank.getFluid();
            if (!fluid.isEmpty()) {
                gas = isGaseous(fluid);
                break;
            }
        }
        if (!gas) {
            Collections.reverse(tanks);
        }
        FluidStack total = FluidStack.EMPTY;
        for (TileTank t : tanks) {
            int realMax = maxDrain - total.getAmount();
            if (realMax <= 0) {
                break;
            }
            FluidStack drained = t.tank.drain(filter, realMax, action);
            if (drained.isEmpty()) {
                continue;
            }
            if (total.isEmpty()) {
                total = drained.copy();
                total.setAmount(0);
            }
            total.grow(drained.getAmount());
        }
        return total;
    }

    // TileEntity

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        tank.readFromNBT(nbt.getCompound("tank"));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("tank", tank.writeToNBT(new CompoundTag()));
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("fluid = " + tank.getDebugString());
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
    }
}
