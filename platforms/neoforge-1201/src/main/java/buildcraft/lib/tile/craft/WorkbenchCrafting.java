/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile.craft;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import buildcraft.api.inventory.IItemTransactor;

import buildcraft.lib.inventory.filter.ArrayStackFilter;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Matches a 3x3 (or {@code width x height}) blueprint pattern against the live vanilla crafting-recipe registry,
 * and, once enough materials are available, actually crafts one item -- the real logic behind the auto-workbench.
 * Mirrors the 26.x class of the same name; see that one's javadoc for the full account of what 1.12.2's original
 * (including its dead {@code INGREDIENTS} matching path, not ported here either) needed to become on a modern
 * target, and why only the exact-stack matching path is live.
 *
 * <p><b>Genuine platform divergence from the 26.x copy of this file, confirmed via {@code javap} against this
 * target's real merged jar.</b> {@code Recipe<C extends Container>} is still generic over a live container type
 * here, not a plain data holder -- {@link CraftingContainer} (extending {@code Container}) is exactly the shape
 * 1.12.2's own {@code InventoryCrafting} was, and {@code Recipe#matches(C, Level)}/{@code Recipe#assemble(C,
 * RegistryAccess)} (note the extra {@code RegistryAccess} parameter 26.x's {@code assemble} doesn't have) both
 * take {@code this} directly, exactly as 1.12.2's original did. So, unlike the 26.x copy, this class <em>does</em>
 * implement {@link CraftingContainer} itself, backed by a small internal {@code ItemStack[]} standing in for
 * 1.12.2's inherited {@code InventoryCrafting} storage -- {@link #getItem} reads {@link #invBlueprint} directly
 * while {@link #isBlueprintDirty}, exactly mirroring the original's {@code getStackInSlot} override, and falls
 * back to that internal array (the "temporary crafting grid") the rest of the time. {@link RecipeManager
 * #getRecipeFor} also returns a plain {@code Optional<T>} here, one layer shallower than 26.x's
 * {@code Optional<RecipeHolder<T>>}.
 *
 * <p>{@code buildcraft.lib.misc.CraftingUtil} (1.12.2's {@code GameRegistry.findRegistry(IRecipe.class)} recipe
 * scan) has no port: {@link Level#getRecipeManager()}'s own {@code getRecipeFor} already does the lookup
 * directly, so the whole class collapses into the one call in {@link #tick()} below.
 *
 * <p>Materials move through {@link IItemTransactor} -- on this target {@link ItemHandlerSimple} already
 * implements it directly (via {@code AbstractInvItemTransactor}), so, unlike 26.x, no wrapper is needed; the tile
 * passes {@link #invMaterials}/{@code invResult} straight through.
 */
public class WorkbenchCrafting implements CraftingContainer {
    private final int width;
    private final int height;
    private final TileBC tile;
    private final ItemHandlerSimple invBlueprint;
    private final IItemTransactor materials;
    private final IItemTransactor result;

    /** The temporary crafting grid -- filled in by {@link #craftExact} while pulling materials, read back by
     * {@link CraftingRecipe#matches}/{@code #assemble}/{@code #getRemainingItems}, and always fully drained again
     * before that method returns. Mirrors what 1.12.2's inherited {@code InventoryCrafting} storage was. */
    private final ItemStack[] gridStacks;

    private boolean isBlueprintDirty = true;
    private boolean areMaterialsDirty = true;
    private boolean cachedHasRequirements = false;

    @Nullable
    private CraftingRecipe currentRecipe;
    private ItemStack assumedResult = ItemStack.EMPTY;

    public WorkbenchCrafting(int width, int height, TileBC tile, ItemHandlerSimple invBlueprint,
        IItemTransactor materials, IItemTransactor result) {
        this.width = width;
        this.height = height;
        this.tile = tile;
        this.invBlueprint = invBlueprint;
        if (invBlueprint.getSlots() < width * height) {
            throw new IllegalArgumentException("Passed blueprint has a smaller size than width * height! ( expected "
                + (width * height) + ", got " + invBlueprint.getSlots() + ")");
        }
        this.materials = materials;
        this.result = result;
        this.gridStacks = new ItemStack[width * height];
        for (int i = 0; i < gridStacks.length; i++) {
            gridStacks[i] = ItemStack.EMPTY;
        }
    }

    public ItemStack getAssumedResult() {
        return assumedResult;
    }

    public void onBlueprintChange() {
        isBlueprintDirty = true;
    }

    public void onMaterialsChange() {
        areMaterialsDirty = true;
    }

    /** @return True if anything changed, false otherwise */
    public boolean tick() {
        ServerLevel level = (ServerLevel) tile.getLevel();
        if (level == null) {
            throw new IllegalStateException("Never call this before the tile has a level!");
        }
        if (!isBlueprintDirty) {
            return false;
        }
        // Read live from invBlueprint (see #getItem) while matching.
        Optional<CraftingRecipe> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, this, level);
        if (found.isEmpty()) {
            currentRecipe = null;
            assumedResult = ItemStack.EMPTY;
        } else {
            currentRecipe = found.get();
            assumedResult = currentRecipe.assemble(this, level.registryAccess());
        }
        // From here on, getItem()/setItem() operate on the internal grid, not the live blueprint.
        isBlueprintDirty = false;
        return true;
    }

    /** @return True if {@link #craft()} might return true, or false if {@link #craft()} will definitely return
     *         false. */
    public boolean canCraft() {
        if (currentRecipe == null || isBlueprintDirty) {
            return false;
        }
        if (!result.canFullyAccept(assumedResult)) {
            return false;
        }
        if (areMaterialsDirty) {
            areMaterialsDirty = false;
            cachedHasRequirements = hasExactStacks();
        }
        return cachedHasRequirements;
    }

    /** Attempts to craft a single item. Assumes {@link #canCraft()} was called in the same tick, with no changes
     * happening in between.
     *
     * @return True if the crafting happened, false otherwise. */
    public boolean craft() {
        if (isBlueprintDirty) {
            return false;
        }
        return craftExact((ServerLevel) tile.getLevel());
    }

    private boolean hasExactStacks() {
        Map<ItemStackKey, Integer> required = new HashMap<>();
        for (int s = 0; s < width * height; s++) {
            ItemStack req = invBlueprint.getStackInSlot(s);
            if (!req.isEmpty()) {
                required.merge(new ItemStackKey(req), 1, Integer::sum);
            }
        }
        for (Map.Entry<ItemStackKey, Integer> entry : required.entrySet()) {
            int count = entry.getValue();
            ArrayStackFilter filter = new ArrayStackFilter(entry.getKey().baseStack);
            ItemStack found = materials.extract(filter, count, count, true);
            if (found.isEmpty() || found.getCount() != count) {
                return false;
            }
        }
        return true;
    }

    /** Implementation of {@link #craft()}, assuming nothing about the current recipe beyond what {@link #canCraft()}
     * already checked. */
    private boolean craftExact(ServerLevel level) {
        // Step 1: make sure the temp grid starts empty (should already be, but matches 1.12.2's own defensive
        // clear at the start of craftExact).
        if (!clearInventoryReturningToMaterials(level)) {
            return false;
        }

        // Step 2: pull exactly one of each blueprint item from materials into the temp grid.
        for (int s = 0; s < width * height; s++) {
            ItemStack bpt = invBlueprint.getStackInSlot(s);
            if (!bpt.isEmpty()) {
                ItemStack stack = materials.extract(new ArrayStackFilter(bpt), 1, 1, false);
                if (stack.isEmpty()) {
                    clearInventoryReturningToMaterials(level);
                    return false;
                }
                gridStacks[s] = stack;
            }
        }

        // Step 3: match and assemble. Some recipes (vanilla fireworks, for instance) need matches() called before
        // assemble(), since they cache what matches() found for assemble() to read back.
        if (!currentRecipe.matches(this, level)) {
            clearInventoryReturningToMaterials(level);
            return false;
        }
        ItemStack craftedResult = currentRecipe.assemble(this, level.registryAccess());
        if (craftedResult.isEmpty()) {
            clearInventoryReturningToMaterials(level);
            return false;
        }
        ItemStack leftover = result.insert(craftedResult, false, false);
        if (!leftover.isEmpty()) {
            InventoryUtil.addToBestAcceptor(level, tile.getBlockPos(), null, leftover);
        }
        NonNullList<ItemStack> remainingStacks = currentRecipe.getRemainingItems(this);
        for (int s = 0; s < width * height; s++) {
            ItemStack inSlot = getItem(s);
            ItemStack remaining = remainingStacks.get(s);

            if (!inSlot.isEmpty()) {
                removeItem(s, 1);
                inSlot = getItem(s);
            }
            if (!remaining.isEmpty()) {
                if (inSlot.isEmpty()) {
                    setItem(s, remaining);
                } else if (StackUtil.canMerge(inSlot, remaining)) {
                    remaining.grow(inSlot.getCount());
                    setItem(s, remaining);
                } else {
                    ItemStack over = materials.insert(remaining, false, false);
                    if (!over.isEmpty()) {
                        InventoryUtil.addToBestAcceptor(level, tile.getBlockPos(), null, over);
                    }
                }
            }
        }

        // Step 4: whatever is left in the temp grid (an unconsumed remaining item, typically) goes back to
        // materials.
        clearInventoryReturningToMaterials(level);
        return true;
    }

    /** @return True if the temp grid is now clear, false if something couldn't be returned to materials (matches
     *          1.12.2's {@code clearInventory} boolean contract). */
    private boolean clearInventoryReturningToMaterials(ServerLevel level) {
        for (int s = 0; s < width * height; s++) {
            ItemStack inSlot = removeItemNoUpdate(s);
            if (!inSlot.isEmpty()) {
                ItemStack over = materials.insert(inSlot, false, false);
                if (!over.isEmpty()) {
                    InventoryUtil.addToBestAcceptor(level, tile.getBlockPos(), null, over);
                }
            }
        }
        return true;
    }

    // CraftingContainer / Container

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public java.util.List<ItemStack> getItems() {
        return java.util.Arrays.asList(gridStacks);
    }

    @Override
    public void fillStackedContents(StackedContents contents) {
        for (ItemStack stack : gridStacks) {
            contents.accountStack(stack);
        }
    }

    @Override
    public int getContainerSize() {
        return gridStacks.length;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : gridStacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return isBlueprintDirty ? invBlueprint.getStackInSlot(index) : gridStacks[index];
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack current = gridStacks[index];
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = current.split(count);
        if (current.isEmpty()) {
            gridStacks[index] = ItemStack.EMPTY;
        }
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack current = gridStacks[index];
        gridStacks[index] = ItemStack.EMPTY;
        return current;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        gridStacks[index] = stack;
    }

    @Override
    public void setChanged() {
        // No-op: nothing external needs to know the temp grid changed.
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < gridStacks.length; i++) {
            gridStacks[i] = ItemStack.EMPTY;
        }
    }
}
