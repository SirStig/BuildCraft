/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.loot.NeoForgeLootContextParams;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.energy.container.ContainerEngineStone;

import buildcraft.BCEnergyRegistries;

/**
 * Renamed from 1.12.2's {@code TileEngineStone_BC8} (see {@code TileEngineWood}'s own javadoc for the general
 * "drop the version-tag suffix" convention this follows). A solid-fuel-burning engine: it consumes a vanilla
 * furnace-style fuel item from a single slot and produces MJ power, smoothed by a small PID-like control loop
 * that is a direct, unchanged port of the original's {@link #getCurrentOutput()} arithmetic.
 *
 * <p><b>The fuel slot</b> ({@link #invFuel}) is a single-slot {@link ItemHandlerSimple}, reachable from every
 * {@link EnumPipePart} with {@link EnumAccess#BOTH} -- unchanged from 1.12.2. {@link #isValidFuel} preserves a
 * real, deliberate 1.12.2 mechanic, not incidental behaviour to "fix": while {@link #isForceInserting} is set,
 * <em>any</em> item may occupy the slot, not just fuel. This is what lets the engine hand back a fuel item's
 * container item (e.g. the empty bucket left over from burning lava) into the same slot the fuel came from, even
 * though an empty bucket is not itself flammable -- {@link #burn()} sets the flag immediately before force-writing
 * the container item with {@link ItemHandlerSimple#setStackInSlot}, and {@link #onSlotChange} clears it again once
 * the player empties the slot. Ported faithfully, including the resulting narrow window (between a container item
 * landing in the slot and it being removed) where an unrelated item could technically also be inserted by an
 * external pipe -- see {@code TileFloodGate}'s own progress-entry precedent in PORTING.md for why a genuine
 * upstream quirk like this is preserved rather than "fixed".
 *
 * <p><b>Fuel burn-time lookup is a genuine platform divergence, not a rename.</b> 1.12.2's
 * {@code TileEntityFurnace.getItemBurnTime(stack)} has no direct equivalent on this target: fuel value is now the
 * {@link CookingFuel} data component (a {@link ResolvableInt} burn time plus a speed multiplier, evaluated
 * against a {@link LootContext}), reached the same way vanilla's own
 * {@code AbstractFurnaceBlockEntity#getBurnDuration} resolves it -- confirmed by decompiling that class from this
 * target's own sources jar. This tile isn't a {@code Container}/{@code BaseContainerBlockEntity} (unlike a real
 * furnace), so {@link #getItemBurnTime} builds its own {@link LootContext} rather than inheriting
 * {@code BaseContainerBlockEntity#getLootContext}: {@link LootContextParamSets#CONTAINER_PROCESS} requires all
 * four of {@code BLOCK_STATE}, {@code BLOCK_ENTITY}, {@code ORIGIN} and {@code CONTAINER} (confirmed by reading
 * that param set's own registration -- {@code LootParams.Builder#create} validates every required key eagerly, so
 * omitting one throws rather than silently defaulting), with only {@code NeoForgeLootContextParams.QUERIED_STACK}
 * optional. {@code CONTAINER} is typed {@code SlotProvider} (a single {@code getSlot(int)} method), not
 * {@code Container} -- this tile supplies a no-op stub, since nothing about a plain burn-time lookup ever queries
 * it. A stack with no {@link DataComponents#COOKING_FUEL} component resolves to a burn time of 0 either way,
 * matching {@code getFuel()}'s vanilla non-fuel semantics on 1.20.1 -- see {@code buildcraft.energy}'s own
 * PORTING.md progress entry for the exact API research trail.
 *
 * <p><b>The container item lookup is also a genuine divergence.</b> 1.12.2's
 * {@code fuel.getItem().getContainerItem(fuel)} becomes {@code ItemStack#getCraftingRemainder()} here -- a
 * NeoForge extension default method ({@code ItemInstanceExtension}, mixed into both {@link ItemStack} and
 * {@link ItemStackTemplate}) that returns a <em>nullable {@link ItemStackTemplate}</em>, not an {@link ItemStack}
 * directly; {@link ItemStackTemplate#create()} produces the real stack. Confirmed via this target's own decompiled
 * {@code AbstractFurnaceBlockEntity#consumeFuel}, which does exactly this for a burning lava bucket.
 *
 * <p><b>Dropped from the original GUI, out of scope for this pass:</b> the in-GUI help/tooltip framework
 * ({@code LedgerEngine}, {@code DummyHelpElement}, {@code ElementHelpInfo}) that 1.12.2's
 * {@code GuiEngineStone_BC8} wired up -- not ported anywhere in this port yet, on either platform, the same way
 * the auto-workbench batch already dropped the vanilla recipe-book integration as pure polish with no bearing on
 * this machine's own logic. {@code deltaFuelLeft}/{@code DeltaInt}/{@code deltaManager} (1.12.2's own pre-
 * {@code DataSlot} GUI-sync mechanism) are not ported either; {@link #getFuelPercentForSync()}/
 * {@link #setFuelPercentForSync(int)} replace it with a single container {@code DataSlot}, exactly like
 * {@code TileAutoWorkbenchBase#getPowerStoredForSync}/{@code #setPowerStoredForSync} already did for that
 * machine's own progress bar -- see {@link ContainerEngineStone}.
 */
