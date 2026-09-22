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

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.tile.TileBC;

import buildcraft.BCFactoryRegistries;

/**
 * A block that, stacked vertically with others of its own kind, behaves as one combined fluid reservoir: filling
 * the bottom tank spills upward once it is full, draining pulls from the top down (or the bottom up, for a gas),
 * and every tank in the column reports the whole column's combined contents to a capability query landing on any
 * one of them. 1.12.2's {@code TileTank} implemented {@code IFluidHandlerAdv} on the tile itself for exactly this
 * reason -- a query never sees just one physical tank's contents. This class does the modern equivalent: it
 * implements {@link ResourceHandler}{@code <}{@link FluidResource}{@code >} directly, as a single logical slot
 * ({@link #size()} {@code == 1}) that internally fans every read/write out across the whole connected column.
 * {@link #tank} (a plain {@link Tank}, one physical slot) is what actually holds *this* block's own share of the
 * fluid and gets serialised to NBT -- the aggregate view is assembled fresh from every tile's {@link #tank} field
 * on each call, never cached.
 *
 * <p><b>The column walk ({@link #getConnectedTanks()}) is the genuinely novel part of this machine.</b> Starting
 * from {@code this}, it walks upward one block at a time as long as the neighbour above is also a {@code TileTank}
 * and {@link #canTanksConnect} agrees, then does the same walking downward, and returns the whole run bottom to
 * top. Unlike a flood-fill (which would need to track visited positions and branch in a full 3D neighbourhood),
 * this only ever looks straight up and down: a tank stack is a 1-wide column by definition, so "the connected
 * tanks" is just "how far does this vertical line of {@code TileTank} blocks extend." Every fluid-handling method
 * below ({@link #getResource}, {@link #getAmountAsLong}, {@link #insert}, {@link #extract},
 * {@link #balanceTankFluids}) starts by calling this, then spreads its work across the resulting list -- so
 * filling the bottom tank of an empty three-tall stack correctly lets fluid pour through into the tanks above it
 * once the bottom one is full, and draining from the top tile of that same stack correctly pulls fluid up from
 * whichever physical tank actually still holds it.
 *
 * <p>Fill and drain both walk the column in a direction that depends on whether the fluid is a gas: a liquid
 * settles toward the bottom of the stack (so drain pulls from the top down, preferring to empty the tiles that
 * would otherwise leave fluid stranded above an empty one, and fill packs the bottom tiles first), while a gas
 * rises, so both directions flip. {@link #isGaseous(FluidResource)} reuses the exact convention {@code TilePump}
 * already established for this target: confirmed via {@code javap} against {@code FluidType}, there is no
 * {@code isGaseous}-shaped method on this target at all, so a negative {@link FluidType#getDensity()} stands in
 * for it, as it does everywhere else in this port that needs the same test.
 *
 * <p><b>Renamed from 1.12.2's private {@code getTanks()}.</b> Not required on this target (nothing here collides
 * with it), but 1.20.1's copy of this class *must* rename it -- {@code IFluidHandler} itself declares a same-
 * signature {@code int getTanks()} there, an unrelated "how many tank slots does this handler have" query, and
 * Java does not allow two same-parameter-list methods that differ only in return type. Both copies use
 * {@link #getConnectedTanks()} so the two files read the same way.
 *
 * <p><b>Everything render/old-network/GUI-only is dropped</b>, matching every precedent already established in
 * this pass ({@code TilePump}, {@code TileChute}): {@code FluidSmoother}/{@code smoothedTank}/
 * {@code getFluidForRender} (client-side fluid-level interpolation with no renderer in this port to consume it),
 * the id-tagged {@code writePayload}/{@code readPayload}/{@code NET_FLUID_DELTA} network-cache system (NeoForge's
 * own sync already covers what is left), and {@code onActivated}'s two behaviours -- {@code FluidUtilBC
 * .onTankActivated} (still not ported anywhere; see that class's own javadoc for why) and
 * {@code BCFactoryGuis.TANK.openGUI} (no GUI/container framework exists yet, the same "first genuinely new GUI
 * deferral" reasoning {@code BlockChute}'s dropped {@code onBlockActivated} already established). Right-clicking a
 * tank is simply a no-op for now, matching {@code BlockChute}.
 *
 * <p><b>No per-tick work.</b> 1.12.2's {@code update()} had two jobs: tick {@code smoothedTank} (dropped, see
 * above) and re-check the comparator level once a tick, calling {@code markDirty()} again if it had changed --
 * but {@code Tank}'s own {@code onContentsChanged} already calls {@code markChunkDirty()} on *every* content
 * change regardless of whether the comparator level actually moved, so that tick-polled recheck was already
 * redundant in 1.12.2 itself once a fill/drain had happened at all. Wiring {@link Tank}'s {@link Tank#Tank(int,
 * Runnable) onChange} callback straight to {@link #markDirtyAndSync()} (the {@code onTankChanged} constructor
 * reference below) reproduces the one behaviour that callback ever actually caused, without a ticker: no
 * {@code getTicker} override exists on {@link buildcraft.factory.block.BlockTank} at all.
 *
 * <p><b>The comparator hook ({@code getComparatorLevel()}) is unchanged 1.12.2 math</b>, reading this tile's own
 * (not the whole column's) {@link #tank} -- a stacked column's comparator output is per physical block, matching
 * 1.12.2's own {@code TileTank#getComparatorLevel}. See {@link buildcraft.factory.block.BlockTank}'s own javadoc
 * for the modern hook names this feeds.
 */
