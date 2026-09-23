/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.tiles.IHasWork;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.data.AverageLong;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC;

import buildcraft.factory.container.ContainerDistiller;

import buildcraft.BCFactoryRegistries;

/**
 * The port of 1.12.2's {@code TileDistiller_BC8}: splits one distillable fluid into a lighter gas product and a
 * heavier liquid product, driven by MJ. The recipe lookup, the power-draw formula in {@link #serverTick} (up to
 * {@link #MAX_MJ_PER_TICK}, scaled by how full the 1024 MJ battery is), the {@code distillPower} carry-over and the
 * "refund {@code distillPower} to the battery when the recipe goes away" rule are all 1.12.2's, unchanged.
 *
 * <p><b>Per-side fluid capabilities</b> ({@code BCFactoryRegistries#registerCapabilities}): 1.12.2 registered
 * {@link #tankIn} on {@code EnumPipePart.HORIZONTALS}, {@link #tankGasOut} on {@code UP} and
 * {@link #tankLiquidOut} on {@code DOWN}, and nothing on {@code CENTER} (a query with no side) -- reproduced
 * exactly. 1.12.2's {@code tankIn.setCanDrain(false)} has no counterpart on this port's {@link Tank} (a
 * {@code FluidStacksResourceHandler}, whose {@code extract} is public and unrestricted), so the horizontal faces
 * are handed {@link #tankInExternal}, a {@link DelegatingResourceHandler} whose {@code extract} always returns 0;
 * the tile itself still drains {@link #tankIn} directly. The two output tanks keep 1.12.2's
 * {@code setCanFill(false)} through {@link Tank#setCanFill}.
 *
 * <p><b>Dropped, not ported:</b> {@code FluidSmoother} (client-side level interpolation; the tanks sync as part of
 * the full-NBT update tag instead, the {@code RenderTileTank} precedent), the id-tagged network payloads
 * ({@code NET_TANK_*}/{@code NET_RENDER_DATA}), the {@code ModelVariableData}/expression-driven piston animation
 * (see {@code RenderDistiller}), {@code ElementHelpInfo} help overlays, and the tanks' item-drop-on-break (no fluid
 * shard item exists in this port yet -- fluid is lost on break, as it is for {@code TileTank}).
 * {@link #isActive} is saved into the update tag so the GUI's active animation and off-state icons can read it.
 */
public class TileDistiller extends TileBC implements IDebuggable, IHasWork, MenuProvider {

    public static final long MAX_MJ_PER_TICK = 6 * MjAPI.MJ;

    public final Tank tankIn = new Tank(4 * 1000, this::onTankChanged);
    public final Tank tankGasOut = new Tank(4 * 1000, this::onTankChanged);
    public final Tank tankLiquidOut = new Tank(4 * 1000, this::onTankChanged);
    /** {@link #tankIn} as seen from outside: fill-only -- see the class javadoc. */
    public final ResourceHandler<FluidResource> tankInExternal = new DelegatingResourceHandler<>(tankIn) {
        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    };

    private final MjBattery mjBattery = new MjBattery(1024 * MjAPI.MJ);
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(mjBattery);

    @Nullable
    private IDistillationRecipe currentRecipe;
    private long distillPower = 0;
    private boolean hasWork, isActive = false;
    private final AverageLong powerAvg = new AverageLong(100);
    /** Set by any tank change; flushed once per tick by {@link #serverTick} into a single client sync, instead of
     * 1.12.2's per-tank {@code FluidSmoother} messages. */
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

    private static boolean isDistillableFluid(FluidResource fluid) {
        return getRecipe(fluid.toStack(1000)) != null;
    }

    public static FluidStack getFluid(Tank tank) {
        FluidResource res = tank.getResource(0);
        return res.isEmpty() ? FluidStack.EMPTY : res.toStack(tank.getAmountAsInt(0));
    }

    /** 1.12.2's {@code FluidTank#drainInternal(reqIn, false)} + {@code isFluidStackIdentical}: whether
     * {@code tank} holds at least {@code req}'s amount of {@code req}'s fluid. */
    private static boolean canDrain(Tank tank, FluidStack req) {
        FluidResource res = tank.getResource(0);
        return !res.isEmpty() && res.matches(req) && tank.getAmountAsInt(0) >= req.getAmount();
    }

    /** 1.12.2's {@code fillInternal(fluid, false) == fluid.amount}: bypasses {@code canFill}/the filter, only
     * capacity and fluid type matter. */
    private static boolean canFillAll(Tank tank, FluidStack fluid) {
        FluidResource res = tank.getResource(0);
        if (!res.isEmpty() && !res.matches(fluid)) {
            return false;
        }
        return tank.getAmountAsInt(0) + fluid.getAmount() <= tank.getCapacity();
    }

    private static void drainInternal(Tank tank, int amount) {
        int left = tank.getAmountAsInt(0) - amount;
        tank.set(0, left <= 0 ? FluidResource.EMPTY : tank.getResource(0), Math.max(0, left));
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

        currentRecipe = getRecipe(getFluid(tankIn));
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
                    drainInternal(tankIn, reqIn.getAmount());
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

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerDistiller(BCFactoryRegistries.DISTILLER_MENU.get(), windowId, playerInv, this);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }

    // TileBC

    /** The 100-sample power history is server-only bookkeeping; the client never reads it, so it is left out of
     * the sync tag sent on every tank change. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.remove("powerAvg");
        return tag;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("tankIn").ifPresent(tankIn::deserialize);
        input.child("tankGasOut").ifPresent(tankGasOut::deserialize);
        input.child("tankLiquidOut").ifPresent(tankLiquidOut::deserialize);
        mjBattery.setStored(input.getLongOr("battery", 0));
        distillPower = input.getLongOr("distillPower", 0);
        input.read("powerAvg", CompoundTag.CODEC).ifPresent(tag -> powerAvg.readFromNbt(tag, "v"));
        isActive = input.getBooleanOr("active", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tankIn.serialize(output.child("tankIn"));
        tankGasOut.serialize(output.child("tankGasOut"));
        tankLiquidOut.serialize(output.child("tankLiquidOut"));
        output.putLong("battery", mjBattery.getStored());
        output.putLong("distillPower", distillPower);
        CompoundTag avg = new CompoundTag();
        powerAvg.writeToNbt(avg, "v");
        output.store("powerAvg", CompoundTag.CODEC, avg);
        output.putBoolean("active", isActive);
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
