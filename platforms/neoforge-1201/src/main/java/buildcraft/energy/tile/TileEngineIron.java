/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.tile;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.IFuelManager.IDirtyFuel;
import buildcraft.api.fuels.ISolidCoolant;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase;
import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.SoundUtil;

import buildcraft.energy.container.ContainerEngineIron;

import buildcraft.BCEnergyRegistries;

/**
 * The Combustion Engine (1.12.2's {@code TileEngineIron_BC8}) -- see the 26.x copy of this class for the full account
 * of the gameplay logic, the zero-amount-stack change, the capability split, the GUI tank clicks and the sync.
 *
 * <p>What differs on this target is only the fluid API, which is still Forge's classic one: the tanks are
 * {@code FluidTank}s ({@link Tank}), a fuel is looked up by {@link FluidStack} and matched with
 * {@link FluidStack#isFluidEqual}, and the capability is an {@link IFluidHandler} returned from
 * {@link #getCapability} for {@link ForgeCapabilities#FLUID_HANDLER} on every side (with its {@link LazyOptional}
 * invalidated in {@link #invalidateCaps()}). {@link #fluidHandler} is therefore a near-literal copy of 1.12.2's
 * {@code InternalFluidHandler} (an {@link IFluidHandlerAdv}, as it was), and {@link #allTanks} a small
 * {@code TankManager} stand-in. {@link #transferStackToTank} keeps 1.12.2's own shape too -- {@code map} drains the
 * item copy through {@link FluidUtil#getFluidHandler(ItemStack)} and {@link IFluidHandlerItem#getContainer()}.
 * A Forge {@code FluidTank} drained to zero keeps its fluid at amount 0, but {@code FluidStack#isEmpty} treats it as
 * empty, so the zero-amount handling is the same as on 26.x.
 */
public class TileEngineIron extends TileEngineBase implements MenuProvider {
    public static final int MAX_FLUID = 10_000;

    public static final double COOLDOWN_RATE = 0.05;
    public static final int MAX_COOLANT_PER_TICK = 40;

    public final Tank tankFuel = new Tank(MAX_FLUID, this::setChanged);
    public final Tank tankCoolant = new Tank(MAX_FLUID, this::setChanged);
    public final Tank tankResidue = new Tank(MAX_FLUID, this::setChanged);
    private final Tank[] tanks = { tankFuel, tankCoolant, tankResidue };

