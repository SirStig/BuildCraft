/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.tiles.IHasWork;
import buildcraft.api.tiles.TilesAPI;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.data.AverageLong;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC;

import buildcraft.factory.container.ContainerDistiller;

import buildcraft.BCFactoryRegistries;

/**
 * The port of 1.12.2's {@code TileDistiller_BC8} -- see the 26.x copy of this class for the full account. The
 * platform difference: this target's {@link Tank} is a classic {@code FluidTank}/{@link IFluidHandler}, and the
 * capabilities are handed out from {@link #getCapability} (per side: input on the four horizontal faces through
 * {@link #tankInExternal}, a fill-only wrapper standing in for 1.12.2's {@code setCanDrain(false)}; gas output on
 * {@code UP}; liquid output on {@code DOWN}; nothing for a side-less query), instead of being registered against
 * the block entity type.
 */
public class TileDistiller extends TileBC implements IDebuggable, IHasWork, MenuProvider {

    public static final long MAX_MJ_PER_TICK = 6 * MjAPI.MJ;

    public final Tank tankIn = new Tank(4 * 1000, this::onTankChanged);
    public final Tank tankGasOut = new Tank(4 * 1000, this::onTankChanged);
    public final Tank tankLiquidOut = new Tank(4 * 1000, this::onTankChanged);
    /** {@link #tankIn} as seen from outside: fill-only. */
    public final IFluidHandler tankInExternal = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return tankIn.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tankIn.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tankIn.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tankIn.fill(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    private final MjBattery mjBattery = new MjBattery(1024 * MjAPI.MJ);
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(mjBattery);

    private final LazyOptional<IFluidHandler> inCap = LazyOptional.of(() -> tankInExternal);
    private final LazyOptional<IFluidHandler> gasCap = LazyOptional.of(() -> tankGasOut);
    private final LazyOptional<IFluidHandler> liquidCap = LazyOptional.of(() -> tankLiquidOut);
    private final LazyOptional<IMjReceiver> receiverCap = LazyOptional.of(() -> mjReceiver);
    private final LazyOptional<IMjReadable> readableCap = LazyOptional.of(() -> mjReceiver);
    private final LazyOptional<IHasWork> hasWorkCap = LazyOptional.of(() -> this);

    @Nullable
    private IDistillationRecipe currentRecipe;
    private long distillPower = 0;
    private boolean hasWork, isActive = false;
    private final AverageLong powerAvg = new AverageLong(100);
    private boolean tanksChanged = false;

    public TileDistiller(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.DISTILLER_TYPE.get(), pos, state);
        tankIn.setFilter(TileDistiller::isDistillableFluid);
        tankGasOut.setCanFill(false);
        tankLiquidOut.setCanFill(false);
    }

    private void onTankChanged() {
        tanksChanged = true;
        setChanged();
    }

    @Nullable
    private static IDistillationRecipe getRecipe(FluidStack fluid) {
        IRefineryRecipeManager manager = BuildcraftRecipeRegistry.refineryRecipes;
        if (manager == null || fluid.isEmpty()) {
            return null;
        }
        return manager.getDistillationRegistry().getRecipeForInput(fluid);
    }

    private static boolean isDistillableFluid(FluidStack fluid) {
        return getRecipe(fluid) != null;
    }

    public static FluidStack getFluid(Tank tank) {
        return tank.getFluid();
    }

    private static boolean canDrain(Tank tank, FluidStack req) {
        FluidStack f = tank.getFluid();
        return !f.isEmpty() && f.isFluidEqual(req) && f.getAmount() >= req.getAmount();
    }

