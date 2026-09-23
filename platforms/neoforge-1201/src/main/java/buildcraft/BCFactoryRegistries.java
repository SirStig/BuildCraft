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
import net.minecraftforge.registries.RegistryObject;

import buildcraft.api.inventory.ItemTransactorCapabilities;

import buildcraft.lib.registry.BCRegistry;

import buildcraft.factory.block.BlockAutoWorkbenchFluids;
import buildcraft.factory.block.BlockAutoWorkbenchItems;
import buildcraft.factory.block.BlockChute;
import buildcraft.factory.block.BlockDistiller;
import buildcraft.factory.block.BlockFloodGate;
import buildcraft.factory.block.BlockHeatExchange;
import buildcraft.factory.block.BlockMiningWell;
import buildcraft.factory.block.BlockPump;
import buildcraft.factory.block.BlockTank;
import buildcraft.factory.block.BlockTube;
import buildcraft.factory.container.ContainerAutoCraftFluids;
import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.factory.tile.TileAutoWorkbenchFluids;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.factory.tile.TileChute;
import buildcraft.factory.tile.TileDistiller;
import buildcraft.factory.tile.TileFloodGate;
import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.factory.tile.TileMiningWell;
import buildcraft.factory.tile.TilePump;
import buildcraft.factory.tile.TileTank;

/**
 * Registrations belonging to the old {@code buildcraftfactory} module -- the first ones. Mirrors
 * {@link BCCoreRegistries}' structure. Unlike 26.x, {@code TileChute} exposes its own capabilities through
 * {@code getCapability} (see that class's own javadoc), so there is no capability-registration listener here --
 * {@link ItemTransactorCapabilities#register} still needs a hook, since 1.20.1 lost {@code @CapabilityInject} and
 * every capability this port defines has to be declared somewhere (matching {@code MjCapabilities}' own
 * precedent), even though nothing in this pass provides {@code IItemTransactor} as a capability yet.
 */
public final class BCFactoryRegistries {

