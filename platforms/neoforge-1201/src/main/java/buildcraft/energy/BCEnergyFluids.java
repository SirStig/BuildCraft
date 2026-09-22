/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import net.minecraftforge.common.SoundActions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import buildcraft.lib.fluid.BCFluidBlock;
import buildcraft.lib.fluid.BCFluidBucketItem;
import buildcraft.lib.fluid.BCFluidType;
import buildcraft.lib.registry.BCRegistry;

import buildcraft.BuildCraft;

/**
 * BuildCraft's oil-refining fluid family: crude oil, its three end products (gaseous, light and dense fuel) and
 * residue, and the five intermediate mixtures between them -- ten fluids, each in three heat variants (cool, hot,
 * searing), thirty in total. 1.12.2 only registered all thirty when {@code buildcraftfactory} was present (for the
 * distiller), and otherwise just heat-0 oil and light fuel; the port is one mod, so {@code BCModules.FACTORY
 * .isLoaded()} is constant {@code true} (see {@code BCModules}' own javadoc) and the full set is always registered.
 *
 * <p>Every number in {@link #preInit}'s table, and every derived property in {@link #defineFluid}, is 1.12.2's
 * own, unchanged: viscosity falls by a quarter per heat level, a fluid at or past its boil point gets a negated
 * density (i.e. becomes a gas), temperature is {@code 300 + 20 * heat}, and the flow distance is
 * {@code baseQuanta + (baseQuanta > 6 ? heat : heat / 2)}. What changed is where each lands:
 * <ul>
 * <li>density/viscosity/temperature/name go on the Forge {@link FluidType} ({@link BCFluidType});</li>
 * <li>flow distance and speed go on {@link ForgeFlowingFluid.Properties}, which models them differently -- see
 *     {@link #levelDecreasePerBlock} and {@link #tickRate};</li>
 * <li>flammability and map colour go on the {@link BCFluidBlock}'s properties.</li>
 * </ul>
 *
 * <p><b>Textures: 1.12.2's stitch-time recolour, baked ahead of time.</b> 1.12.2 shipped one greyscale animated
 * still/flow texture pair per <i>heat level</i> ({@code heat_N_still}/{@code heat_N_flow}), not per fluid, and
 * {@code BCEnergySprites}/{@code AtlasSpriteFluid} generated each fluid's sprite at texture-stitch time by mapping
 * every grey value {@code v} of every animation frame, per channel, to {@code (dark * (256 - v) + light * v) / 256}
 * (integer maths, alpha forced opaque) using the fluid's {@code tex_dark}/{@code tex_light} pair -- a gradient map,
 * which no multiplicative tint can reproduce (residue's "dark" end is lighter than its "light" end). Neither target
 * has a stitch-time sprite-replacement hook shaped like 1.12.2's {@code setTextureEntry}, and vanilla's own
 * {@code paletted_permutations} sprite source (the nearest data-driven equivalent) was ruled out by reading it: its
 * {@code PalettedSpriteSupplier} builds each output {@code SpriteContents} with a single whole-image
 * {@code FrameSize} and no animation metadata (both targets), which would turn the 16x512 animation strip into one
 * stretched static sprite. So the exact same formula was run once, offline, over the original
 * {@code buildcraft_resources} {@code heat_N_*} textures, and the sixty resulting
 * {@code textures/block/fluids/<fluid>_heat_<N>_{still,flow}.png} files ship with the original
 * {@code heat_N_*.png.mcmeta} animation files copied byte-for-byte beside them. The fluid tint is therefore plain
 * white, just as 1.12.2's {@code BCFluid#setColour(light, dark)} forced {@code colour} to white.
 */
public final class BCEnergyFluids {

    private BCEnergyFluids() {}

    /** 1.12.2's {@code BCEnergyConfig.enableOilBurn} ({@code worldgen.oil.can_burn}), at its default. This port has
     * no energy config yet; this is the value every 1.12.2 install got unless it opted out. */
    private static final boolean ENABLE_OIL_BURN = true;

