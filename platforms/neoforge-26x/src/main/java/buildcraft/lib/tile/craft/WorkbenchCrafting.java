/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile.craft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.inventory.IItemTransactor;

import buildcraft.lib.inventory.filter.ArrayStackFilter;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Matches a 3x3 (or {@code width x height}) blueprint pattern against the live vanilla crafting-recipe registry,
 * and, once enough materials are available, actually crafts one item -- the real logic behind the auto-workbench.
 *
 * <p>Only the <b>exact-stack</b> matching path is ported. 1.12.2's own {@code WorkbenchCrafting} had a second,
 * tag/ingredient-based path ({@code EnumRecipeType.INGREDIENTS}, {@code craftByIngredients}/{@code hasIngredients})
 * -- but both of those methods are commented-out dead code in the original, and {@code canCraft()}/{@code craft()}'s
 * own switch statements fall the {@code INGREDIENTS} case straight through into {@code EXACT_STACKS} regardless of
 * what {@link #recipeType} they compute. In other words: 1.12.2 already only ever ran the exact-stack path at
 * runtime, no matter which one {@link #tick()} decided the current recipe needed. This port reproduces exactly
 * that behaviour -- match by exact item + data components, not by recipe ingredient/tag -- rather than inventing
 * the half-written ingredient path back into existence.
 *
 * <p><b>Genuine platform divergence: the recipe-matching API itself, not just a rename.</b> 1.12.2's
 * {@code InventoryCrafting} was both the "temporary crafting grid" state <em>and</em> the object passed straight
 * into {@code IRecipe#matches}/{@code #getCraftingResult}. Confirmed via {@code javap} against this target's real
 * merged jar: {@code Recipe<T extends RecipeInput>} is generic over a plain immutable data holder, not a live
 * container -- {@link CraftingInput} is built via the static factory {@link CraftingInput#of(int, int, List)}, and
 * {@code Recipe#matches(T, Level)}/{@code Recipe#assemble(T)} both take that value directly (confirmed: 26.3's
 * {@code assemble} takes no {@code RegistryAccess} parameter at all, unlike the 1.20.1 copy of this file). So
 * there is no "inventory-shaped" class to extend here the way 1.12.2's {@code WorkbenchCrafting extends
 * InventoryCrafting} could -- this class is a plain object that snapshots {@link #invBlueprint} (while matching a
 * fresh recipe) or its own temporary crafting grid (while executing one) into a new {@link CraftingInput} each
 * time it needs one. {@link net.minecraft.world.item.crafting.RecipeManager#getRecipeFor} also returns
 * {@code Optional<RecipeHolder<T>>} here, one layer more than 1.20.1's plain {@code Optional<T>} -- the actual
 * recipe is {@code holder.value()}.
 *
 * <p>{@code buildcraft.lib.misc.CraftingUtil} (1.12.2's {@code GameRegistry.findRegistry(IRecipe.class)} recipe
 * scan) has no port: {@link net.minecraft.world.level.Level#recipeAccess()} /
 * {@link net.minecraft.server.level.ServerLevel#recipeAccess()}'s {@code getRecipeFor} already does the lookup
 * directly, so the whole class collapses into the one call in {@link #tick()} below.
 *
 * <p>Materials are moved through {@link IItemTransactor} rather than {@link ItemHandlerSimple} directly, wrapped
 * by the tile via {@code ItemHandlerWrapper} -- {@link ItemHandlerSimple} on this target is a plain
 * {@code ResourceHandler<ItemResource>}, not an {@link IItemTransactor} itself (see that class's own javadoc), so
 * every extract/insert here goes through the wrapper and a short-lived {@link Transaction}, matching
 * {@code buildcraft.factory.tile.TileChute}'s already-established precedent for the same platform gap.
 */
public class WorkbenchCrafting {
    private final int width;
    private final int height;
    private final TileBC tile;
    private final ItemHandlerSimple invBlueprint;
    private final IItemTransactor materials;
    private final IItemTransactor result;

    private boolean isBlueprintDirty = true;
    private boolean areMaterialsDirty = true;
    private boolean cachedHasRequirements = false;

    @Nullable
    private RecipeHolder<CraftingRecipe> currentRecipe;
    private ItemStack assumedResult = ItemStack.EMPTY;

    public WorkbenchCrafting(int width, int height, TileBC tile, ItemHandlerSimple invBlueprint,
        IItemTransactor materials, IItemTransactor result) {
        this.width = width;
        this.height = height;
        this.tile = tile;
        this.invBlueprint = invBlueprint;
        if (invBlueprint.size() < width * height) {
            throw new IllegalArgumentException("Passed blueprint has a smaller size than width * height! ( expected "
                + (width * height) + ", got " + invBlueprint.size() + ")");
        }
        this.materials = materials;
        this.result = result;
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
        CraftingInput input = snapshotBlueprint();
        Optional<RecipeHolder<CraftingRecipe>> found = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (found.isEmpty()) {
            currentRecipe = null;
            assumedResult = ItemStack.EMPTY;
        } else {
            currentRecipe = found.get();
            assumedResult = currentRecipe.value().assemble(input);
        }
        isBlueprintDirty = false;
        return true;
    }

    /** @return True if {@link #craft()} might return true, or false if {@link #craft()} will definitely return
     *         false. */
    public boolean canCraft() {
        if (currentRecipe == null || isBlueprintDirty) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (!result.canFullyAccept(ItemResource.of(assumedResult), assumedResult.getCount(), transaction)) {
                return false;
            }
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

    private CraftingInput snapshotBlueprint() {
        List<ItemStack> items = new ArrayList<>(width * height);
        for (int s = 0; s < width * height; s++) {
            items.add(invBlueprint.getStackInSlot(s));
        }
        return CraftingInput.of(width, height, items);
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
            try (Transaction transaction = Transaction.openRoot()) {
                ResourceStack<ItemResource> found = materials.extract(filter, count, count, transaction);
                if (found == null || found.amount() != count) {
                    return false;
                }
                // Never committed -- this is only a peek at what materials could give up.
            }
        }
        return true;
    }

    /** Implementation of {@link #craft()}, assuming nothing about the current recipe beyond what {@link #canCraft()}
     * already checked. */
    private boolean craftExact(ServerLevel level) {
        ItemStack[] grid = new ItemStack[width * height];
        for (int s = 0; s < width * height; s++) {
            ItemStack bpt = invBlueprint.getStackInSlot(s);
            if (bpt.isEmpty()) {
                grid[s] = ItemStack.EMPTY;
                continue;
            }
            ItemStack pulled = extractOne(bpt);
            if (pulled.isEmpty()) {
                for (int j = 0; j < s; j++) {
                    returnToMaterials(level, grid[j]);
                }
                return false;
            }
            grid[s] = pulled;
        }

        // CraftingInput.of trims its input down to the bounding box of non-empty stacks (confirmed via a real
        // ArrayIndexOutOfBoundsException crash the very first time this was exercised through RCON, not
        // guessed in advance -- see PORTING.md's progress entry for the full account): a recipe whose own
        // pattern is smaller than this tile's own width*height (a 1x2 sticks recipe inside a 3x3 blueprint, say)
        // gets a *trimmed* CraftingInput, not a width*height one. getRemainingItems(input) is therefore sized to
        // the trimmed grid, not width*height -- so this has to go through the Positioned form and map trimmed
        // coordinates back to original grid slots, rather than iterating 0..width*height directly the way
        // matches()/assemble() can (both only ever read through input.getItem(...), which is trim-relative and
        // safe either way).
        CraftingInput.Positioned positioned = CraftingInput.ofPositioned(width, height, List.of(grid));
        CraftingInput input = positioned.input();
        // Some recipes (vanilla fireworks, for instance) need matches() called before assemble(), since they
        // cache what matches() found for assemble() to read back.
        if (!currentRecipe.value().matches(input, level)) {
            returnAllToMaterials(level, grid);
            return false;
        }
        ItemStack craftedResult = currentRecipe.value().assemble(input);
        if (craftedResult.isEmpty()) {
            returnAllToMaterials(level, grid);
            return false;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = result.insert(ItemResource.of(craftedResult), craftedResult.getCount(), false, transaction);
            transaction.commit();
            if (inserted < craftedResult.getCount()) {
                ItemStack leftover = craftedResult.copyWithCount(craftedResult.getCount() - inserted);
                InventoryUtil.addToBestAcceptor(level, tile.getBlockPos(), null, leftover);
            }
        }

        NonNullList<ItemStack> remainingStacks = currentRecipe.value().getRemainingItems(input);
        int trimmedWidth = input.width();
        int trimmedHeight = input.height();
        for (int ty = 0; ty < trimmedHeight; ty++) {
            for (int tx = 0; tx < trimmedWidth; tx++) {
                ItemStack remaining = remainingStacks.get(tx + ty * trimmedWidth);
                // The item that was pulled into this grid slot is always fully consumed by a single-count
                // exact-stack craft, so nothing needs to be "decremented" here -- only a genuine remaining item
                // (an empty bucket, say) needs to go anywhere.
                if (!remaining.isEmpty()) {
                    returnToMaterials(level, remaining);
                }
            }
        }
        return true;
    }

    private ItemStack extractOne(ItemStack template) {
        ArrayStackFilter filter = new ArrayStackFilter(template);
        try (Transaction transaction = Transaction.openRoot()) {
            ResourceStack<ItemResource> found = materials.extract(filter, 1, 1, transaction);
            if (found == null || found.isEmpty()) {
                return ItemStack.EMPTY;
            }
            transaction.commit();
            return found.resource().toStack(found.amount());
        }
    }

    private void returnToMaterials(ServerLevel level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = materials.insert(ItemResource.of(stack), stack.getCount(), false, transaction);
            transaction.commit();
            if (inserted < stack.getCount()) {
                InventoryUtil.addToBestAcceptor(level, tile.getBlockPos(), null, stack.copyWithCount(stack.getCount() - inserted));
            }
        }
    }

    private void returnAllToMaterials(ServerLevel level, ItemStack[] grid) {
        for (ItemStack stack : grid) {
            returnToMaterials(level, stack);
        }
    }
}
