/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.tile;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;

import buildcraft.api.mj.ILaserTarget;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.data.AverageLong;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Common base for every "laser table" -- see the 26.x copy of this class for the full account of what changed
 * from 1.12.2's {@code TileLaserTableBase}. This target keeps {@code CompoundTag}-based {@code load}/
 * {@code saveAdditional} and 1.12.2's own {@link ItemHandlerManager}/{@link IItemHandlerModifiable} shape (see
 * that class's own javadoc), rather than 26.x's {@code ValueInput}/{@code ValueOutput} rewrite.
 */
public abstract class TileLaserTableBase extends TileBC implements ILaserTarget, IDebuggable {

    private final AverageLong avgPower = new AverageLong(120);
    public long power;

    public final ItemHandlerManager itemManager = new ItemHandlerManager(this::onSlotChange);

    protected TileLaserTableBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract long getTarget();

    protected void onSlotChange(IItemHandlerModifiable handler, int slot, @NotNull ItemStack before, @NotNull ItemStack after) {
        setChanged();
    }

    // ILaserTarget

    @Override
    public long getRequiredLaserPower() {
        return getTarget() - power;
    }

    @Override
    public long receiveLaserPower(long microJoules) {
        long received = Math.min(microJoules, getRequiredLaserPower());
        power += received;
        avgPower.push(received);
        return microJoules - received;
    }

    @Override
    public boolean isInvalidTarget() {
        return isRemoved();
    }

    /** Driven by the owning block's {@code getTicker}. Subclasses override this, call {@code super.serverTick()}
     * first, then do their own work -- see the 26.x copy of this class for why no remote check is needed. */
    public void serverTick() {
        avgPower.tick();
        if (getTarget() <= 0) {
            power = 0;
            avgPower.clear();
        }
    }

    // Capabilities

    /** Every table's item inventory is reachable from every side, matching 1.12.2's {@code EnumPipePart.VALUES}
     * wiring on each -- see each tile's own constructor. Mirrors {@code TileAutoWorkbenchBase}'s own delegation to
     * {@link #itemManager}. */
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> itemCap = itemManager.getCapability(cap, side);
        if (itemCap.isPresent()) {
            return itemCap;
        }
        return super.getCapability(cap, side);
    }

    // TileBC

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemManager.deserializeNBT(nbt.getCompound("inv_manager"));
        power = nbt.getLong("power");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("inv_manager", itemManager.serializeNBT());
        nbt.putLong("power", power);
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("power - " + MjAPI.formatMj(power));
        left.add("target - " + MjAPI.formatMj(getTarget()));
    }

    /** See the 26.x copy of this class's own javadoc for the algorithm; the only change here is
     * {@link ItemHandlerSimple#getSlots()} in place of {@code size()} and a plain {@code setStackInSlot}, matching
     * 1.12.2's own {@code IItemHandlerModifiable} shape this target keeps. */
    protected boolean extract(ItemHandlerSimple inv, Collection<IngredientStack> items, boolean simulate,
        boolean precise) {
        AtomicLong remainingStacks = new AtomicLong(countNonEmpty(inv));
        boolean allItemsConsumed = items.stream().allMatch(definition -> {
            int remaining = definition.count();
            for (int i = 0; i < inv.getSlots() && remaining > 0; i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (slotStack.isEmpty()) {
                    continue;
                }
                if (definition.ingredient().test(slotStack)) {
                    int spend = Math.min(remaining, slotStack.getCount());
                    remaining -= spend;
                    if (!simulate) {
                        slotStack.shrink(spend);
                        inv.setStackInSlot(i, slotStack);
                    }
                }
            }
            if (remaining == 0) {
                remainingStacks.decrementAndGet();
                return true;
            }
            return false;
        });
        return allItemsConsumed && (!precise || remainingStacks.get() == 0);
    }

    private static long countNonEmpty(ItemHandlerSimple inv) {
        long count = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
