/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.tile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.recipes.AssemblyRecipe;

import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.recipe.AssemblyRecipeRegistry;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.silicon.EnumAssemblyRecipeState;
import buildcraft.silicon.container.ContainerAssemblyTable;

import buildcraft.BCSiliconRegistries;

/**
 * Ported from 1.12.2's {@code TileAssemblyTable}: rather than one recipe at a time, this scans every
 * {@link AssemblyRecipe} against the grid every tick, remembers which outputs are currently possible, lets the
 * player pick which ones to "save" (persist even once the inputs run out) via right-clicking their icon in the
 * GUI, and feeds MJ into whichever saved-and-affordable recipe is first in iteration order.
 *
 * <p><b>Recipe-state sync</b> uses this port's established "full NBT on change" idiom ({@code TileDistiller}'s own
 * precedent) rather than 1.12.2's id-tagged {@code NET_GUI_DATA} payload: {@link #recipesStates} is itself part of
 * the saved/synced state (see {@link #saveAdditional}/{@link #loadAdditional}), and every place 1.12.2 called
 * {@code sendNetworkGuiUpdate(NET_GUI_DATA)} this calls {@link #markDirtyAndSync()} instead -- so the GUI, which
 * reads {@link #recipesStates} directly off the tile, works identically whether that tile is the server's or the
 * client's synced copy.
 *
 * <p><b>The "click an icon to save/un-save it" interaction</b> is now vanilla's own container-button mechanism
 * ({@link AbstractContainerMenu#clickMenuButton}, sent by {@code MultiPlayerGameMode#handleInventoryButtonClick})
 * rather than a hand-rolled packet -- see {@link ContainerAssemblyTable#clickMenuButton}.
 */
public class TileAssemblyTable extends TileLaserTableBase implements MenuProvider {
    private static final Identifier ADVANCEMENT = Identifier.fromNamespaceAndPath("buildcraft", "precision_crafting");

    public final ItemHandlerSimple inv;
    public SortedMap<AssemblyInstruction, EnumAssemblyRecipeState> recipesStates = new TreeMap<>();

    @Nullable
    private UUID owner;

    public TileAssemblyTable(BlockPos pos, BlockState state) {
        super(BCSiliconRegistries.ASSEMBLY_TABLE_TYPE.get(), pos, state);
        inv = itemManager.addInvHandler("inv", 3 * 4, EnumAccess.BOTH, EnumPipePart.VALUES);
    }

    public void onPlacedBy(@Nullable LivingEntity placer) {
        owner = placer == null ? null : placer.getUUID();
    }

    private NonNullList<ItemStack> gridStacks() {
        NonNullList<ItemStack> stacks = NonNullList.withSize(inv.size(), ItemStack.EMPTY);
        for (int i = 0; i < inv.size(); i++) {
            stacks.set(i, inv.getStackInSlot(i));
        }
        return stacks;
    }

