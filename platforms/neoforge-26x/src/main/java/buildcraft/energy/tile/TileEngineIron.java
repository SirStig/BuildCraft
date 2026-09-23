/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.tile;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.enums.EnumPowerStage;
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

import buildcraft.energy.container.ContainerEngineIron;

import buildcraft.BCEnergyRegistries;

/**
 * Renamed from 1.12.2's {@code TileEngineIron_BC8} (the "Iron Engine" in code, "Combustion Engine" in game), following
 * {@code TileEngineStone}'s "drop the version-tag suffix" convention. A liquid-fuel engine with three 10-bucket tanks:
 * fuel (anything in {@code BuildcraftFuelRegistry.fuel}), coolant (anything in {@code BuildcraftFuelRegistry.coolant})
 * and residue (the by-product of a "dirty" fuel such as crude oil).
 *
 * <p><b>The gameplay logic is 1.12.2's, line for line</b> -- {@link #burn()}, {@link #updateHeatLevel()} (with its
 * inlined {@code coolEngine}/{@code fillCoolingBuffer} blocks), the piston speeds, the power/chain limits and
 * {@link #getCurrentOutput()}. In short: while redstone-powered and not in a cooling penalty, the engine burns one mB
 * of fuel every {@code totalBurningTime / 1000} ticks, adds that fuel's {@code powerPerCycle} MJ every tick, and heats
 * up by {@code powerPerCycle * HEAT_PER_MJ} degrees per tick. Above {@link #IDEAL_HEAT} it drains up to
 * {@value #MAX_COOLANT_PER_TICK} mB of coolant per tick (water cools {@code 0.0023} degrees per mB) -- with no coolant
 * the heat keeps climbing until the base class's power stage reaches {@link EnumPowerStage#OVERHEAT}, where
 * {@link TileEngineBase#serverTick()} stops calling {@link #burn()} and the engine stays stalled until it is cooled
 * (coolant added, or redstone removed so it cools at {@value #COOLDOWN_RATE} degrees per tick). Turning the redstone
 * signal off after running adds a 10-tick {@link #penaltyCooling} on top of the cool-down, exactly as before.
 *
 * <p><b>What changed, and why:</b>
 * <ul>
 * <li><b>The fuel tank's zero-amount stack.</b> 1.12.2 burned fuel with a direct {@code fuel.amount--} on the
 *     tank's own {@code FluidStack}, so while the last mB was still burning the tank held a zero-amount stack of the
 *     fuel, and {@code burn()} only cleared it when {@link #burnTime} ran out as well. A
 *     {@code FluidStacksResourceHandler} cannot hold a zero-amount stack of a fluid (its {@code FluidResource.toStack(0)}
 *     is simply empty), so the tank empties as soon as the last mB is taken. {@link #burn()} keeps
 *     {@link #currentFuel} for that final stretch instead, which gives exactly the same result: the last mB still
 *     burns for its full time before the engine stops. The same limitation is why 1.12.2's "prime an empty residue
 *     tank with a zero-amount residue stack" branch is dropped: it only told a GUI which fluid was coming.</li>
 * <li><b>Fluid capability.</b> 1.12.2's {@code InternalFluidHandler} (fill = fuel then coolant, drain = residue only,
 *     on every face) becomes {@link #fluidHandler}: a {@link CombinedResourceHandler} over three
 *     {@link TankAccess} views, fuel and coolant insert-only and residue extract-only, registered for
 *     {@code Capabilities.Fluid.BLOCK} on every side in {@code BCEnergyRegistries}. {@link #allTanks} is 1.12.2's
 *     {@code TankManager}, used for the right-click with a fluid container (see {@code BlockEngineIron}): it can
 *     fill any tank that accepts the fluid and drain any tank, in fuel, coolant, residue order.</li>
 * <li><b>GUI tank clicks.</b> 1.12.2's {@code Tank#transferStackToTank} (the tank widget's click and the shift-click
 *     handler), including {@code tankCoolant}'s solid-coolant override of {@code map} (ice becomes water, 1500 mB per
 *     block), is {@link #transferStackToTank} here. The item's fluid capability is reached through a one-slot
 *     {@link ItemStacksResourceHandler} and {@link ItemAccess#forHandlerIndexStrict}, so a bucket can turn into an empty
 *     bucket ({@link ItemAccess#forStack} would forbid that, since it never changes the item).</li>
 * <li><b>Networking.</b> 1.12.2 sent the tanks in its {@code NET_GUI_DATA}/{@code NET_GUI_TICK} payloads. Here the
 *     tanks only mark the tile dirty ({@code setChanged}, not a full block-update sync, which would happen every tick
 *     while fuel burns), and {@link ContainerEngineIron} syncs each tank's fluid id and amount with container data
 *     slots, which fit: both are well under the protocol's 16-bit data-slot limit.</li>
 * <li><b>Dropped:</b> the {@code ElementHelpInfo} help text (the help framework is not ported, see
 *     {@code TileEngineStone}), and {@code TankManager#addDrops} (the fragile fluid shards 1.12.2 dropped for a
 *     broken engine's fluids are not ported; the fluid is lost, as with every other tank in this port).</li>
 * </ul>
 */