    private BCFactoryRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** Default properties match what {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft
     * block (hardness 5, resistance 10, {@code SoundType.METAL}), the same reasoning already worked out for
     * {@code BlockDecoration}/{@code BlockEngineWood}. {@code noOcclusion()} keeps the one non-cosmetic half of
     * 1.12.2's {@code isOpaqueCube() -> false} -- see {@link BlockChute}'s own javadoc for why no shape override
     * accompanies it. */
    public static final RegistryObject<BlockChute> CHUTE = REGISTRY.addBlockAndItem("chute", () -> new BlockChute(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileChute>> CHUTE_TYPE =
        REGISTRY.addBlockEntity("chute", TileChute::new, CHUTE);

    /** Same default properties as {@link #CHUTE} -- see that field's own javadoc. */
    public static final RegistryObject<BlockMiningWell> MINING_WELL = REGISTRY.addBlockAndItem("mining_well", () -> new BlockMiningWell(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileMiningWell>> MINING_WELL_TYPE =
        REGISTRY.addBlockEntity("mining_well", TileMiningWell::new, MINING_WELL);

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL} -- see {@link #CHUTE}'s own javadoc. */
    public static final RegistryObject<BlockPump> PUMP = REGISTRY.addBlockAndItem("pump", () -> new BlockPump(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TilePump>> PUMP_TYPE =
        REGISTRY.addBlockEntity("pump", TilePump::new, PUMP);

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL}/{@link #PUMP} -- see {@link #CHUTE}'s own
     * javadoc. */
    public static final RegistryObject<BlockTank> TANK = REGISTRY.addBlockAndItem("tank", () -> new BlockTank(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileTank>> TANK_TYPE =
        REGISTRY.addBlockEntity("tank", TileTank::new, TANK);

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL}/{@link #PUMP}/{@link #TANK} -- see
     * {@link #CHUTE}'s own javadoc. A plain full cube -- see {@link BlockFloodGate}'s own javadoc, and the 26.x
     * copy of this file, for why {@code openSides} is real gameplay state even though its 1.12.2 blockstate
     * visualisation is dropped. */
    public static final RegistryObject<BlockFloodGate> FLOOD_GATE = REGISTRY.addBlockAndItem("flood_gate", () -> new BlockFloodGate(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileFloodGate>> FLOOD_GATE_TYPE =
        REGISTRY.addBlockEntity("flood_gate", TileFloodGate::new, FLOOD_GATE);

    /** No {@code BlockItem} ({@link BCRegistry#addBlock}, not {@code addBlockAndItem}) and an empty loot table --
     * see {@link BlockTube}'s own javadoc for why. {@code strength(-1.0F, ...)} matches
     * {@code BlockSpringWater}'s "always unbreakable" precedent; {@code noOcclusion()} keeps the one non-cosmetic
     * half of 1.12.2's {@code isOpaqueCube()}/{@code isFullCube() -> false}. */
    public static final RegistryObject<BlockTube> TUBE = REGISTRY.addBlock("tube", () -> new BlockTube(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(-1.0F, 6_000_000.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .noLootTable()));

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL}/{@link #PUMP}/{@link #TANK}/
     * {@link #FLOOD_GATE} -- see {@link #CHUTE}'s own javadoc. Right-click always opens
     * {@link ContainerAutoCraftItems}'s GUI, this port's first real container -- see
     * {@link BlockAutoWorkbenchItems}'s own javadoc for why there is no wrench check here, unlike
     * {@link #FLOOD_GATE}. */
    public static final RegistryObject<BlockAutoWorkbenchItems> AUTO_WORKBENCH_ITEMS = REGISTRY.addBlockAndItem(
        "auto_workbench_item", () -> new BlockAutoWorkbenchItems(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 10.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileAutoWorkbenchItems>> AUTO_WORKBENCH_ITEMS_TYPE =
        REGISTRY.addBlockEntity("auto_workbench_item", TileAutoWorkbenchItems::new, AUTO_WORKBENCH_ITEMS);

    /** See {@link BCRegistry#addMenu}'s own javadoc for why this exists, and
     * {@code buildcraft.factory.client.BCFactoryClientRegistries} for the separate client-only screen
     * registration this pairs with. */
    public static final RegistryObject<MenuType<ContainerAutoCraftItems>> AUTO_WORKBENCH_ITEMS_MENU =
        REGISTRY.addMenu("auto_workbench_item", ContainerAutoCraftItems::new);

    /** Same default properties as {@link #CHUTE}/etc -- see {@link #CHUTE}'s own javadoc. Registry name
     * {@code auto_workbench_fluid} is this port's own choice, not a carried-over original -- see the 26.x copy of
     * this file, and {@code buildcraft.factory.tile.TileAutoWorkbenchFluids}'s own javadoc, for why 1.12.2 never
     * actually registered this block (or created any asset for it) at all. Unlike 26.x, this tile exposes its own
     * fluid capability through {@code getCapability} (see that class's own javadoc), so there is nothing to add
     * to a capability-registration listener here -- this file has none, matching {@link #CHUTE}'s own precedent. */
    public static final RegistryObject<BlockAutoWorkbenchFluids> AUTO_WORKBENCH_FLUIDS = REGISTRY.addBlockAndItem(
        "auto_workbench_fluid", () -> new BlockAutoWorkbenchFluids(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 10.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileAutoWorkbenchFluids>> AUTO_WORKBENCH_FLUIDS_TYPE =
        REGISTRY.addBlockEntity("auto_workbench_fluid", TileAutoWorkbenchFluids::new, AUTO_WORKBENCH_FLUIDS);

    /** See {@link #AUTO_WORKBENCH_ITEMS_MENU}'s own javadoc. */
    public static final RegistryObject<MenuType<ContainerAutoCraftFluids>> AUTO_WORKBENCH_FLUIDS_MENU =
        REGISTRY.addMenu("auto_workbench_fluid", ContainerAutoCraftFluids::new);

    /** 1.12.2's {@code buildcraftfactory:distiller} -- see the 26.x copy of this file. Its fluid/MJ/has-work
     * capabilities come from {@code TileDistiller#getCapability}. */
    public static final RegistryObject<BlockDistiller> DISTILLER = REGISTRY.addBlockAndItem("distiller", () -> new BlockDistiller(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileDistiller>> DISTILLER_TYPE =
        REGISTRY.addBlockEntity("distiller", TileDistiller::new, DISTILLER);

    /** See {@link #AUTO_WORKBENCH_ITEMS_MENU}'s own javadoc. */
    public static final RegistryObject<MenuType<ContainerDistiller>> DISTILLER_MENU =
        REGISTRY.addMenu("distiller", ContainerDistiller::new);

    /** 1.12.2's {@code buildcraftfactory:heat_exchange} -- same properties as {@link #DISTILLER}. */
    public static final RegistryObject<BlockHeatExchange> HEAT_EXCHANGE = REGISTRY.addBlockAndItem("heat_exchange",
        () -> new BlockHeatExchange(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 10.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileHeatExchange>> HEAT_EXCHANGE_TYPE =
        REGISTRY.addBlockEntity("heat_exchange", TileHeatExchange::new, HEAT_EXCHANGE);

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(ItemTransactorCapabilities::register);
    }
}
