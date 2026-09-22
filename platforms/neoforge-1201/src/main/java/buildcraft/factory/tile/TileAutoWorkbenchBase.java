/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.Arrays;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.tiles.IHasWork;
import buildcraft.api.tiles.TilesAPI;

import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.craft.WorkbenchCrafting;
import buildcraft.lib.tile.item.IAutoCraft;
import buildcraft.lib.tile.item.ItemHandlerFiltered;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.factory.container.ContainerAutoCraftItems;

/**
 * A fixed vanilla-crafting-table recipe, configured by a player dragging items into a phantom 3x3 blueprint grid,
 * that then automatically pulls matching materials from piped-in items and produces the crafted result over
 * time, powered by MJ. Mirrors the 26.x class of the same name -- see that one's javadoc for the full account of
 * what changed from 1.12.2 (the dropped recipe book, the dropped id-tagged network payloads, the direct
 * {@link IMjRedstoneReceiver} implementation).
 *
 * <p>This file differs from the 26.x copy only in the usual 1.20.1 places: NBT is still {@link CompoundTag}
 * rather than {@code ValueInput}/{@code ValueOutput}, capabilities are exposed by the block entity itself through
 * {@code getCapability} rather than registered against the block entity type (matching {@code TileChute}/
 * {@code TileMiner}'s own precedent), and {@link WorkbenchCrafting} is handed {@link #invMaterials}/
 * {@link #invResult} directly as {@link buildcraft.api.inventory.IItemTransactor} -- {@link ItemHandlerSimple}
 * already implements that interface on this target, so no {@code ItemHandlerWrapper} is needed (see
 * {@link WorkbenchCrafting}'s own javadoc).
 */
public abstract class TileAutoWorkbenchBase extends TileBC implements IHasWork, IMjRedstoneReceiver, IAutoCraft, MenuProvider {

    /** A redstone engine generates {@code 1 * MjAPI.MJ} per tick. This makes it a lot slower without one
     * powering it. */
    private static final long POWER_GEN_PASSIVE = MjAPI.MJ / 5;

    /** It takes 10 seconds to craft an item. Public so the container's progress-bar math
     * ({@code buildcraft.factory.container.ContainerAutoCraftItems#getProgress}) can divide by it without a
     * round trip through the tile instance. */
    public static final long POWER_REQUIRED = POWER_GEN_PASSIVE * 20 * 10;

    private static final long POWER_LOST = POWER_GEN_PASSIVE * 10;

    private static final ResourceLocation ADVANCEMENT_AUTOCRAFT = new ResourceLocation("buildcraftfactory", "lazy_crafting");

    public final ItemHandlerManager itemManager = new ItemHandlerManager(this::onSlotChange);
    public final ItemHandlerSimple invBlueprint;
    public final ItemHandlerSimple invMaterialFilter;
    public final ItemHandlerFiltered invMaterials;
    public final ItemHandlerSimple invResult;
    private final WorkbenchCrafting crafting;

    private final LazyOptional<IMjReceiver> receiverCap = LazyOptional.of(() -> (IMjReceiver) this);
    private final LazyOptional<IHasWork> hasWorkCap = LazyOptional.of(() -> (IHasWork) this);

    /** The amount of power stored until crafting can begin. When this reaches {@link #POWER_REQUIRED} the current
     * recipe is crafted. */
    private long powerStored;

    @Nullable
    private UUID owner;

    protected TileAutoWorkbenchBase(BlockEntityType<?> type, BlockPos pos, BlockState state, int width, int height) {
        super(type, pos, state);
        int slots = width * height;
        invBlueprint = itemManager.addInvHandler("blueprint", slots, EnumAccess.PHANTOM);
        invMaterialFilter = itemManager.addInvHandler("material_filter", slots, EnumAccess.PHANTOM);
        invMaterials = new ItemHandlerFiltered(invMaterialFilter, true);
        invMaterials.setCallback(this::onSlotChange);
        itemManager.addInvHandler("materials", invMaterials, EnumAccess.INSERT, EnumPipePart.VALUES);
        invResult = itemManager.addInvHandler("result", 1, EnumAccess.EXTRACT, EnumPipePart.VALUES);
        crafting = new WorkbenchCrafting(width, height, this, invBlueprint, invMaterials, invResult);
    }

    public void onPlacedBy(@Nullable LivingEntity placer) {
        owner = placer == null ? null : placer.getUUID();
    }

    private void onSlotChange(IItemHandlerModifiable handler, int slot, @NotNull ItemStack before, @NotNull ItemStack after) {
        markDirtyAndSync();
        if (StackUtil.canMerge(before, after) && before.getCount() == after.getCount()) {
            return;
        }
        if (handler == invBlueprint) {
            crafting.onBlueprintChange();
        } else if (handler == invMaterials) {
            crafting.onMaterialsChange();
        }
    }

    /** Driven by the owning block's {@code getTicker}; was {@code TileAutoWorkbenchBase#update()}. */
    public void serverTick() {
        boolean didChange = crafting.tick();
        if (crafting.canCraft()) {
            if (powerStored >= POWER_REQUIRED) {
                if (crafting.craft()) {
                    // Used for #hasWork(), so it doesn't return false for the one tick in between crafts.
                    powerStored = crafting.canCraft() ? 1 : 0;
                    if (owner != null) {
                        AdvancementUtil.unlockAdvancement(owner, ADVANCEMENT_AUTOCRAFT);
                    }
                }
            } else {
                powerStored += POWER_GEN_PASSIVE;
            }
        } else if (powerStored >= POWER_LOST) {
            powerStored -= POWER_LOST;
        } else {
            powerStored = 0;
        }
        if (didChange) {
            createFilters();
            markDirtyAndSync();
        }
    }