public class TileTank extends TileBC implements ResourceHandler<FluidResource>, IDebuggable {
    private static final int TANK_CAPACITY = 16 * FluidType.BUCKET_VOLUME;

    public final Tank tank = new Tank(TANK_CAPACITY, this::onTankChanged);

    public TileTank(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.TANK_TYPE.get(), pos, state);
    }

    private void onTankChanged() {
        markDirtyAndSync();
    }

    public int getComparatorLevel() {
        int amount = tank.getAmountAsInt(0);
        int cap = tank.getCapacity();
        return amount * 14 / cap + (amount > 0 ? 1 : 0);
    }

    /** Driven by {@link buildcraft.factory.block.BlockTank#setPlacedBy} -- re-settles the whole column's fluid
     * distribution the moment a new tank is slotted into (or onto) an existing stack. */
    public void onPlacedBy() {
        balanceTankFluids();
    }

    /** Moves fluids around to their preferred positions. (For gaseous fluids this will move everything as high as
     * possible, for liquid fluids this will move everything as low as possible.) */
    public void balanceTankFluids() {
        List<TileTank> tanks = getConnectedTanks();
        FluidResource fluid = FluidResource.EMPTY;
        for (TileTank tile : tanks) {
            FluidResource held = tile.tank.getFluidType();
            if (held.isEmpty()) {
                continue;
            }
            if (fluid.isEmpty()) {
                fluid = held;
            } else if (!fluid.equals(held)) {
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

    private static boolean isGaseous(FluidResource fluid) {
        return fluid.getFluidType().getDensity() < 0;
    }

    // Tank helper methods

    /** Tests to see if this tank can connect to the other one, in the given direction. BuildCraft itself only
     * calls with {@link Direction#UP} or {@link Direction#DOWN}, however addons are free to call with any of the
     * other 4 non-null faces. (Although an addon calling from other faces must provide some way of transferring
     * fluids around).
     *
     * @param other The other tank.
     * @param direction The direction that the other tank is, from this tank.
     * @return True if this can connect, false otherwise. */
    public boolean canConnectTo(TileTank other, Direction direction) {
        return true;
    }

    /** Helper for {@link #canConnectTo(TileTank, Direction)} that only returns true if both tanks can connect to
     * each other.
     *
     * @param direction The direction from the "from" tank, to the "to" tank, such that
     *            {@code from.getBlockPos().relative(direction)} equals {@code to.getBlockPos()}.
     * @return True if both could connect, false otherwise. */
    public static boolean canTanksConnect(TileTank from, TileTank to, Direction direction) {
        return from.canConnectTo(to, direction) && to.canConnectTo(from, direction.getOpposite());
    }

    /** @return A list of all connected tanks around this block, ordered by position from bottom to top. See the
     *         class javadoc for why this is not simply called {@code getTanks()}. */
    private List<TileTank> getConnectedTanks() {
        // double-ended queue rather than array list to avoid the copy operation when we search downwards
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

    // ResourceHandler<FluidResource>

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        if (index != 0) {
            return FluidResource.EMPTY;
        }
        List<TileTank> tanks = getConnectedTanks();
        FluidResource bottom = tanks.get(0).tank.getFluidType();
        return bottom.isEmpty() ? tanks.get(tanks.size() - 1).tank.getFluidType() : bottom;
    }

    @Override
    public long getAmountAsLong(int index) {
        if (index != 0) {
            return 0;
        }
        long total = 0;
        for (TileTank t : getConnectedTanks()) {
            total += t.tank.getAmountAsLong(0);
        }
        return total;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        if (index != 0) {
            return 0;
        }
        long total = 0;
        for (TileTank t : getConnectedTanks()) {
            total += t.tank.getCapacity();
        }
        return total;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (index != 0 || resource.isEmpty()) {
            return false;
        }
        for (TileTank t : getConnectedTanks()) {
            FluidResource current = t.tank.getFluidType();
            if (!current.isEmpty() && !current.equals(resource)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext ctx) {
        if (index != 0 || resource.isEmpty() || amount <= 0 || !isValid(0, resource)) {
            return 0;
        }
        List<TileTank> tanks = getConnectedTanks();
        if (isGaseous(resource)) {
            Collections.reverse(tanks);
        }
        int remaining = amount;
        int inserted = 0;
        for (TileTank t : tanks) {
            int did = t.tank.insert(0, resource, remaining, ctx);
            if (did > 0) {
                remaining -= did;
                inserted += did;
                if (remaining == 0) {
                    break;
                }
            }
        }
        return inserted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext ctx) {
        if (index != 0 || resource.isEmpty() || amount <= 0) {
            return 0;
        }
        List<TileTank> tanks = getConnectedTanks();
        if (!isGaseous(resource)) {
            Collections.reverse(tanks);
        }
        int remaining = amount;
        int extracted = 0;
        for (TileTank t : tanks) {
            int did = t.tank.extract(0, resource, remaining, ctx);
            if (did > 0) {
                remaining -= did;
                extracted += did;
                if (remaining == 0) {
                    break;
                }
            }
        }
        return extracted;
    }

    // TileEntity

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("tank").ifPresent(tank::deserialize);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("fluid = " + tank.getDebugString());
    }
}