    public final IFluidHandlerAdv fluidHandler = new InternalFluidHandler();
    public final IFluidHandler allTanks = new AllTanks();
    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> fluidHandler);

    private int penaltyCooling = 0;
    private boolean lastPowered = false;
    private double burnTime;
    private double residueAmount = 0;
    @Nullable
    private IFuel currentFuel;

    public TileEngineIron(BlockPos pos, BlockState state) {
        super(BCEnergyRegistries.ENGINE_IRON_TYPE.get(), pos, state);
        tankFuel.setFilter(this::isValidFuel);
        tankCoolant.setFilter(this::isValidCoolant);
        tankResidue.setFilter(this::isResidue);
    }

    // TileBC overrides

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        CompoundTag tanksTag = nbt.getCompound("tanks");
        tankFuel.readFromNBT(tanksTag.getCompound("fuel"));
        tankCoolant.readFromNBT(tanksTag.getCompound("coolant"));
        tankResidue.readFromNBT(tanksTag.getCompound("residue"));
        penaltyCooling = nbt.getInt("penaltyCooling");
        burnTime = nbt.getDouble("burnTime");
        residueAmount = Math.max(0, nbt.getDouble("residueAmount"));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        CompoundTag tanksTag = new CompoundTag();
        tanksTag.put("fuel", tankFuel.writeToNBT(new CompoundTag()));
        tanksTag.put("coolant", tankCoolant.writeToNBT(new CompoundTag()));
        tanksTag.put("residue", tankResidue.writeToNBT(new CompoundTag()));
        nbt.put("tanks", tanksTag);
        nbt.putInt("penaltyCooling", penaltyCooling);
        nbt.putDouble("burnTime", burnTime);
        nbt.putDouble("residueAmount", residueAmount);
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
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

    // TileEngineBase overrides

    @Override
    public double getPistonSpeed() {
        return switch (getPowerStage()) {
            case BLUE -> 0.04;
            case GREEN -> 0.05;
            case YELLOW -> 0.06;
            case RED -> 0.07;
            default -> 0;
        };
    }

    @NotNull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(false);
    }

    @Override
    public boolean isBurning() {
        return !tankFuel.getFluid().isEmpty() && penaltyCooling == 0 && isRedstonePowered;
    }

    @Override
    protected void burn() {
        final FluidStack fuel = tankFuel.getFluid();
        int fuelAmount = fuel.isEmpty() ? 0 : fuel.getAmount();
        if (!fuel.isEmpty() && (currentFuel == null || !currentFuel.getFluid().isFluidEqual(fuel))) {
            currentFuel = BuildcraftFuelRegistry.fuel.getFuel(fuel);
        }
        // See the 26.x copy: an empty tank while burnTime is still positive is 1.12.2's zero-amount stack.
        if (fuel.isEmpty() && burnTime <= 0) {
            currentFuel = null;
        }
        if (currentFuel == null) {
            return;
        }

        if (penaltyCooling <= 0) {
            if (isRedstonePowered) {
                lastPowered = true;

                if (burnTime > 0 || fuelAmount > 0) {
                    if (burnTime > 0) {
                        burnTime--;
                    }
                    if (burnTime <= 0) {
                        if (fuelAmount > 0) {
                            tankFuel.drain(1, FluidAction.EXECUTE);
                            burnTime += currentFuel.getTotalBurningTime() / 1000.0;

                            // If we also produce residue then put it out too
                            if (currentFuel instanceof IDirtyFuel dirtyFuel) {
                                FluidStack residueFluid = dirtyFuel.getResidue().copy();
                                residueAmount += residueFluid.getAmount() / 1000.0;
                                if (residueAmount >= 1) {
                                    residueFluid.setAmount(Mth.floor(residueAmount));
                                    residueAmount -= tankResidue.fill(residueFluid, FluidAction.EXECUTE);
                                }
                            }
                        } else {
                            tankFuel.setFluid(FluidStack.EMPTY);
                            currentFuel = null;
                            currentOutput = 0;
                            return;
                        }
                    }
                    currentOutput = currentFuel.getPowerPerCycle(); // Comment out for constant power
                    addPower(currentFuel.getPowerPerCycle());
                    heat += currentFuel.getPowerPerCycle() * HEAT_PER_MJ / MjAPI.MJ;
                }
            } else if (lastPowered) {
                lastPowered = false;
                penaltyCooling = 10;
                // 10 tick of penalty on top of the cooling
            }
        }
    }

    @Override
    public void updateHeatLevel() {
        double target;
        if (heat > MIN_HEAT && (penaltyCooling > 0 || !isRedstonePowered)) {
            heat -= COOLDOWN_RATE;
            target = MIN_HEAT;
        } else if (heat > IDEAL_HEAT) {
            target = IDEAL_HEAT;
        } else {
            target = heat;
        }

        if (target != heat) {
            coolEngine(target);
            getPowerStage();
        }

        if (heat <= MIN_HEAT && penaltyCooling > 0) {
            penaltyCooling--;
        }

        if (heat <= MIN_HEAT) {
            heat = MIN_HEAT;
        }
    }

    /** See the 26.x copy. */
    private void coolEngine(double target) {
        double coolingBuffer = 0;
        double extraHeat = heat - target;

        if (extraHeat > 0) {
            if (tankCoolant.getFluidAmount() > 0) {
                float coolPerMb = BuildcraftFuelRegistry.coolant.getDegreesPerMb(tankCoolant.getFluid(), (float) heat);
                if (coolPerMb > 0) {
                    int coolantAmount = Math.min(MAX_COOLANT_PER_TICK, tankCoolant.getFluidAmount());
                    coolingBuffer += coolantAmount * coolPerMb;
                    tankCoolant.drain(coolantAmount, FluidAction.EXECUTE);
                }
            }
        }

        heat -= coolingBuffer;
    }

    @Override
    public boolean isActive() {
        return penaltyCooling <= 0;
    }

    @Override
    public long getMaxPower() {
        return 10_000 * MjAPI.MJ;
    }

    @Override
    public long maxPowerReceived() {
        return 2_000 * MjAPI.MJ;
    }

    @Override
    public long maxPowerExtracted() {
        return 500 * MjAPI.MJ;
    }

    @Override
    public float explosionRange() {
        return 4;
    }

    @Override
    protected int getMaxChainLength() {
        return 4;
    }

    @Override
    public long getCurrentOutput() {
        if (currentFuel == null) {
            return 0;
        } else {
            return currentFuel.getPowerPerCycle();
        }
    }

    // Fluid related

    private boolean isValidFuel(FluidStack fluid) {
        return BuildcraftFuelRegistry.fuel.getFuel(fluid) != null;
    }

    private boolean isValidCoolant(FluidStack fluid) {
        return BuildcraftFuelRegistry.coolant.getCoolant(fluid) != null;
    }

    private boolean isResidue(FluidStack fluid) {
        // If this is the client then we don't have a current fuel- just trust the server that its correct
        if (level != null && level.isClientSide()) {
            return true;
        }
        if (currentFuel instanceof IDirtyFuel dirtyFuel) {
            return fluid.isFluidEqual(dirtyFuel.getResidue());
        }
        return false;
    }

    /** @return The tank at {@code index} in GUI order (fuel, coolant, residue), or {@code null} if out of range. */
    @Nullable
    public Tank getTank(int index) {
        return index >= 0 && index < tanks.length ? tanks[index] : null;
    }

    /** 1.12.2's {@code Tank#transferStackToTank} -- see the 26.x copy. Server side only. */
    public ItemStack transferStackToTank(Tank tank, Player player, ItemStack stack) {
        if (level == null || level.isClientSide() || stack.isEmpty()) {
            return stack;
        }
        boolean isCreative = player.getAbilities().instabuild;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        int space = tank.getCapacity() - tank.getFluidAmount();

        // 1: from the item into the tank.
        FluidGetResult result = map(tank, copy, space);
        if (result != null && !result.fluidStack.isEmpty()) {
            int accepted = tank.fill(result.fluidStack, FluidAction.SIMULATE);
            if (isCreative ? (accepted > 0) : (accepted == result.fluidStack.getAmount())) {
                tank.fill(result.fluidStack, FluidAction.EXECUTE);
                SoundUtil.playBucketEmpty(level, player.blockPosition(), result.fluidStack);
                return isCreative ? stack : shrunk(stack, result.itemStack, player);
            }
        }

        // 2: from the tank into the item.
        Optional<IFluidHandlerItem> itemFluid = FluidUtil.getFluidHandler(copy.copy()).resolve();
        if (itemFluid.isEmpty()) {
            return stack;
        }
        FluidStack drained = tank.drain(tank.getCapacity(), FluidAction.SIMULATE);
        if (drained.isEmpty()) {
            return stack;
        }
        int filled = itemFluid.get().fill(drained, FluidAction.EXECUTE);
        if (filled > 0) {
            FluidStack reallyDrained = tank.drain(filled, FluidAction.EXECUTE);
            SoundUtil.playBucketFill(level, player.blockPosition(), reallyDrained);
            return isCreative ? stack : shrunk(stack, itemFluid.get().getContainer(), player);
        }
        return stack;
    }

    /** 1.12.2's {@code Tank#map}, plus {@code tankCoolant}'s own override for solid coolants. */
    @Nullable
    private FluidGetResult map(Tank tank, ItemStack stack, int space) {
        if (tank == tankCoolant) {
            ISolidCoolant coolant = BuildcraftFuelRegistry.coolant.getSolidCoolant(stack);
            FluidStack fluidCoolant = coolant == null ? null : coolant.getFluidFromSolidCoolant(stack);
            if (fluidCoolant != null && fluidCoolant.getAmount() > 0 && fluidCoolant.getAmount() <= space) {
                return new FluidGetResult(ItemStack.EMPTY, fluidCoolant);
            }
        }
        Optional<IFluidHandlerItem> handler = FluidUtil.getFluidHandler(stack.copy()).resolve();
        if (handler.isEmpty() || space <= 0) {
            return null;
        }
        FluidStack drained = handler.get().drain(space, FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return null;
        }
        return new FluidGetResult(handler.get().getContainer(), drained);
    }

    private record FluidGetResult(ItemStack itemStack, FluidStack fluidStack) {}

    /** One of {@code stack} was used up and became {@code result}: hand back whichever should now be held. */
    private static ItemStack shrunk(ItemStack stack, ItemStack result, Player player) {
        ItemStack remaining = stack.copy();
        remaining.shrink(1);
        if (remaining.isEmpty()) {
            return result;
        }
        if (!result.isEmpty()) {
            InventoryUtil.addToPlayer(player, result);
        }
        return remaining;
    }

    /** 1.12.2's {@code Tank#onGuiClicked} -- see the 26.x copy. */
    public void onGuiClicked(int index, Player player, AbstractContainerMenu menu) {
        Tank tank = getTank(index);
        ItemStack held = menu.getCarried();
        if (tank == null || held.isEmpty()) {
            return;
        }
        menu.setCarried(transferStackToTank(tank, player, held));
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        super.getDebugInfo(left, right, side);
        left.add("fuel = " + tankFuel.getDebugString());
        left.add("coolant = " + tankCoolant.getDebugString());
        left.add("residue = " + tankResidue.getDebugString());
        left.add("burnTime = " + burnTime + ", penaltyCooling = " + penaltyCooling);
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerEngineIron(windowId, playerInv, this);
    }

    /** 1.12.2's {@code InternalFluidHandler}: fill the fuel tank, else the coolant tank; drain only residue. */
    private final class InternalFluidHandler implements IFluidHandlerAdv {
        @Override
        public int getTanks() {
            return tanks.length;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return tanks[tank].getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tanks[tank].getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank != 2 && tanks[tank].isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = tankFuel.fill(resource, action);
            if (filled == 0) {
                filled = tankCoolant.fill(resource, action);
            }
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return tankResidue.drain(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return tankResidue.drain(maxDrain, action);
        }

        @Override
        public FluidStack drain(IFluidFilter filter, int maxDrain, FluidAction action) {
            return tankResidue.drain(filter, maxDrain, action);
        }
    }

    /** 1.12.2's {@code TankManager} over the three tanks: each keeps its own filter; fills and drains go in fuel,
     * coolant, residue order, draining only one fluid per call. */
    private final class AllTanks implements IFluidHandler {
        @Override
        public int getTanks() {
            return tanks.length;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return tanks[tank].getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tanks[tank].getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tanks[tank].isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            FluidStack remaining = resource.copy();
            int filled = 0;
            for (Tank tank : tanks) {
                if (remaining.isEmpty()) {
                    break;
                }
                int used = tank.fill(remaining, action);
                filled += used;
                remaining.shrink(used);
            }
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            FluidStack draining = FluidStack.EMPTY;
            int left = resource.getAmount();
            for (Tank tank : tanks) {
                if (left <= 0) {
                    break;
                }
                if (!tank.getFluid().isFluidEqual(resource)) {
                    continue;
                }
                FluidStack drained = tank.drain(left, action);
                if (!drained.isEmpty()) {
                    if (draining.isEmpty()) {
                        draining = drained;
                    } else {
                        draining.grow(drained.getAmount());
                    }
                    left -= drained.getAmount();
                }
            }
            return draining;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            for (Tank tank : tanks) {
                if (!tank.getFluid().isEmpty()) {
                    FluidStack template = tank.getFluid().copy();
                    template.setAmount(maxDrain);
                    return drain(template, action);
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