    @Override
    public boolean hasWork() {
        return powerStored > 0;
    }

    /** @return The current craft progress, from 0 (nothing stored) to 1 (ready to craft). No partial-tick
     *         interpolation is done -- see the 26.x copy of this class's own javadoc for why 1.12.2's
     *         {@code powerStoredLast} has no replacement. */
    public double getProgress() {
        return (double) powerStored / POWER_REQUIRED;
    }

    /** @return The raw stored power, truncated to {@code int} -- always fits, since {@link #POWER_REQUIRED} is
     *          nowhere near {@link Integer#MAX_VALUE}. Read by the container's synced {@code DataSlot} on the
     *          server, and written back into this same field by that slot's {@code set(int)} on the client, the
     *          same "block entity doubles as the data source" idiom vanilla's own furnace uses. */
    public int getPowerStoredForSync() {
        return (int) powerStored;
    }

    public void setPowerStoredForSync(int value) {
        this.powerStored = value;
    }

    private void createFilters() {
        int slotCount = invBlueprint.getSlots();
        if (crafting.getAssumedResult().isEmpty()) {
            clearFilters();
            return;
        }
        NonNullList<ItemStack> uniqueStacks = NonNullList.create();
        int[] requirements = new int[slotCount];
        for (int s = 0; s < slotCount; s++) {
            ItemStack bptStack = invBlueprint.getStackInSlot(s);
            if (!bptStack.isEmpty()) {
                boolean foundMatch = false;
                for (int i = 0; i < uniqueStacks.size(); i++) {
                    if (StackUtil.canMerge(bptStack, uniqueStacks.get(i))) {
                        foundMatch = true;
                        requirements[i]++;
                        break;
                    }
                }
                if (!foundMatch) {
                    requirements[uniqueStacks.size()] = 1;
                    uniqueStacks.add(bptStack);
                }
            }
        }
        int uniqueSlotCount = uniqueStacks.size();
        if (uniqueSlotCount == 0) {
            clearFilters();
            return;
        }
        int[] slotAllocationCount = new int[uniqueSlotCount];
        Arrays.fill(slotAllocationCount, 1);
        int slotsLeft = slotCount - uniqueSlotCount;
        for (int i = 0; i < slotsLeft; i++) {
            int smallestDifference = Integer.MAX_VALUE;
            int smallestDifferenceIndex = 0;

            for (int s = 0; s < uniqueSlotCount; s++) {
                ItemStack stack = uniqueStacks.get(s);
                int uniqueCountTotal = stack.getMaxStackSize() * slotAllocationCount[s];

                int difference = uniqueCountTotal / requirements[s];
                if (difference < smallestDifference) {
                    smallestDifference = difference;
                    smallestDifferenceIndex = s;
                }
            }
            slotAllocationCount[smallestDifferenceIndex]++;
        }

        int realIndex = 0;
        for (int s = 0; s < uniqueSlotCount; s++) {
            ItemStack stack = uniqueStacks.get(s).copy();
            stack.setCount(1);
            for (int i = 0; i < slotAllocationCount[s]; i++) {
                invMaterialFilter.setStackInSlot(realIndex, stack);
                realIndex++;
            }
        }
        if (realIndex != slotCount) {
            throw new IllegalStateException("Somehow the balanced formula wasn't perfectly balanced!");
        }
    }

    private void clearFilters() {
        for (int s = 0; s < invMaterialFilter.getSlots(); s++) {
            invMaterialFilter.setStackInSlot(s, ItemStack.EMPTY);
        }
    }

    // IMjRedstoneReceiver

    @Override
    public boolean canConnect(@NotNull IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        return POWER_REQUIRED - powerStored;
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        long req = getPowerRequested();
        long taken = Math.min(req, microJoules);
        if (!simulate) {
            powerStored += taken;
        }
        return microJoules - taken;
    }

    // IAutoCraft

    @Override
    public ItemStack getCurrentRecipeOutput() {
        return crafting.getAssumedResult();
    }

    @Override
    public ItemHandlerSimple getInvBlueprint() {
        return invBlueprint;
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerAutoCraftItems(windowId, playerInv, this);
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == MjCapabilities.RECEIVER) {
            return receiverCap.cast();
        }
        if (cap == TilesAPI.HAS_WORK) {
            return hasWorkCap.cast();
        }
        LazyOptional<T> itemCap = itemManager.getCapability(cap, side);
        if (itemCap.isPresent()) {
            return itemCap;
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        receiverCap.invalidate();
        hasWorkCap.invalidate();
    }

    // TileBC

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemManager.deserializeNBT(nbt.getCompound("inv_manager"));
        powerStored = nbt.getLong("powerStored");
        owner = nbt.hasUUID("owner") ? nbt.getUUID("owner") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("inv_manager", itemManager.serializeNBT());
        nbt.putLong("powerStored", powerStored);
        if (owner != null) {
            nbt.putUUID("owner", owner);
        }
    }
}
