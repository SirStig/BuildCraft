/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegistryObject;

import buildcraft.api.fuels.BuildcraftFuelRegistry;

import buildcraft.lib.fluid.CoolantRegistry;
import buildcraft.lib.fluid.FuelRegistry;
import buildcraft.lib.registry.BCRegistry;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.energy.BCEnergyRecipes;

import buildcraft.energy.block.BlockEngineIron;
import buildcraft.energy.block.BlockEngineStone;
import buildcraft.energy.container.ContainerEngineIron;
import buildcraft.energy.container.ContainerEngineStone;
import buildcraft.energy.tile.TileEngineIron;
import buildcraft.energy.tile.TileEngineStone;

/**
 * Registrations belonging to the old {@code buildcraftenergy} module -- the first ones. Mirrors
 * {@link BCFactoryRegistries}' structure. Unlike 26.x, {@link TileEngineStone} exposes its own item capability
 * through {@code getCapability} (see that class's own javadoc), so there is no capability-registration listener
 * here -- the MJ connector capability is likewise already handled by the inherited {@code TileEngineBase}
 * {@code getCapability} override, matching {@code BCCoreRegistries}' {@code ENGINE_WOOD}/{@code ENGINE_CREATIVE}.
 */
public final class BCEnergyRegistries {

    private BCEnergyRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** Properties match every other machine ported so far ({@code BCCoreRegistries#ENGINE_WOOD}/
     * {@code #ENGINE_CREATIVE}, {@code BCFactoryRegistries#CHUTE}, ...): hardness 5, resistance 10,
     * {@code SoundType.METAL}. */
    public static final RegistryObject<BlockEngineStone> ENGINE_STONE = REGISTRY.addBlockAndItem(
        "engine_stone", () -> new BlockEngineStone(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 10.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileEngineStone>> ENGINE_STONE_TYPE =
        REGISTRY.addBlockEntity("engine_stone", TileEngineStone::new, ENGINE_STONE);

    /** See {@link BCRegistry#addMenu}'s own javadoc for why this exists, and
     * {@code buildcraft.energy.client.BCEnergyClientRegistries} for the separate client-only screen registration
     * this pairs with. */
    public static final RegistryObject<MenuType<ContainerEngineStone>> ENGINE_STONE_MENU =
        REGISTRY.addMenu("engine_stone", ContainerEngineStone::new);

    /** The Combustion Engine -- 1.12.2's {@code IRON} engine type, registered right after the Stirling engine as
     * 1.12.2's {@code BCEnergyBlocks} did. Same block properties as {@link #ENGINE_STONE}. Its fluid capability is
     * exposed by the tile itself ({@code TileEngineIron#getCapability}), like every other capability on this
     * target. */
    public static final RegistryObject<BlockEngineIron> ENGINE_IRON = REGISTRY.addBlockAndItem(
        "engine_iron", () -> new BlockEngineIron(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 10.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileEngineIron>> ENGINE_IRON_TYPE =
        REGISTRY.addBlockEntity("engine_iron", TileEngineIron::new, ENGINE_IRON);

    public static final RegistryObject<MenuType<ContainerEngineIron>> ENGINE_IRON_MENU =
        REGISTRY.addMenu("engine_iron", ContainerEngineIron::new);

    /* The oil/fuel fluid family: a fluid type, source + flowing fluid, placeable block and bucket for each of the
     * thirty, all defined in BCEnergyFluids. Called here, after the engines, so the buckets follow them in the
     * creative tab. */
    static {
        BCEnergyFluids.preInit(REGISTRY);
    }

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        BCEnergyFluids.register(modBus);
        // 1.12.2 installed these in BCLibRegistries#preInit; energy is the only module that fills them.
        BuildcraftFuelRegistry.fuel = FuelRegistry.INSTANCE;
        BuildcraftFuelRegistry.coolant = CoolantRegistry.INSTANCE;
        modBus.addListener(BCEnergyRegistries::commonSetup);
    }

    /** 1.12.2's {@code FMLInitializationEvent} step: the fuel/coolant values need the registered fluids. Queued
     * onto the main thread because the two registries are plain unsynchronised lists and common setup itself runs
     * in parallel across mods. */
    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(BCEnergyRecipes::init);
    }
}
