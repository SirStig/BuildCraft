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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

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
 * neighbouring RF source and converts it into MJ, at a rate the four upgrade slots (iron/gold gears) can boost.
 *
 * <p><b>RF storage and exposure is a genuine platform divergence, not a rename.</b> 1.12.2's {@code Rf} inner
 * class was a hand-rolled {@code net.minecraftforge.energy.IEnergyStorage}: an {@code int currentRF} field,
 * {@code receiveEnergy} accumulating into it, {@code extractEnergy} always refusing (this engine only ever
 * consumes RF, never gives it back). 26.x has no {@code IEnergyStorage} at all -- confirmed via {@code javap}
 * against the real 26.3 {@code neoforge-universal.jar}: energy capabilities are now
 * {@code net.neoforged.neoforge.transfer.energy.EnergyHandler}, moved inside a rollback-capable
 * {@code Transaction} the same way {@link buildcraft.api.mj.MjToRfAutoConvertor} (the *other* direction --
 * presenting an MJ machine as RF, not RF as fuel for an MJ machine, so it does not apply here; see that class's
 * own javadoc) already had to account for. Rather than hand-writing a second {@code SnapshotJournal}, this uses
 * NeoForge's own ready-made {@link SimpleEnergyHandler} (confirmed present in the real jar, not guessed): a
 * capacity-and-rate-limited transactional int store that already implements {@code EnergyHandler} and
 * {@code ValueIOSerializable} correctly, configured with {@code maxExtract = 0} to reproduce 1.12.2's
 * always-refuse-extraction behaviour for free rather than overriding {@code extract} to return {@code 0} by hand.
 *
 * <p>Everything downstream of that storage -- the upgrade-slot MJ/tick bonus, the heat ramp while consuming RF,
 * the RF-to-MJ conversion via {@link BCLibConfig#mjRfConversion}, and every {@link TileEngineBase} override -- is
 * a direct, unchanged port of 1.12.2's own arithmetic.
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

    /** See this class's own javadoc for why this replaces 1.12.2's hand-rolled {@code Rf} inner class. */
    public final SimpleEnergyHandler rfEnergy = new SimpleEnergyHandler(MAX_RF, MAX_RF, 0);

    /** 0-100. Server: kept up to date by {@link #engineUpdate()}. Client: written only by
     * {@link ContainerEngineRF}'s synced {@code DataSlot} -- see {@link #getRfPercentForSync()}. */
    private int rfPercent;

    public TileEngineRF(BlockPos pos, BlockState state) {
        super(BCEnergyRegistries.ENGINE_RF_TYPE.get(), pos, state);
        invUpgrades = itemManager.addInvHandler("upgrades", 4, this::isValidUpgrade, EnumAccess.NONE);
    }

    private boolean isValidUpgrade(int slot, ItemResource resource) {
        return RF_UPGRADE.containsKey(resource.getItem());
    }

    // TileBC overrides

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemManager.serialize(output.child("inv_manager"));
        rfEnergy.serialize(output.child("rf"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("inv_manager").ifPresent(itemManager::deserialize);
        input.child("rf").ifPresent(rfEnergy::deserialize);
    }

    // Engine overrides

    @NotNull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(false);
    }

    @Override
    public boolean isBurning() {
        return rfEnergy.getAmountAsLong() > 0 && isRedstonePowered;
    }

    public long getMjPerTick() {
        long value = MjAPI.MJ * 4;
        for (int slot = 0; slot < invUpgrades.size(); slot++) {
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
        long currentRF = rfEnergy.getAmountAsLong();
        if (currentRF <= 0) {
            return;
        }

        if (isRedstonePowered) {
            long mjPerRf = BCLibConfig.mjRfConversion.mjPerRf;
            long maxRf = getRfConsumptionRate();

            long rfConsumed = Math.min(currentRF, maxRf);
            long mjGenerated = rfConsumed * mjPerRf;

            if (power + mjGenerated >= getMaxPower()) {
                return;
            }

            currentOutput = mjGenerated;
            addPower(mjGenerated);
            rfEnergy.set((int) (currentRF - rfConsumed));
            heat += HEAT_RATE;
            if (heat >= 200) {
                heat = 200;
            }
        }
    }

    @Override
    protected void engineUpdate() {
        super.engineUpdate();
        rfPercent = (int) (rfEnergy.getAmountAsLong() * 100 / MAX_RF);
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
        return rfEnergy.getAmountAsLong() > 0 ? getMjPerTick() : 0;
    }

    public long getCurrentRF() {
        return rfEnergy.getAmountAsLong();
    }

    /** @return 0-100, how full the RF buffer is -- read by {@link ContainerEngineRF}'s synced {@code DataSlot},
     *          the same per-tick GUI sync {@code TileEngineStone#getFuelPercentForSync()} uses and for the same
     *          reason: {@link #markDirtyAndSync()} only fires on a meaningful state change, not every tick. */
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
        return new ContainerEngineRF(BCEnergyRegistries.ENGINE_RF_MENU.get(), windowId, playerInv, this);
    }

    /** Called server-side (once, when the menu is first opened) so the client can look up this same tile again --
     * see {@link ContainerEngineRF}'s client-side factory constructor. */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }
}