    private boolean updateRecipes() {
        // TODO: rework this to not iterate over every recipe every tick.
        boolean changed = false;
        NonNullList<ItemStack> grid = gridStacks();
        for (AssemblyRecipe recipe : AssemblyRecipeRegistry.REGISTRY.values()) {
            Set<ItemStack> outputs = recipe.getOutputs(grid);
            for (ItemStack out : outputs) {
                AssemblyInstruction instruction = new AssemblyInstruction(recipe, out);
                if (!recipesStates.containsKey(instruction)) {
                    recipesStates.put(instruction, EnumAssemblyRecipeState.POSSIBLE);
                    changed = true;
                }
            }
        }

        boolean findActive = false;
        for (Iterator<Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState>> iterator =
            recipesStates.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState> entry = iterator.next();
            AssemblyInstruction instruction = entry.getKey();
            EnumAssemblyRecipeState state = entry.getValue();
            boolean enough = extract(inv, instruction.recipe.getInputsFor(instruction.output), true, false);
            if (state == EnumAssemblyRecipeState.POSSIBLE) {
                if (!enough) {
                    iterator.remove();
                    changed = true;
                }
            } else {
                if (enough) {
                    if (state == EnumAssemblyRecipeState.SAVED) {
                        state = EnumAssemblyRecipeState.SAVED_ENOUGH;
                        changed = true;
                    }
                } else if (state != EnumAssemblyRecipeState.SAVED) {
                    state = EnumAssemblyRecipeState.SAVED;
                    changed = true;
                }
            }
            if (state == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) {
                findActive = true;
            }
            entry.setValue(state);
        }
        if (!findActive) {
            for (Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState> entry : recipesStates.entrySet()) {
                if (entry.getValue() == EnumAssemblyRecipeState.SAVED_ENOUGH) {
                    entry.setValue(EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE);
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    @Nullable
    private AssemblyInstruction getActiveRecipe() {
        return recipesStates.entrySet().stream()
            .filter(entry -> entry.getValue() == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private void activateNextRecipe() {
        AssemblyInstruction activeRecipe = getActiveRecipe();
        if (activeRecipe == null) {
            return;
        }
        int index = 0;
        int activeIndex = 0;
        boolean isActiveLast = false;
        long enoughCount = recipesStates.values().stream()
            .filter(state -> state == EnumAssemblyRecipeState.SAVED_ENOUGH
                || state == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE)
            .count();
        if (enoughCount <= 1) {
            return;
        }
        for (Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState> entry : recipesStates.entrySet()) {
            EnumAssemblyRecipeState state = entry.getValue();
            if (state == EnumAssemblyRecipeState.SAVED_ENOUGH) {
                isActiveLast = false;
            }
            if (state == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) {
                entry.setValue(EnumAssemblyRecipeState.SAVED_ENOUGH);
                activeIndex = index;
                isActiveLast = true;
            }
            index++;
        }
        index = 0;
        for (Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState> entry : recipesStates.entrySet()) {
            AssemblyRecipe recipe = entry.getKey().recipe;
            EnumAssemblyRecipeState state = entry.getValue();
            if (state == EnumAssemblyRecipeState.SAVED_ENOUGH && recipe != activeRecipe.recipe
                && (index > activeIndex || isActiveLast)) {
                entry.setValue(EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE);
                break;
            }
            index++;
        }
    }

    @Override
    public long getTarget() {
        return Optional.ofNullable(getActiveRecipe())
            .map(instruction -> instruction.recipe.getRequiredMicroJoulesFor(instruction.output))
            .orElse(0L);
    }

    /** Toggles a recipe's saved state -- called by {@link ContainerAssemblyTable#clickMenuButton}, the modern
     * replacement for 1.12.2's {@code sendRecipeStateToServer} packet. */
    public void toggleRecipe(int index) {
        if (index < 0 || index >= recipesStates.size()) {
            return;
        }
        Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState> entry =
            new ArrayList<>(recipesStates.entrySet()).get(index);
        EnumAssemblyRecipeState current = entry.getValue();
        entry.setValue(current == EnumAssemblyRecipeState.POSSIBLE
            ? EnumAssemblyRecipeState.SAVED
            : EnumAssemblyRecipeState.POSSIBLE);
        markDirtyAndSync();
    }

    @Override
    public void serverTick() {
        super.serverTick();

        boolean changed = updateRecipes();

        if (getTarget() > 0) {
            if (owner != null) {
                AdvancementUtil.unlockAdvancement(owner, ADVANCEMENT);
            }
            if (power >= getTarget()) {
                AssemblyInstruction instruction = getActiveRecipe();
                extract(inv, instruction.recipe.getInputsFor(instruction.output), false, false);
                InventoryUtil.addToBestAcceptor(getLevel(), getBlockPos(), null, instruction.output.copy());
                power -= getTarget();
                activateNextRecipe();
                changed = true;
            }
        }
        if (changed) {
            markDirtyAndSync();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.ValueOutputList list = output.childrenList("recipes_states");
        recipesStates.forEach((instruction, state) -> {
            ValueOutput entry = list.addChild();
            entry.store("recipe", Identifier.CODEC, instruction.recipe.getName());
            entry.store("output", ItemStack.CODEC, instruction.output);
            entry.putInt("state", state.ordinal());
        });
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        recipesStates.clear();
        for (ValueInput entry : input.childrenListOrEmpty("recipes_states")) {
            Optional<Identifier> name = entry.read("recipe", Identifier.CODEC);
            Optional<ItemStack> out = entry.read("output", ItemStack.CODEC);
            if (name.isPresent() && out.isPresent()) {
                AssemblyInstruction instruction = lookupRecipe(name.get(), out.get());
                if (instruction != null) {
                    recipesStates.put(instruction, EnumAssemblyRecipeState.values()[entry.getIntOr("state", 0)]);
                }
            }
        }
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Nullable
    private AssemblyInstruction lookupRecipe(Identifier name, ItemStack output) {
        AssemblyRecipe recipe = AssemblyRecipeRegistry.REGISTRY.get(name);
        return recipe != null ? new AssemblyInstruction(recipe, output) : null;
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerAssemblyTable(BCSiliconRegistries.ASSEMBLY_TABLE_MENU.get(), windowId, playerInv, this);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }

    public class AssemblyInstruction implements Comparable<AssemblyInstruction> {
        public final AssemblyRecipe recipe;
        public final ItemStack output;

        private AssemblyInstruction(AssemblyRecipe recipe, ItemStack output) {
            this.recipe = recipe;
            this.output = output;
        }

        @Override
        public int compareTo(AssemblyInstruction o) {
            return recipe.compareTo(o.recipe) + output.toString().compareTo(o.output.toString());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AssemblyInstruction other)) {
                return false;
            }
            return recipe.getName().equals(other.recipe.getName()) && ItemStack.matches(output, other.output);
        }

        @Override
        public int hashCode() {
            return recipe.getName().hashCode();
        }
    }
}