public class TileEngineStone extends TileEngineBase implements MenuProvider {
    private static final long MAX_OUTPUT = MjAPI.MJ;
    private static final long MIN_OUTPUT = MAX_OUTPUT / 3;
    private static final float kp = 1f;
    private static final float ki = 0.05f;
    private static final long eLimit = (MAX_OUTPUT - MIN_OUTPUT) * 20;

    public final ItemHandlerManager itemManager = new ItemHandlerManager(this::onSlotChange);
    public final ItemHandlerSimple invFuel;

    private int burnTime = 0;
    private int totalBurnTime = 0;
    private long esum = 0;

    private boolean isForceInserting = false;

    /** 0-100. Server: kept up to date by {@link #engineUpdate()} whenever {@link #burnTime}/{@link #totalBurnTime}
     * change. Client: written only by {@link ContainerEngineStone}'s synced {@code DataSlot} -- see
     * {@link #getFuelPercentForSync()}. */
    private int fuelPercent;

    public TileEngineStone(BlockPos pos, BlockState state) {
        super(BCEnergyRegistries.ENGINE_STONE_TYPE.get(), pos, state);
        invFuel = itemManager.addInvHandler("fuel", 1, this::isValidFuel, EnumAccess.BOTH, EnumPipePart.VALUES);
    }

    private boolean isValidFuel(int slot, ItemResource resource) {
        // Always allow inserting container items if they aren't fuel -- see this class's own javadoc.
        return isForceInserting || getItemBurnTime(resource.toStack(1)) > 0;
    }

    private void onSlotChange(ItemHandlerSimple handler, int slot, ItemStack before, ItemStack after) {
        if (handler == invFuel && isForceInserting && after.isEmpty()) {
            isForceInserting = false;
        }
    }

