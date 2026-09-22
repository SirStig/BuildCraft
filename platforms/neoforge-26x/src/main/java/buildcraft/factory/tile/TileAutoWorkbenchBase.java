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
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.tiles.IHasWork;

import buildcraft.lib.inventory.ItemHandlerWrapper;
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

import buildcraft.BCFactoryRegistries;

/**
 * A fixed vanilla-crafting-table recipe, configured by a player dragging items into a phantom 3x3 blueprint grid
 * (see {@code buildcraft.factory.container.ContainerAutoCraftItems}, this port's first real GUI/container), that
 * then automatically pulls matching materials from piped-in items and produces the crafted result over time,
 * powered by MJ. {@link WorkbenchCrafting} does the actual recipe matching/execution; this class owns the
 * inventories, the MJ accumulation, and the advancement grant.
 *
 * <p><b>The vanilla-recipe-book integration is dropped, not ported.</b> 1.12.2's {@code GuiAutoCraftItems} (301
 * lines) embedded {@code GuiRecipeBookPhantom}/{@code IRecipeShownListener} so a player could click a recipe in
 * the book to auto-fill the blueprint. That is real client-side UI plumbing with no bearing on this tile's own
 * logic, and a player can still fill the blueprint by hand without it -- dropped the same way this port already
 * drops other pure-convenience features (see e.g. the filter-overlay icons note on
 * {@code buildcraft.factory.container.ContainerAutoCraftItems}).
 *
 * <p><b>{@code createFilters()}</b> is a straight algorithmic port: given the blueprint's unique item stacks, it
 * balances the 9 material-filter slots across them so that no single filter slot artificially caps how much of a
 * common ingredient can be piped in at once (see the original's own inline comments, carried over below). Nothing
 * about this needed to change beyond {@code NonNullList}/{@code StackUtil.canMerge}, both already ported.
 *
 * <p><b>The MJ accumulation ({@link #POWER_GEN_PASSIVE}/{@link #POWER_REQUIRED}/{@link #POWER_LOST}) is a direct
 * port</b>, not routed through {@link buildcraft.api.mj.MjBattery} the way most other machines in this port are --
 * 1.12.2's original didn't use a battery here either (it implements {@link IMjRedstoneReceiver} directly, with its
 * own {@link #powerStored} field and accumulate/decay logic), and there is nothing about this machine's charge-up-
 * then-spend-it-all-at-once behaviour that a generic battery would simplify.
 *
 * <p><b>No custom network payload survives.</b> 1.12.2's {@code writePayload}/{@code readPayload} pushed
 * {@code powerStored} (for the progress bar) and the assumed result (for the display slot) by hand, id-tagged.
 * The former is now a single {@code DataSlot} the container adds (see
 * {@code buildcraft.factory.container.ContainerAutoCraftItems}); the latter needs nothing at all, since a
 * {@code SlotDisplay} whose {@code getItem()} reads {@link #getCurrentRecipeOutput()} live is already diffed and
 * pushed to the client by {@code AbstractContainerMenu#broadcastChanges()} every tick. {@link #resultClient} and
 * partial-tick progress interpolation (1.12.2's {@code powerStoredLast}) are both dropped with it -- see
 * {@link #getProgress()}.
 *
 * <p><b>Materials move through {@link buildcraft.api.inventory.IItemTransactor}, wrapped from
 * {@link #invMaterials}/{@link #invResult} via {@link ItemHandlerWrapper}</b> -- see {@link WorkbenchCrafting}'s
 * own javadoc for why {@link ItemHandlerSimple} itself isn't one on this target.
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

    private static final Identifier ADVANCEMENT_AUTOCRAFT =
        Identifier.fromNamespaceAndPath("buildcraftfactory", "lazy_crafting");

    public final ItemHandlerManager itemManager = new ItemHandlerManager(this::onSlotChange);
    public final ItemHandlerSimple invBlueprint;
    public final ItemHandlerSimple invMaterialFilter;
    public final ItemHandlerFiltered invMaterials;
    public final ItemHandlerSimple invResult;
    private final WorkbenchCrafting crafting;

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
        crafting = new WorkbenchCrafting(width, height, this, invBlueprint,
            new ItemHandlerWrapper(invMaterials), new ItemHandlerWrapper(invResult));
    }

    public void onPlacedBy(@Nullable LivingEntity placer) {
        owner = placer == null ? null : placer.getUUID();
    }

    private void onSlotChange(ItemHandlerSimple handler, int slot, @NotNull ItemStack before, @NotNull ItemStack after) {
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
     *         interpolation is done -- see this class's own javadoc for why 1.12.2's {@code powerStoredLast} has
     *         no replacement. */
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
        int slotCount = invBlueprint.size();
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
            ItemStack stack = uniqueStacks.get(s).copyWithCount(1);
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
        for (int s = 0; s < invMaterialFilter.size(); s++) {
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
        return new ContainerAutoCraftItems(BCFactoryRegistries.AUTO_WORKBENCH_ITEMS_MENU.get(), windowId, playerInv, this);
    }

    /** Called server-side (once, when the menu is first opened) so the client can look up this same tile again --
     * see {@code buildcraft.factory.container.ContainerAutoCraftItems}'s client-side factory constructor. */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }

    // TileBC

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        itemManager.deserialize(input);
        powerStored = input.getLongOr("powerStored", 0);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemManager.serialize(output);
        output.putLong("powerStored", powerStored);
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
        }
    }
}