    private static boolean canFillAll(Tank tank, FluidStack fluid) {
        FluidStack f = tank.getFluid();
        if (!f.isEmpty() && !f.isFluidEqual(fluid)) {
            return false;
        }
        return tank.getFluidAmount() + fluid.getAmount() <= tank.getCapacity();
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    public boolean hasWork() {
        return hasWork;
    }

    /** Driven by the owning block's {@code getTicker}; was {@code TileDistiller_BC8#update()} (server half). */
    public void serverTick() {
        boolean wasActive = isActive;
        powerAvg.tick();

        currentRecipe = getRecipe(tankIn.getFluid());
        if (currentRecipe == null) {
            mjBattery.addPowerChecking(distillPower, false);
            distillPower = 0;
            isActive = false;
            hasWork = false;
        } else {
            FluidStack reqIn = currentRecipe.in();
            FluidStack outLiquid = currentRecipe.outLiquid();
            FluidStack outGas = currentRecipe.outGas();

            boolean canExtract = canDrain(tankIn, reqIn);
            boolean canFillLiquid = canFillAll(tankLiquidOut, outLiquid);
            boolean canFillGas = canFillAll(tankGasOut, outGas);

            if (canExtract && canFillLiquid && canFillGas) {
                hasWork = true;
                long max = MAX_MJ_PER_TICK;
                max *= mjBattery.getStored() + max;
                max /= mjBattery.getCapacity() / 2;
                max = Math.min(max, MAX_MJ_PER_TICK);
                long powerReq = currentRecipe.powerRequired();
                long power = mjBattery.extractPower(0, max);
                powerAvg.push(max);
                distillPower += power;
                isActive = power > 0;
                if (distillPower >= powerReq) {
                    isActive = true;
                    distillPower -= powerReq;
                    tankIn.drain(reqIn.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                    tankGasOut.fillInternal(outGas);
                    tankLiquidOut.fillInternal(outLiquid);
                }
                setChanged();
            } else {
                hasWork = false;
                mjBattery.addPowerChecking(distillPower, false);
                distillPower = 0;
                isActive = false;
            }
        }

        if (tanksChanged || wasActive != isActive) {
            tanksChanged = false;
            markDirtyAndSync();
        }
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (side == null) {
                return LazyOptional.empty();
            }
            return switch (side) {
                case UP -> gasCap.cast();
                case DOWN -> liquidCap.cast();
                default -> inCap.cast();
            };
        }
        if (cap == MjCapabilities.RECEIVER) {
            return receiverCap.cast();
        }
        if (cap == MjCapabilities.READABLE) {
            return readableCap.cast();
        }
        if (cap == TilesAPI.HAS_WORK) {
            return hasWorkCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inCap.invalidate();
        gasCap.invalidate();
        liquidCap.invalidate();
        receiverCap.invalidate();
        readableCap.invalidate();
        hasWorkCap.invalidate();
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerDistiller(windowId, playerInv, this);
    }

    // TileBC

    /** The 100-sample power history is server-only bookkeeping; the client never reads it, so it is left out of
     * the sync tag sent on every tank change. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.remove("powerAvg");
        return tag;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        tankIn.readFromNBT(nbt.getCompound("tankIn"));
        tankGasOut.readFromNBT(nbt.getCompound("tankGasOut"));
        tankLiquidOut.readFromNBT(nbt.getCompound("tankLiquidOut"));
        mjBattery.setStored(nbt.getLong("battery"));
        distillPower = nbt.getLong("distillPower");
        powerAvg.readFromNbt(nbt, "powerAvg");
        isActive = nbt.getBoolean("active");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("tankIn", tankIn.writeToNBT(new CompoundTag()));
        nbt.put("tankGasOut", tankGasOut.writeToNBT(new CompoundTag()));
        nbt.put("tankLiquidOut", tankLiquidOut.writeToNBT(new CompoundTag()));
        nbt.putLong("battery", mjBattery.getStored());
        nbt.putLong("distillPower", distillPower);
        powerAvg.writeToNbt(nbt, "powerAvg");
        nbt.putBoolean("active", isActive);
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("In = " + tankIn.getDebugString());
        left.add("GasOut = " + tankGasOut.getDebugString());
        left.add("LiquidOut = " + tankLiquidOut.getDebugString());
        left.add("Battery = " + mjBattery.getDebugString());
        left.add("Progress = " + MjAPI.formatMj(distillPower));
        left.add("Rate = " + LocaleUtil.localizeMjFlow(powerAvg.getAverageLong()));
        left.add("CurrRecipe = " + currentRecipe);
    }
}
