/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.recipe;

import com.google.common.collect.ImmutableSet;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.AssemblyRecipeBasic;
import buildcraft.api.recipes.IngredientStack;

import buildcraft.lib.recipe.AssemblyRecipeRegistry;

import buildcraft.transport.item.ItemPluggableLens;

import buildcraft.BCCoreRegistries;
import buildcraft.BCTransportRegistries;

/**
 * Port of the non-gate half of 1.12.2's {@code buildcraft.silicon.BCSiliconRecipes#registerRecipes}: the
 * assembly-table recipes for the pulsar/timer/light-sensor/lens pipe accessories, all of which now live under
 * {@code buildcraft.transport} rather than {@code buildcraft.silicon} -- see {@code BCTransportRegistries}' own
 * javadoc for why the whole gate/pluggable family landed there instead. {@link FacadeAssemblyRecipes} is the
 * assembly table's other, pre-existing recipe (already wired in from {@code BCTransportRegistries}) and is not
 * duplicated here.
 *
 * <p><b>Scope cut, this batch:</b> every gate-related recipe in 1.12.2's original method -- the plain
 * crafting-table base-gate recipes, the AND&lt;-&gt;OR shapeless swap, and every assembly-table gate/
 * gate-modifier/redstone-chipset/gate-copier recipe -- is left unported. All of them need one or both of:
 * (1) an {@code EnumRedstoneChipset}-equivalent item, which does not exist anywhere in this port yet (confirmed:
 * no chipset item, no chipset enum at all, not merely an unregistered one), or (2) an exact-NBT/
 * {@link buildcraft.transport.gate.GateVariant}-matching {@code Ingredient} (1.12.2's own {@code IngredientNBTBC}),
 * which also does not exist here and would need new custom-{@code Ingredient} infrastructure to add faithfully
 * rather than a same-shape port (a plain {@code Ingredient.of(gateItem)} would match every gate variant, not just
 * the one the recipe upgrades from). Both are real, addressable gaps -- just not one-line ones -- left for
 * whichever batch ports redstone chipsets.
 */
public final class PluggableAssemblyRecipes {
    private PluggableAssemblyRecipes() {}

    public static void register() {
        registerPulsar();
        registerLightSensor();
        registerTimer();
        registerLenses();
    }

    /** 1.12.2 preferred a wood engine as the "redstone engine" ingredient and only fell back to a plain redstone
     * block if {@code BCCoreBlocks.engine} was null; both the wood engine and the pulsar exist on this port, so
     * this is the unconditional real recipe rather than the original's defensive null check (which guarded a
     * multi-jar build where {@code buildcraftcore} could be absent -- not a configuration this single-mod-id port
     * has). */
    private static void registerPulsar() {
        ItemStack output = new ItemStack(BCTransportRegistries.PLUG_PULSAR.get());
        ItemStack redstoneEngine = new ItemStack(BCCoreRegistries.ENGINE_WOOD.get());

        ImmutableSet<IngredientStack> input = ImmutableSet.of(
            new IngredientStack(Ingredient.of(redstoneEngine.getItem())),
            new IngredientStack(Ingredient.of(Items.IRON_INGOT), 2)
        );
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("plug_pulsar", 1000 * MjAPI.MJ, input, output));
    }

    private static void registerLightSensor() {
        ItemStack output = new ItemStack(BCTransportRegistries.PLUG_LIGHT_SENSOR.get());
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("light-sensor", 500 * MjAPI.MJ,
            ImmutableSet.of(new IngredientStack(Ingredient.of(Blocks.DAYLIGHT_DETECTOR))), output));
    }

    private static void registerTimer() {
        ItemStack output = new ItemStack(BCTransportRegistries.PLUG_TIMER.get());
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("timer", 500 * MjAPI.MJ,
            ImmutableSet.of(new IngredientStack(Ingredient.of(Items.CLOCK))), output));
    }

    /** {@code EnumDyeColor}'s 16 values plus the two "clear glass" (no dye) variants -- matches 1.12.2's own
     * per-colour loop plus its two trailing colourless recipes exactly. */
    private static void registerLenses() {
        for (DyeColor colour : DyeColor.values()) {
            IngredientStack stainedGlass = new IngredientStack(Ingredient.of(Blocks.STAINED_GLASS.pick(colour)));

            AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("lens-regular-" + colour.getName(),
                500 * MjAPI.MJ, ImmutableSet.of(stainedGlass), ItemPluggableLens.getStack(colour, false)));

            AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("lens-filter-" + colour.getName(),
                500 * MjAPI.MJ,
                ImmutableSet.of(stainedGlass, new IngredientStack(Ingredient.of(Blocks.IRON_BARS))),
                ItemPluggableLens.getStack(colour, true)));
        }

        IngredientStack glass = new IngredientStack(Ingredient.of(Blocks.GLASS));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("lens-regular", 500 * MjAPI.MJ,
            ImmutableSet.of(glass), ItemPluggableLens.getStack(null, false)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("lens-filter", 500 * MjAPI.MJ,
            ImmutableSet.of(glass, new IngredientStack(Ingredient.of(Blocks.IRON_BARS))),
            ItemPluggableLens.getStack(null, true)));
    }
}
