/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.tile;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

import buildcraft.lib.BCLibConfig;
import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.energy.container.ContainerEngineRF;

import buildcraft.BCCoreRegistries;
import buildcraft.BCEnergyRegistries;

/**
 * Renamed from 1.12.2's {@code TileEngineRF} -- an engine that consumes Forge/NeoForge Redstone Flux from a
 * neighbouring RF source and converts it into MJ, boosted by up to four iron/gold gear upgrades. Unlike 26.x
 * (see that copy of this class for the full account of its genuine {@code EnergyHandler}/transaction-based
 * divergence), this target still has the classic {@code net.minecraftforge.energy.IEnergyStorage}/
 * {@code CapabilityEnergy} pair 1.12.2 itself used, so 1.12.2's own hand-rolled {@code Rf} inner class -- an
 * {@code int currentRF} field, {@code receiveEnergy} accumulating into it, {@code extractEnergy} always refusing
 * -- ports over essentially unchanged, exposed the same way {@link TileEngineStone}'s item capability is: a
 * {@link #getCapability} override, not a {@code RegisterCapabilitiesEvent} listener.
 */
public class TileEngineRF extends TileEngineBase implements MenuProvider {
    private static final int MAX_RF = 10_000;
    private static final double HEAT_RATE = 0.06;
    private static final double COOLDOWN_RATE = 0.01;

    private static final Map<Item, Long> RF_UPGRADE = new LinkedHashMap<>();

    static {
        RF_UPGRADE.put(BCCoreRegistries.GEAR_IRON.get(), MjAPI.MJ * 2);
        RF_UPGRADE.put(BCCoreRegistries.GEAR_GOLD.get(), MjAPI.MJ * 3);
    }

    public final ItemHandlerManager itemManager = new ItemHandlerManager(null);
    public final ItemHandlerSimple invUpgrades;
    private final Rf rf = new Rf();
    private final LazyOptional<IEnergyStorage> rfCap = LazyOptional.of(() -> rf);

    /** 0-100. Server: kept up to date by {@link #engineUpdate()}. Client: written only by
     * {@link ContainerEngineRF}'s synced {@code DataSlot} -- see {@link #getRfPercentForSync()}. */
    private int rfPercent;

    public TileEngineRF(BlockPos pos, BlockState state) {
        super(BCEnergyRegistries.ENGINE_RF_TYPE.get(), pos, state);
        invUpgrades = itemManager.addInvHandler("upgrades", 4, this::isValidUpgrade, EnumAccess.NONE);
    }

    private boolean isValidUpgrade(int slot, ItemStack stack) {
        return RF_UPGRADE.containsKey(stack.getItem());
    }

    // TileBC overrides

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemManager.deserializeNBT(nbt.getCompound("inv_manager"));
        rf.currentRF = nbt.getInt("currentRF");
        rfPercent = rf.currentRF * 100 / MAX_RF;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("inv_manager", itemManager.serializeNBT());
        nbt.putInt("currentRF", rf.currentRF);
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ENERGY && side == getCurrentFacing()) {
            return rfCap.cast();
        }
        LazyOptional<T> itemCap = itemManager.getCapability(cap, side);
        if (itemCap.isPresent()) {
            return itemCap;
        }
        return super.getCapability(cap, side);
    }

    // Engine overrides

    @NotNull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(false);
    }

    @Override
    public boolean isBurning() {
        return rf.currentRF > 0 && isRedstonePowered;
    }

    public long getMjPerTick() {
        long value = MjAPI.MJ * 4;
        for (int slot = 0; slot < invUpgrades.getSlots(); slot++) {
            ItemStack stack = invUpgrades.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Long add = RF_UPGRADE.get(stack.getItem());
            if (add != null) {
                value += add;
            }
        }
        return value;
    }

    public int getRfConsumptionRate() {
        long mjPerTick = getMjPerTick();
        long mjPerRf = BCLibConfig.mjRfConversion.mjPerRf;
        return (int) (mjPerTick / mjPerRf);
    }

    @Override
    protected void burn() {
        if (rf.currentRF <= 0) {
            return;
        }

        if (isRedstonePowered) {
            long mjPerRf = BCLibConfig.mjRfConversion.mjPerRf;
            int maxRf = getRfConsumptionRate();

            int rfConsumed = Math.min(rf.currentRF, maxRf);
            long mjGenerated = rfConsumed * mjPerRf;

            if (power + mjGenerated >= getMaxPower()) {
                return;
            }

            currentOutput = mjGenerated;
            addPower(mjGenerated);
            rf.currentRF -= rfConsumed;
            heat += HEAT_RATE;
            if (heat >= 200) {
                heat = 200;
            }
        }
    }

    @Override
    protected void engineUpdate() {
        super.engineUpdate();
        rfPercent = rf.currentRF * 100 / MAX_RF;
    }

    @Override
    public void updateHeatLevel() {
        if (heat > MIN_HEAT) {
            heat -= COOLDOWN_RATE;
        }

        if (heat <= MIN_HEAT) {
            heat = MIN_HEAT;
        }

        getPowerStage();
    }

    @Override
    public long getMaxPower() {
        return 1000 * MjAPI.MJ;
    }

    @Override
    public long maxPowerReceived() {
        return 200 * MjAPI.MJ;
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
    public double getPistonSpeed() {
        return switch (getPowerStage()) {
            case BLUE -> 0.04;
            case GREEN -> 0.05;
            case YELLOW -> 0.06;
            case RED -> 0.07;
            default -> 0;
        };
    }

    @Override
    public long getCurrentOutput() {
        return rf.currentRF > 0 ? getMjPerTick() : 0;
    }

    public int getCurrentRF() {
        return rf.currentRF;
    }

    public int getRfPercentForSync() {
        return rfPercent;
    }

    public void setRfPercentForSync(int value) {
        this.rfPercent = value;
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerEngineRF(windowId, playerInv, this);
    }

    private static final class Rf implements IEnergyStorage {
        int currentRF;

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int max = Math.min(MAX_RF - currentRF, maxReceive);
            if (max <= 0) {
                return 0;
            }

            if (!simulate) {
                currentRF += max;
            }
            return max;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return currentRF;
        }

        @Override
        public int getMaxEnergyStored() {
            return MAX_RF;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