    private static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, BuildCraft.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, BuildCraft.MOD_ID);

    public static BCFluid[] crudeOil;
    /** All 3 fuels (no residue) */
    public static BCFluid[] oilDistilled;
    /** The 3 heaviest components (fuelLight, fuelDense and oilResidue) */
    public static BCFluid[] oilHeavy;
    /** The 2 lightest fuels (no dense fuel) */
    public static BCFluid[] fuelMixedLight;
    /** The 2 heaviest fuels (no gaseous fuel) */
    public static BCFluid[] fuelMixedHeavy;
    /** The 2 heaviest products (fuelDense and oilResidue) */
    public static BCFluid[] oilDense;

    // End products in order from least to most dense
    public static BCFluid[] fuelGaseous;
    public static BCFluid[] fuelLight;
    public static BCFluid[] fuelDense;
    public static BCFluid[] oilResidue;

    private static final List<BCFluid> ALL = new ArrayList<>();

    /** Every fluid defined, in registration order (each base fluid's three heats together). */
    public static final List<BCFluid> allFluids = Collections.unmodifiableList(ALL);

    /** Called once, from {@code BCEnergyRegistries}' static initialiser, so the buckets land in the creative tab
     * right after the Stirling engine. {@code tar} is declared in 1.12.2 but never defined or registered there
     * either, so it is not here. */
    public static void preInit(BCRegistry registry) {
        int[][] data = { //@formatter:off
            // Tabular form of all the fluid values
            // density, viscosity, boil, spread,  tex_light,   tex_dark, sticky, flammable
            {      900,      2000,    3,      6, 0x50_50_50, 0x05_05_05,      1,         1 },// Crude Oil
            {     1200,      4000,    3,      4, 0x10_0F_10, 0x42_10_42,      1,         0 },// Residue
            {      850,      1800,    3,      6, 0xA0_8F_1F, 0x42_35_20,      1,         1 },// Heavy Oil
            {      950,      1600,    3,      5, 0x87_6E_77, 0x42_24_24,      1,         1 },// Dense Oil
            {      750,      1400,    2,      8, 0xE4_AF_78, 0xB4_7F_00,      0,         1 },// Distilled Oil
            {      600,       800,    2,      7, 0xFF_AF_3F, 0xE0_7F_00,      0,         1 },// Dense Fuel
            {      700,      1000,    2,      7, 0xF2_A7_00, 0xC4_87_00,      0,         1 },// Mixed Heavy Fuels
            {      400,       600,    1,      8, 0xFF_FF_30, 0xE4_CF_00,      0,         1 },// Light Fuel
            {      650,       900,    1,      9, 0xF6_D7_00, 0xC4_B7_00,      0,         1 },// Mixed Light Fuels
            {      300,       500,    0,     10, 0xFA_F6_30, 0xE0_D9_00,      0,         1 },// Gas Fuel
        };//@formatter:on
        int index = 0;

        // Add all of the fluid states
        crudeOil = defineFluids(registry, data[index++], "oil");
        oilResidue = defineFluids(registry, data[index++], "oil_residue");
        oilHeavy = defineFluids(registry, data[index++], "oil_heavy");
        oilDense = defineFluids(registry, data[index++], "oil_dense");
        oilDistilled = defineFluids(registry, data[index++], "oil_distilled");
        fuelDense = defineFluids(registry, data[index++], "fuel_dense");
        fuelMixedHeavy = defineFluids(registry, data[index++], "fuel_mixed_heavy");
        fuelLight = defineFluids(registry, data[index++], "fuel_light");
        fuelMixedLight = defineFluids(registry, data[index++], "fuel_mixed_light");
        fuelGaseous = defineFluids(registry, data[index++], "fuel_gaseous");
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
    }

    private static BCFluid[] defineFluids(BCRegistry registry, int[] data, String name) {
        BCFluid[] arr = new BCFluid[3];
        for (int h = 0; h < 3; h++) {
            arr[h] = defineFluid(registry, data, h, name);
        }
        return arr;
    }

    private static BCFluid defineFluid(BCRegistry registry, int[] data, int heat, String name) {
        final int density = data[0];
        final int baseViscosity = data[1];
        final int boilPoint = data[2];
        final int baseQuanta = data[3];
        final int texLight = data[4];
        final int texDark = data[5];
        // data[6] (sticky) is 1.12.2's "oilIsDense" option, off by default -- see BCFluidBlock's javadoc.
        final boolean flammable = ENABLE_OIL_BURN && data[7] == 1;

        String fullName = name + (heat == 0 ? "" : "_heat_" + heat);
        int tempAdjustedViscosity = baseViscosity * (4 - heat) / 4;
        int boilAdjustedDensity = density * (heat >= boilPoint ? -1 : 1);
        int quanta = baseQuanta + (baseQuanta > 6 ? heat : heat / 2);

        String fluidTexture = "block/fluids/" + name + "_heat_" + heat;
        ResourceLocation still = new ResourceLocation(BuildCraft.MOD_ID, fluidTexture + "_still");
        ResourceLocation flowing = new ResourceLocation(BuildCraft.MOD_ID, fluidTexture + "_flow");
        MapColor mapColour = getMapColor(texDark);

        BCFluid def = new BCFluid(name, fullName, heat);
        def.type = FLUID_TYPES.register(fullName, () -> new BCFluidType(
            FluidType.Properties.create()
                .descriptionId("fluid_type." + BuildCraft.MOD_ID + "." + name)
                .density(boilAdjustedDensity)
                .viscosity(tempAdjustedViscosity)
                .temperature(300 + 20 * heat)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY),
            still, flowing, heat, true, texLight, texDark, flammable));
        def.source = FLUIDS.register(fullName, () -> new ForgeFlowingFluid.Source(def.fluidProperties(quanta,
            tempAdjustedViscosity)));
        def.flowing = FLUIDS.register("flowing_" + fullName, () -> new ForgeFlowingFluid.Flowing(def.fluidProperties(
            quanta, tempAdjustedViscosity)));
        def.block = registry.addBlock(fullName, () -> {
            // Vanilla 1.20.1 water/lava use PushReaction.DESTROY (26.x's use POPPED); noCollission is 1.20.1's spelling.
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of()
                .mapColor(mapColour)
                .replaceable()
                .noCollission()
                .strength(100.0F)
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY);
            return new BCFluidBlock(def.source, flammable, flammable ? p.ignitedByLava() : p);
        });
        def.bucket = registry.addItem(fullName + "_bucket",
            () -> new BCFluidBucketItem(def.source, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

        ALL.add(def);
        return def;
    }

    /**
     * 1.12.2's {@code BlockFluidClassic#setQuantaPerBlock(q)} let a fluid travel {@code q - 1} blocks from its
     * source, one quanta per block, for any {@code q} from 1 to 16. Vanilla's {@code FlowingFluid} always starts at
     * level 8 and subtracts a fixed {@code levelDecreasePerBlock} per block, so it can only travel 7, 3, 2 or 1
     * blocks (a decrease of 1, 2, 3 or 4+). This picks the nearest of those to the original distance: every fluid
     * whose original spread was 5 blocks or more becomes 7 (the vanilla-water spread; the 8-to-11-block fuels
     * cannot exceed it), 3-4 becomes 3.
     */
    static int levelDecreasePerBlock(int quanta) {
        return Math.max(1, Math.round(7f / Math.max(1, quanta - 1)));
    }

    /**
     * Ticks between flow updates, as {@code viscosity / 200} -- the linear rule that reproduces vanilla's own two
     * calibration points exactly on both targets (water: viscosity 1000, {@code getTickDelay} 5; lava: viscosity
     * 6000, 30 in the overworld), floored at 1 for the thinnest searing gas. Forge 1.12.2's {@code BlockFluidBase}
     * used this same rule, but no Forge 1.12.2 jar exists in this environment to re-check it against, so the
     * vanilla calibration is what this actually rests on. (The 1.20.1 {@code ForgeFlowingFluid.Properties} defaults --
     * tick rate 5, level decrease 1, slope distance 4, explosion resistance 1 -- are identical to 26.x's
     * {@code BaseFlowingFluid.Properties}, confirmed in both sources jars.)
     */
    static int tickRate(int viscosity) {
        return Math.max(1, viscosity / 200);
    }

    /** 1.12.2's own nearest-colour search, over the same 64-entry map colour table: skip unused slots and
     * {@code NONE} (colour 0), pick the smallest squared RGB distance. */
    private static MapColor getMapColor(int color) {
        MapColor bestMapColor = MapColor.COLOR_BLACK;
        int currentDifference = Integer.MAX_VALUE;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (int id = 0; id < 64; id++) {
            MapColor mapColor = MapColor.byId(id);
            if (mapColor == null || mapColor.col == 0) {
                continue;
            }
            int mr = (mapColor.col >> 16) & 0xFF;
            int mg = (mapColor.col >> 8) & 0xFF;
            int mb = mapColor.col & 0xFF;

            int dr = mr - r;
            int dg = mg - g;
            int db = mb - b;

            int difference = dr * dr + dg * dg + db * db;

            if (difference < currentDifference) {
                currentDifference = difference;
                bestMapColor = mapColor;
            }
        }
        return bestMapColor;
    }

    /**
     * One registered BuildCraft fluid: 1.12.2's {@code BCFluid} held everything itself (and Forge created the block
     * and bucket for it), whereas here the fluid type, the two vanilla {@link Fluid} instances, the block and the
     * bucket are five separate registry entries, so this is what ties them together for the rest of the module.
     */
    public static final class BCFluid {
        private final String baseName;
        private final String name;
        private final int heat;
        private RegistryObject<BCFluidType> type;
        private RegistryObject<ForgeFlowingFluid.Source> source;
        private RegistryObject<ForgeFlowingFluid.Flowing> flowing;
        private RegistryObject<BCFluidBlock> block;
        private RegistryObject<BCFluidBucketItem> bucket;

        private BCFluid(String baseName, String name, int heat) {
            this.baseName = baseName;
            this.name = name;
            this.heat = heat;
        }

        private ForgeFlowingFluid.Properties fluidProperties(int quanta, int viscosity) {
            return new ForgeFlowingFluid.Properties(type, source, flowing)
                .bucket(bucket)
                .block(block)
                .levelDecreasePerBlock(levelDecreasePerBlock(quanta))
                .tickRate(tickRate(viscosity))
                // Vanilla water's and lava's own value (and this block's own strength(100)).
                .explosionResistance(100.0F);
        }

        /** The un-suffixed family name, e.g. {@code oil} for all three of oil's heat variants. */
        public String getBaseName() {
            return baseName;
        }

        /** The registry path, e.g. {@code oil} or {@code oil_heat_2} -- 1.12.2's fluid name exactly. */
        public String getName() {
            return name;
        }

        public int getHeatValue() {
            return heat;
        }

        public RegistryObject<BCFluidType> getType() {
            return type;
        }

        public RegistryObject<ForgeFlowingFluid.Source> getSource() {
            return source;
        }

        public RegistryObject<ForgeFlowingFluid.Flowing> getFlowing() {
            return flowing;
        }

        public RegistryObject<BCFluidBlock> getBlock() {
            return block;
        }

        public RegistryObject<BCFluidBucketItem> getBucket() {
            return bucket;
        }
    }
}