public class TileEngineIron extends TileEngineBase implements MenuProvider {
    public static final int MAX_FLUID = 10_000;

    public static final double COOLDOWN_RATE = 0.05;
    public static final int MAX_COOLANT_PER_TICK = 40;

    public final Tank tankFuel = new Tank(MAX_FLUID, this::setChanged);
    public final Tank tankCoolant = new Tank(MAX_FLUID, this::setChanged);
    public final Tank tankResidue = new Tank(MAX_FLUID, this::setChanged);

    /** The face-independent {@code Capabilities.Fluid.BLOCK} handler -- see this class's javadoc. */
    public final ResourceHandler<FluidResource> fluidHandler = new CombinedResourceHandler<>(
        new TankAccess(tankFuel, true, false),
        new TankAccess(tankCoolant, true, false),
        new TankAccess(tankResidue, false, true)
    );

    /** 1.12.2's {@code TankManager}: all three tanks with their own filters, for a player's fluid container. */
    public final ResourceHandler<FluidResource> allTanks = new CombinedResourceHandler<>(tankFuel, tankCoolant, tankResidue);

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
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("tanks").ifPresent(tanks -> {
            tanks.child("fuel").ifPresent(tankFuel::deserialize);
            tanks.child("coolant").ifPresent(tankCoolant::deserialize);
            tanks.child("residue").ifPresent(tankResidue::deserialize);
        });
        penaltyCooling = input.getIntOr("penaltyCooling", 0);
        burnTime = input.getDoubleOr("burnTime", 0);
        residueAmount = Math.max(0, input.getDoubleOr("residueAmount", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput tanks = output.child("tanks");
        tankFuel.serialize(tanks.child("fuel"));
        tankCoolant.serialize(tanks.child("coolant"));
        tankResidue.serialize(tanks.child("residue"));
        output.putInt("penaltyCooling", penaltyCooling);
        output.putDouble("burnTime", burnTime);
        output.putDouble("residueAmount", residueAmount);
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
        return !tankFuel.isEmpty() && penaltyCooling == 0 && isRedstonePowered;
    }

    @Override
    protected void burn() {
        final FluidResource fuel = tankFuel.getResource(0);
        int fuelAmount = tankFuel.getAmountAsInt(0);
        if (!fuel.isEmpty() && (currentFuel == null || !currentFuel.getFluid().equals(fuel))) {
            currentFuel = BuildcraftFuelRegistry.fuel.getFuel(fuel);
        }
        // 1.12.2's "fuel == null": an empty tank with nothing left burning. An empty tank while burnTime is still
        // positive is 1.12.2's zero-amount stack instead -- see this class's javadoc.
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
                            fuelAmount--;
                            tankFuel.set(0, fuelAmount > 0 ? fuel : FluidResource.EMPTY, fuelAmount);
                            burnTime += currentFuel.getTotalBurningTime() / 1000.0;

                            // If we also produce residue then put it out too
                            if (currentFuel instanceof IDirtyFuel dirtyFuel) {
                                FluidStack residueFluid = dirtyFuel.getResidue();
                                residueAmount += residueFluid.getAmount() / 1000.0;
                                if (residueAmount >= 1) {
                                    residueAmount -= fillResidue(FluidResource.of(residueFluid), Mth.floor(residueAmount));
                                }
                            }
                        } else {
                            tankFuel.set(0, FluidResource.EMPTY, 0);
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

    private int fillResidue(FluidResource residue, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int filled = tankResidue.insert(0, residue, amount, transaction);
            transaction.commit();
            return filled;
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

    /** 1.12.2's inlined {@code coolEngine(target)} + {@code fillCoolingBuffer()} blocks, including its behaviour of
     * spending a whole tick's worth of coolant (up to {@value #MAX_COOLANT_PER_TICK} mB) however little heat there
     * is to remove. */
    private void coolEngine(double target) {
        double coolingBuffer = 0;
        double extraHeat = heat - target;

        if (extraHeat > 0) {
            int available = tankCoolant.getAmountAsInt(0);
            if (available > 0) {
                FluidResource coolant = tankCoolant.getResource(0);
                float coolPerMb = BuildcraftFuelRegistry.coolant.getDegreesPerMb(coolant, (float) heat);
                if (coolPerMb > 0) {
                    int coolantAmount = Math.min(MAX_COOLANT_PER_TICK, available);
                    coolingBuffer += coolantAmount * coolPerMb;
                    int left = available - coolantAmount;
                    tankCoolant.set(0, left > 0 ? coolant : FluidResource.EMPTY, left);
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

    /** Unused, exactly as in 1.12.2 -- see {@code TileEngineStone#explosionRange()}. */
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

    private boolean isValidFuel(FluidResource fluid) {
        return BuildcraftFuelRegistry.fuel.getFuel(fluid) != null;
    }

    private boolean isValidCoolant(FluidResource fluid) {
        return BuildcraftFuelRegistry.coolant.getCoolant(fluid) != null;
    }

    private boolean isResidue(FluidResource fluid) {
        // If this is the client then we don't have a current fuel- just trust the server that its correct
        if (level != null && level.isClientSide()) {
            return true;
        }
        if (currentFuel instanceof IDirtyFuel dirtyFuel) {
            return FluidResource.of(dirtyFuel.getResidue()).equals(fluid);
        }
        return false;
    }

    /** @return The tank at {@code index} in GUI order (fuel, coolant, residue), or {@code null} if out of range. */
    @Nullable
    public Tank getTank(int index) {
        return switch (index) {
            case 0 -> tankFuel;
            case 1 -> tankCoolant;
            case 2 -> tankResidue;
            default -> null;
        };
    }

    /**
     * 1.12.2's {@code Tank#transferStackToTank}: first tries to empty one of {@code stack} into {@code tank} (a solid
     * coolant, for the coolant tank, or any fluid container), then to fill one of {@code stack} from {@code tank}.
     * Server side only.
     *
     * @return What should replace {@code stack} (the cursor stack, or the shift-clicked slot's contents). Any extra
     *         item produced (an empty bucket from a stack of several full ones) goes to the player's inventory.
     */
    public ItemStack transferStackToTank(Tank tank, Player player, ItemStack stack) {
        if (level == null || level.isClientSide() || stack.isEmpty()) {
            return stack;
        }
        boolean isCreative = player.hasInfiniteMaterials();
        ItemStack copy = stack.copyWithCount(1);
        int space = tank.getCapacity() - tank.getAmountAsInt(0);

        // 1: from the item into the tank. tankCoolant's own map() override came first in 1.12.2.
        if (tank == tankCoolant) {
            ISolidCoolant solid = BuildcraftFuelRegistry.coolant.getSolidCoolant(copy);
            FluidStack fluidCoolant = solid == null ? null : solid.getFluidFromSolidCoolant(copy);
            if (fluidCoolant != null && fluidCoolant.getAmount() > 0 && fluidCoolant.getAmount() <= space) {
                FluidResource resource = FluidResource.of(fluidCoolant);
                int amount = fluidCoolant.getAmount();
                try (Transaction transaction = Transaction.openRoot()) {
                    int accepted = tank.insert(0, resource, amount, transaction);
                    if (isCreative ? accepted > 0 : accepted == amount) {
                        transaction.commit();
                        FluidUtil.triggerSoundAndGameEvent(resource, level, player.position(), player, false);
                        return isCreative ? stack : shrunk(stack, ItemStack.EMPTY, player);
                    }
                }
            }
        }

        ItemStacksResourceHandler slot = new ItemStacksResourceHandler(1);
        slot.set(0, ItemResource.of(copy), 1);
        ResourceHandler<FluidResource> itemFluid = ItemAccess.forHandlerIndexStrict(slot, 0).getCapability(Capabilities.Fluid.ITEM);
        if (itemFluid == null) {
            return stack;
        }
        if (space > 0) {
            try (Transaction transaction = Transaction.openRoot()) {
                ResourceStack<FluidResource> moved = ResourceHandlerUtil.moveFirst(itemFluid, tank, r -> true, space, transaction);
                if (moved != null) {
                    transaction.commit();
                    FluidUtil.triggerSoundAndGameEvent(moved.resource(), level, player.position(), player, false);
                    return isCreative ? stack : shrunk(stack, slotContents(slot), player);
                }
            }
        }

        // 2: from the tank into the item.
        try (Transaction transaction = Transaction.openRoot()) {
            ResourceStack<FluidResource> moved = ResourceHandlerUtil.moveFirst(tank, itemFluid, r -> true, tank.getCapacity(), transaction);
            if (moved != null) {
                transaction.commit();
                FluidUtil.triggerSoundAndGameEvent(moved.resource(), level, player.position(), player, true);
                return isCreative ? stack : shrunk(stack, slotContents(slot), player);
            }
        }
        return stack;
    }

    private static ItemStack slotContents(ItemStacksResourceHandler slot) {
        return slot.getResource(0).toStack(slot.getAmountAsInt(0));
    }

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

    /** 1.12.2's {@code Tank#onGuiClicked}: the tank widget at {@code index} was clicked with {@code menu}'s cursor
     * stack. */
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
        return new ContainerEngineIron(BCEnergyRegistries.ENGINE_IRON_MENU.get(), windowId, playerInv, this);
    }

    /** See {@code TileEngineStone#writeClientSideData}. */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }

    /** One tank as seen from outside: 1.12.2's {@code TankProperties(tank, canFill, canDrain)}. */
    private static final class TankAccess extends DelegatingResourceHandler<FluidResource> {
        private final boolean canFill;
        private final boolean canDrain;

        TankAccess(Tank tank, boolean canFill, boolean canDrain) {
            super(tank);
            this.canFill = canFill;
            this.canDrain = canDrain;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return canFill && super.isValid(index, resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return canFill ? super.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            return canFill ? super.insert(resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return canDrain ? super.extract(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            return canDrain ? super.extract(resource, amount, transaction) : 0;
        }
    }
}
