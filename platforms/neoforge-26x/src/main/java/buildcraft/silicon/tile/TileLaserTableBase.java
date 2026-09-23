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

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.mj.ILaserTarget;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.data.AverageLong;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Common base for every "laser table": a machine that accumulates MJ received from a {@code TileLaser} (not yet
 * ported this round -- see {@code buildcraft.silicon.block.BlockLaserTable}'s own javadoc) until it reaches
 * {@link #getTarget()}, then does whatever its subclass does with that power.
 *
 * <p>Ported from 1.12.2's {@code TileLaserTableBase}. The biggest structural change is ticking itself: 1.12.2's
 * version implemented {@code ITickable} and guarded its whole body with {@code if (world.isRemote) return;}. This
 * target ticks a block entity from the owning block's {@code BlockEntityTicker}, which is only ever installed
 * server-side to begin with (the {@code TileDistiller}/{@code TileAutoWorkbenchBase} precedent) -- so
 * {@link #serverTick()} needs no remote check at all, and subclasses that override it call
 * {@code super.serverTick()} first exactly the way 1.12.2's subclasses called {@code super.update()}.
 *
 * <p>{@link #power} is still a plain field, synced to the client the same way {@code TileDistiller} syncs its
 * tanks: included in the saved NBT, pushed out via {@link #markDirtyAndSync()} whenever it (or anything a GUI
 * reads) changes. There is no separate id-tagged {@code NET_GUI_TICK} payload or client-side {@code avgPowerClient}
 * mirror -- see this port's GUI classes for how each subclass's screen reads {@link #power}/{@link #getTarget()}
 * straight off the tile.
 *
 * <p>{@link #extract} is a near-literal port: {@code IngredientStack} used to wrap a
 * {@code Predicate<ItemStack>}-shaped "ingredient" field; on this target it wraps a real {@link
 * net.minecraft.world.item.crafting.Ingredient} instead (see {@link IngredientStack}'s own javadoc), so the
 * predicate call becomes {@code ingredient().test(stack)} and the count becomes {@code count()}.
 */
public abstract class TileLaserTableBase extends TileBC implements ILaserTarget, IDebuggable {

    private final AverageLong avgPower = new AverageLong(120);
    public long power;

    public final ItemHandlerManager itemManager = new ItemHandlerManager(this::onSlotChange);

    protected TileLaserTableBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract long getTarget();

    /** Overridden by subclasses that care about a slot changing; the default just marks the chunk dirty, matching
     * 1.12.2's default {@code onSlotChange} (which didn't force an immediate client sync either). */
    protected void onSlotChange(ItemHandlerSimple handler, int slot, ItemStack before, ItemStack after) {
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

    /** Driven by the owning block's {@code getTicker} -- see this class's own javadoc for why no remote check is
     * needed. Subclasses override this, call {@code super.serverTick()} first, then do their own work. */
    public void serverTick() {
        avgPower.tick();
        if (getTarget() <= 0) {
            power = 0;
            avgPower.clear();
        }
    }

    // TileBC

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        itemManager.deserialize(input);
        power = input.getLongOr("power", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemManager.serialize(output);
        output.putLong("power", power);
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("power - " + MjAPI.formatMj(power));
        left.add("target - " + MjAPI.formatMj(getTarget()));
    }

    /** 1.12.2's {@code TileLaserTableBase#extract}: checks (and optionally performs) removing {@code items} from
     * {@code inv}. {@code precise} additionally requires every non-empty stack in {@code inv} to have been used by
     * some ingredient -- used by the assembly table to refuse a recipe while extra, unrelated items sit in the
     * grid. */
    protected boolean extract(ItemHandlerSimple inv, Collection<IngredientStack> items, boolean simulate,
        boolean precise) {
        AtomicLong remainingStacks = new AtomicLong(countNonEmpty(inv));
        boolean allItemsConsumed = items.stream().allMatch(definition -> {
            int remaining = definition.count();
            for (int i = 0; i < inv.size() && remaining > 0; i++) {
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
        for (int i = 0; i < inv.size(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
