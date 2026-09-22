/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;

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
 * Renamed from 1.12.2's {@code TileEngineStone_BC8}. Mirrors the 26.x class of the same name -- see that one's
 * javadoc for the full account of the fuel slot's {@link #isValidFuel}/{@link #isForceInserting} mechanic, the
 * PID-like {@link #getCurrentOutput()} smoothing (a direct, unchanged port on both platforms), and what was
 * deliberately dropped from 1.12.2's GUI (the ledger/help-tooltip framework, {@code DeltaInt}/{@code deltaManager}
 * in favour of {@link #getFuelPercentForSync()}'s single container {@code DataSlot}).
 *
 * <p><b>Fuel burn-time lookup is much simpler here than on 26.x</b> -- see that class's javadoc for the full 26.x
 * account. This target still has {@code TileEntityFurnace.getItemBurnTime}'s modern equivalent as a one-line
 * call: {@code net.minecraftforge.common.ForgeHooks.getBurnTime(stack, RecipeType<?>)}, confirmed via decompiling
 * this target's own {@code AbstractFurnaceBlockEntity#isFuel}, which calls exactly this with a {@code null}
 * recipe type, matching "is this fuel at all", not "is this fuel for smelting specifically" -- this tile passes
 * {@code null} the same way, since a Stirling Engine burns any real furnace fuel, not just smelting fuel.
 * {@code ItemStack#getBurnTime(RecipeType<?>)} (the {@code IForgeItemStack} default method mixed into
 * {@link ItemStack} itself) looks like the natural one-line call, and compiles fine, but is <em>not</em> the
 * resolved lookup -- see {@link #getItemBurnTime}'s own javadoc for the real defect this caused and how it was
 * caught.
 *
 * <p><b>The container item lookup also has a direct 1.20.1 equivalent</b>, unlike 26.x's
 * {@code ItemStack#getCraftingRemainder()} (which returns a nullable {@code ItemStackTemplate}, not an
 * {@link ItemStack}): this target keeps 1.12.2's stack-aware shape almost unchanged as
 * {@link ItemStack#hasCraftingRemainingItem()}/{@link ItemStack#getCraftingRemainingItem()}, confirmed via this
 * target's own decompiled {@code AbstractFurnaceBlockEntity#burn}, which calls exactly this pair for a burning
 * lava bucket.
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
     * change. Client: written only by {@link ContainerEngineStone}'s synced {@code DataSlot}. */
    private int fuelPercent;

    public TileEngineStone(BlockPos pos, BlockState state) {
        super(BCEnergyRegistries.ENGINE_STONE_TYPE.get(), pos, state);
        invFuel = itemManager.addInvHandler("fuel", 1, this::isValidFuel, EnumAccess.BOTH, EnumPipePart.VALUES);
    }

    private boolean isValidFuel(int slot, ItemStack stack) {
        // Always allow inserting container items if they aren't fuel -- see the 26.x class's own javadoc.
        return isForceInserting || getItemBurnTime(stack) > 0;
    }

    private void onSlotChange(IItemHandlerModifiable handler, int slot, ItemStack before, ItemStack after) {
        if (handler == invFuel && isForceInserting && after.isEmpty()) {
            isForceInserting = false;
        }
    }

    // TileBC overrides

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemManager.deserializeNBT(nbt.getCompound("inv_manager"));
        burnTime = nbt.getInt("burnTime");
        totalBurnTime = nbt.getInt("totalBurnTime");
        esum = nbt.getLong("esum");
        fuelPercent = totalBurnTime <= 0 ? 0 : (burnTime * 100) / totalBurnTime;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("inv_manager", itemManager.serializeNBT());
        nbt.putInt("burnTime", burnTime);
        nbt.putInt("totalBurnTime", totalBurnTime);
        nbt.putLong("esum", esum);
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
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
            totalBurnTime = getItemBurnTime(invFuel.getStackInSlot(0));
            burnTime = totalBurnTime;

            if (burnTime > 0) {
                ItemStack fuel = invFuel.extractItem(0, 1, false);
                ItemStack container = fuel.hasCraftingRemainingItem() ? fuel.getCraftingRemainingItem() : ItemStack.EMPTY;
                if (!container.isEmpty()) {
                    if (invFuel.getStackInSlot(0).isEmpty()) {
                        isForceInserting = false;
                        ItemStack leftover = invFuel.insertItem(0, container, false);
                        if (!leftover.isEmpty()) {
                            isForceInserting = true;
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

    /** See this class's own javadoc for why this is a one-line call on this target, unlike 26.x.
     *
     * <p><b>Real defect found and fixed during RCON verification:</b> {@code ItemStack#getBurnTime(RecipeType)}
     * itself (the {@code IForgeItemStack} default method) is <em>not</em> the fully-resolved lookup -- it is the
     * per-item override hook, which returns {@code -1} as a sentinel for "this item doesn't override its burn
     * time, fall back to vanilla's own {@code FurnaceBlockEntity.getFuel()} map". {@code ForgeHooks#getBurnTime}
     * is what actually resolves that sentinel (and fires {@code ForgeEventFactory#getItemBurnTime}) -- confirmed
     * by decompiling it: {@code int ret = stack.getBurnTime(recipeType); return
     * ForgeEventFactory.getItemBurnTime(stack, ret == -1 ? VANILLA_BURNS.getOrDefault(...) : ret, recipeType)}.
     * Calling {@code stack.getBurnTime(null)} directly, as an earlier version of this method did, made every
     * plain vanilla fuel item (coal included) resolve to {@code -1} -- caught only once a real coal item was
     * inserted via RCON and the fuel slot silently refused it ({@code isValidFuel} never went true, since
     * {@code -1 > 0} is false). {@link net.minecraftforge.common.ForgeHooks#getBurnTime} is the correct call, and
     * already returns {@code 0} for an empty stack itself. */
    private static int getItemBurnTime(ItemStack stack) {
        return net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null);
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

    /** Never actually called anywhere in {@link TileEngineBase} on this platform -- see the 26.x class's own
     * javadoc for the full account of why this is a faithful port of upstream's own dead explosion plumbing, not
     * a gap. */
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

    /** @return 0-100, how much of the currently-burning fuel item's total burn time remains. */
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
        return new ContainerEngineStone(windowId, playerInv, this);
    }
}
