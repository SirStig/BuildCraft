/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.fluid.Tank;

import buildcraft.factory.container.ContainerAutoCraftFluids;

import buildcraft.BCFactoryRegistries;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for the full account of why this block never
 * actually shipped in 1.12.2 (its registration is commented out in {@code BCFactoryBlocks.java}, and no asset
 * ever existed for it) and what stands in for its never-created assets in this port.
 *
 * <p>This file differs from the 26.x copy only in the usual 1.20.1 places: NBT is {@link CompoundTag}, and fluid
 * capabilities are exposed by the block entity itself through {@link #getCapability} (matching
 * {@code TilePump}/{@code TileFloodGate}'s own precedent on this target) rather than registered against the
 * block entity type. Since {@link TileAutoWorkbenchBase} already overrides {@link #getCapability} for the MJ/
 * has-work/item capabilities, this class's own override only adds the {@code FLUID_HANDLER} branch and falls
 * through to {@code super.getCapability} for everything else.
 *
 * <p><b>{@link #combinedTanks}</b> stands in for 1.12.2's {@code TankManager}-typed {@code tankManager} field for
 * the "no specific side" case (matching the 26.x copy's own {@link net.neoforged.neoforge.transfer.CombinedResourceHandler}
 * substitute) -- confirmed via {@code javap} against the Forge 1.20.1 universal jar that no combined-fluid-handler
 * wrapper exists on this target the way {@code net.minecraftforge.items.wrapper.CombinedInvWrapper} does for
 * items, so this is a small, purpose-built two-tank adapter rather than a from-scratch port of the full
 * {@code TankManager} class (nothing else in this port has needed one yet).
 */
public class TileAutoWorkbenchFluids extends TileAutoWorkbenchBase implements IDebuggable {
    public final Tank tank1 = new Tank(6 * FluidType.BUCKET_VOLUME, this::markDirtyAndSync);
    public final Tank tank2 = new Tank(6 * FluidType.BUCKET_VOLUME, this::markDirtyAndSync);
    public final IFluidHandler combinedTanks = new CombinedTanks();

    private final LazyOptional<IFluidHandler> tank1Cap = LazyOptional.of(() -> tank1);
    private final LazyOptional<IFluidHandler> tank2Cap = LazyOptional.of(() -> tank2);
    private final LazyOptional<IFluidHandler> combinedCap = LazyOptional.of(() -> combinedTanks);

    public TileAutoWorkbenchFluids(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.AUTO_WORKBENCH_FLUIDS_TYPE.get(), pos, state, 2, 2);
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerAutoCraftFluids(windowId, playerInv, this);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("Tanks:");
        left.add("  tank1: " + tank1.getContentsString());
        left.add("  tank2: " + tank2.getContentsString());
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (side == null) {
                return combinedCap.cast();
            }
            return switch (side) {
                case DOWN, NORTH, WEST -> tank1Cap.cast();
                case UP, SOUTH, EAST -> tank2Cap.cast();
            };
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        tank1Cap.invalidate();
        tank2Cap.invalidate();
        combinedCap.invalidate();
    }

    // TileBC

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        tank1.readFromNBT(nbt.getCompound("tank1"));
        tank2.readFromNBT(nbt.getCompound("tank2"));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("tank1", tank1.writeToNBT(new CompoundTag()));
        nbt.put("tank2", tank2.writeToNBT(new CompoundTag()));
    }

    /** A minimal read-through {@link IFluidHandler} combining {@link #tank1} (index 0) and {@link #tank2}
     * (index 1) -- see this class's own javadoc for why no ready-made combinator exists on this target to reuse
     * instead. {@link #fill}/{@link #drain} try {@link #tank1} first, then spill any remainder into
     * {@link #tank2} -- the same "first handler that accepts, then the next" rule
     * {@code net.minecraftforge.items.wrapper.CombinedInvWrapper} itself uses for items. */
    private final class CombinedTanks implements IFluidHandler {
        private final Tank[] tanks = { tank1, tank2 };

        @Override
        public int getTanks() {
            return tanks.length;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tanks[tank].getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tanks[tank].getTankCapacity(0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tanks[tank].isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            FluidStack remaining = resource.copy();
            int filled = 0;
            for (Tank tank : tanks) {
                if (remaining.isEmpty()) {
                    break;
                }
                int amount = tank.fill(remaining, action);
                filled += amount;
                remaining.shrink(amount);
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            FluidStack result = FluidStack.EMPTY;
            FluidStack remaining = resource.copy();
            for (Tank tank : tanks) {
                if (remaining.isEmpty()) {
                    break;
                }
                FluidStack drained = tank.drain(remaining, action);
                if (!drained.isEmpty()) {
                    result = mergeInto(result, drained);
                    remaining.shrink(drained.getAmount());
                }
            }
            return result;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack result = FluidStack.EMPTY;
            int remaining = maxDrain;
            for (Tank tank : tanks) {
                if (remaining <= 0) {
                    break;
                }
                FluidStack drained = tank.drain(remaining, action);
                if (!drained.isEmpty()) {
                    result = mergeInto(result, drained);
                    remaining -= drained.getAmount();
                }
            }
            return result;
        }

        private FluidStack mergeInto(FluidStack result, FluidStack drained) {
            if (result.isEmpty()) {
                return drained;
            }
            result.grow(drained.getAmount());
            return result;
        }
    }
}