    // TileBC overrides

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        itemManager.deserialize(input);
        burnTime = input.getIntOr("burnTime", 0);
        totalBurnTime = input.getIntOr("totalBurnTime", 0);
        esum = input.getLongOr("esum", 0);
        fuelPercent = totalBurnTime <= 0 ? 0 : (burnTime * 100) / totalBurnTime;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemManager.serialize(output);
        output.putInt("burnTime", burnTime);
        output.putInt("totalBurnTime", totalBurnTime);
        output.putLong("esum", esum);
    }

    // Engine overrides

    @NotNull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(false);
    }

    @Override
    public boolean isBurning() {
        return burnTime > 0;
    }

    @Override
    protected void engineUpdate() {
        super.engineUpdate();
        if (burnTime > 0) {
            burnTime--;
            if (getPowerStage() != EnumPowerStage.OVERHEAT) {
                long output = getCurrentOutput();
                currentOutput = output;
                addPower(output);
            }
        }
        fuelPercent = totalBurnTime <= 0 ? 0 : (burnTime * 100) / totalBurnTime;
    }

    @Override
    protected void burn() {
        if (burnTime == 0 && isRedstonePowered) {
            ItemStack current = invFuel.getStackInSlot(0);
            totalBurnTime = getItemBurnTime(current);
            burnTime = totalBurnTime;

            if (burnTime > 0) {
                ItemStack fuel = current.copyWithCount(1);
                ItemStack remaining = current.copy();
                remaining.shrink(1);
                invFuel.setStackInSlot(0, remaining);

                ItemStackTemplate remainderTemplate = fuel.getCraftingRemainder();
                ItemStack container = remainderTemplate == null ? ItemStack.EMPTY : remainderTemplate.create();
                if (!container.isEmpty()) {
                    if (invFuel.getStackInSlot(0).isEmpty()) {
                        isForceInserting = false;
                        int inserted;
                        try (Transaction transaction = Transaction.openRoot()) {
                            inserted = invFuel.insert(ItemResource.of(container), container.getCount(), transaction);
                            if (inserted > 0) {
                                transaction.commit();
                            }
                        }
                        if (inserted < container.getCount()) {
                            isForceInserting = true;
                            ItemStack leftover = container.copy();
                            leftover.shrink(inserted);
                            invFuel.setStackInSlot(0, leftover);
                        }
                    } else if (level != null) {
                        // Not good!
                        InventoryUtil.addToBestAcceptor(level, getBlockPos(), null, container);
                    }
                }
            }
        }
    }

    /** See this class's own javadoc for the full account of why this isn't a rename of
     * {@code TileEntityFurnace.getItemBurnTime}. */
    private int getItemBurnTime(ItemStack stack) {
        if (stack.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        LootContext context = new LootContext.Builder(
            new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.BLOCK_STATE, getBlockState())
                .withParameter(LootContextParams.BLOCK_ENTITY, this)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(getBlockPos()))
                .withParameter(LootContextParams.CONTAINER, slot -> null)
                .withOptionalParameter(NeoForgeLootContextParams.QUERIED_STACK, stack)
                .create(LootContextParamSets.CONTAINER_PROCESS)
        ).create(Optional.empty());
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
    }

    @Override
    public long maxPowerReceived() {
        return 200 * MjAPI.MJ;
    }

    @Override
    public long maxPowerExtracted() {
        return 100 * MjAPI.MJ;
    }

    @Override
    public long getMaxPower() {
        return 1000 * MjAPI.MJ;
    }

    /** Never actually called anywhere in {@link TileEngineBase} on this platform -- confirmed by reading 1.12.2's
     * own {@code TileEngineBase_BC8} (~line 469), where the one call site ({@code worldObj.createExplosion(...)})
     * is commented out in the <em>original</em> source itself. 1.12.2's own engines never actually explode on
     * overheat despite the plumbing being there; this faithfully returns the same constant the original did,
     * without adding a working explosion mechanic that never existed upstream. */
    @Override
    public float explosionRange() {
        return 2;
    }

    @Override
    public long getCurrentOutput() {
        long e = 3 * getMaxPower() / 8 - power;
        esum = clamp(esum + e, -eLimit, eLimit);
        return clamp(e + esum / 20, MIN_OUTPUT, MAX_OUTPUT);
    }

    private static long clamp(long val, long min, long max) {
        return Math.max(min, Math.min(max, val));
    }

    /** @return 0-100, how much of the currently-burning fuel item's total burn time remains. Read by
     *          {@link ContainerEngineStone}'s synced {@code DataSlot} on the server, and written back into
     *          {@link #fuelPercent} by that same slot's {@code set(int)} on the client -- see this class's own
     *          javadoc. */
    public int getFuelPercentForSync() {
        return fuelPercent;
    }

    public void setFuelPercentForSync(int value) {
        this.fuelPercent = value;
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        super.getDebugInfo(left, right, side);
        left.add("esum = " + MjAPI.formatMj(esum) + " M");
        long e = 3 * getMaxPower() / 8 - power;
        left.add("output = " + MjAPI.formatMj(clamp(e + esum / 20, MIN_OUTPUT, MAX_OUTPUT)) + " MJ");
        left.add("burnTime = " + burnTime + " / " + totalBurnTime);
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerEngineStone(BCEnergyRegistries.ENGINE_STONE_MENU.get(), windowId, playerInv, this);
    }

    /** Called server-side (once, when the menu is first opened) so the client can look up this same tile again --
     * see {@link ContainerEngineStone}'s client-side factory constructor. */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }
}
