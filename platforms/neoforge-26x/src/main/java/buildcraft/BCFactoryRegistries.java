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
import net.minecraft.world.level.material.MapColor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

import buildcraft.api.inventory.ItemTransactorCapabilities;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.tiles.TilesAPI;

import buildcraft.lib.inventory.AutomaticProvidingTransactor;
import buildcraft.lib.registry.BCRegistry;

import buildcraft.factory.block.BlockAutoWorkbenchItems;
import buildcraft.factory.block.BlockChute;
import buildcraft.factory.block.BlockFloodGate;
import buildcraft.factory.block.BlockMiningWell;
import buildcraft.factory.block.BlockPump;
import buildcraft.factory.block.BlockTank;
import buildcraft.factory.block.BlockTube;
import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.factory.tile.TileChute;
import buildcraft.factory.tile.TileFloodGate;
import buildcraft.factory.tile.TileMiningWell;
import buildcraft.factory.tile.TilePump;
import buildcraft.factory.tile.TileTank;

/**
 * Registrations belonging to the old {@code buildcraftfactory} module -- the first ones. Mirrors
 * {@link BCCoreRegistries}' structure exactly.
 */
public final class BCFactoryRegistries {

    private BCFactoryRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** Default properties match what {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft
     * block (hardness 5, resistance 10, {@code SoundType.METAL}), the same reasoning already worked out for
     * {@code BlockDecoration}/{@code BlockEngineWood}. {@code noOcclusion()} keeps the one non-cosmetic half of
     * 1.12.2's {@code isOpaqueCube() -> false} -- see {@link BlockChute}'s own javadoc for why no shape override
     * accompanies it. */
    public static final DeferredBlock<BlockChute> CHUTE = REGISTRY.addBlockAndItem("chute", BlockChute::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileChute>> CHUTE_TYPE =
        REGISTRY.addBlockEntity("chute", TileChute::new, CHUTE);

    /** Same default properties as {@link #CHUTE} -- see that field's own javadoc. */
    public static final DeferredBlock<BlockMiningWell> MINING_WELL = REGISTRY.addBlockAndItem("mining_well", BlockMiningWell::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileMiningWell>> MINING_WELL_TYPE =
        REGISTRY.addBlockEntity("mining_well", TileMiningWell::new, MINING_WELL);

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL} -- see {@link #CHUTE}'s own javadoc. */
    public static final DeferredBlock<BlockPump> PUMP = REGISTRY.addBlockAndItem("pump", BlockPump::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TilePump>> PUMP_TYPE =
        REGISTRY.addBlockEntity("pump", TilePump::new, PUMP);

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL}/{@link #PUMP} -- see {@link #CHUTE}'s own
     * javadoc. */
    public static final DeferredBlock<BlockTank> TANK = REGISTRY.addBlockAndItem("tank", BlockTank::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileTank>> TANK_TYPE =
        REGISTRY.addBlockEntity("tank", TileTank::new, TANK);

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL}/{@link #PUMP}/{@link #TANK} -- see
     * {@link #CHUTE}'s own javadoc. A plain full cube, matching {@link #PUMP}/{@link #TANK}'s own "no renderer to
     * justify a non-cube model yet" call -- see {@link BlockFloodGate}'s own javadoc for why {@code openSides} is
     * real gameplay state even though its 1.12.2 blockstate visualisation is dropped. */
    public static final DeferredBlock<BlockFloodGate> FLOOD_GATE = REGISTRY.addBlockAndItem("flood_gate", BlockFloodGate::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFloodGate>> FLOOD_GATE_TYPE =
        REGISTRY.addBlockEntity("flood_gate", TileFloodGate::new, FLOOD_GATE);

    /** No {@code BlockItem} ({@link BCRegistry#addBlock}, not {@code addBlockAndItem}) and an empty loot table --
     * see {@link BlockTube}'s own javadoc for why. {@code strength(-1.0F, ...)} matches
     * {@code BlockSpringWater}'s "always unbreakable" precedent; {@code noOcclusion()} keeps the one non-cosmetic
     * half of 1.12.2's {@code isOpaqueCube()}/{@code isFullCube() -> false}. */
    public static final DeferredBlock<BlockTube> TUBE = REGISTRY.addBlock("tube", BlockTube::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(-1.0F, 6_000_000.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .noLootTable());

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL}/{@link #PUMP}/{@link #TANK}/
     * {@link #FLOOD_GATE} -- see {@link #CHUTE}'s own javadoc. Right-click always opens
     * {@link ContainerAutoCraftItems}'s GUI, this port's first real container -- see
     * {@link BlockAutoWorkbenchItems}'s own javadoc for why there is no wrench check here, unlike
     * {@link #FLOOD_GATE}. */
    public static final DeferredBlock<BlockAutoWorkbenchItems> AUTO_WORKBENCH_ITEMS = REGISTRY.addBlockAndItem(
        "auto_workbench_item", BlockAutoWorkbenchItems::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileAutoWorkbenchItems>> AUTO_WORKBENCH_ITEMS_TYPE =
        REGISTRY.addBlockEntity("auto_workbench_item", TileAutoWorkbenchItems::new, AUTO_WORKBENCH_ITEMS);

    /** See {@link BCRegistry#addMenu}'s own javadoc for why this exists, and
     * {@code buildcraft.factory.client.BCFactoryClientRegistries} for the separate client-only screen
     * registration this pairs with. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerAutoCraftItems>> AUTO_WORKBENCH_ITEMS_MENU =
        REGISTRY.addMenu("auto_workbench_item", ContainerAutoCraftItems::new);

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCFactoryRegistries::registerCapabilities);
    }

    /**
     * A chute's inventory is reachable from every {@code EnumPipePart} it registered with
     * ({@code EnumAccess.INSERT, EnumPipePart.VALUES} -- see {@link TileChute}), so unlike
     * {@code BCCoreRegistries}' engine connector (guarded to a single facing) this delegates to
     * {@code itemManager.getHandlerForFace} unconditionally, for every side.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, CHUTE_TYPE.get(), (tile, side) -> tile.itemManager.getHandlerForFace(side));
        event.registerBlockEntity(MjCapabilities.RECEIVER, CHUTE_TYPE.get(), (tile, side) -> tile.mjReceiver);
        event.registerBlockEntity(MjCapabilities.READABLE, CHUTE_TYPE.get(), (tile, side) -> tile.mjReceiver);

        event.registerBlockEntity(MjCapabilities.RECEIVER, MINING_WELL_TYPE.get(), (tile, side) -> tile.mjReceiver);
        event.registerBlockEntity(MjCapabilities.READABLE, MINING_WELL_TYPE.get(), (tile, side) -> tile.mjReceiver);
        // See TileMiner's own javadoc for why this reads the isComplete() method, not a mirrored field.
        event.registerBlockEntity(TilesAPI.HAS_WORK, MINING_WELL_TYPE.get(), (tile, side) -> () -> !tile.isComplete());
        event.registerBlockEntity(ItemTransactorCapabilities.ITEM_TRANSACTOR, MINING_WELL_TYPE.get(),
            (tile, side) -> AutomaticProvidingTransactor.INSTANCE);

        // See TilePump's own javadoc for why this registers mjRedstoneReceiver rather than the inherited
        // (plain, non-redstone) TileMiner#mjReceiver.
        event.registerBlockEntity(MjCapabilities.RECEIVER, PUMP_TYPE.get(), (tile, side) -> tile.mjRedstoneReceiver);
        event.registerBlockEntity(MjCapabilities.READABLE, PUMP_TYPE.get(), (tile, side) -> tile.mjRedstoneReceiver);
        event.registerBlockEntity(TilesAPI.HAS_WORK, PUMP_TYPE.get(), (tile, side) -> () -> !tile.isComplete());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, PUMP_TYPE.get(), (tile, side) -> tile.tank);

        // TileTank implements ResourceHandler<FluidResource> itself (a single logical slot spanning the whole
        // connected column) -- see that class's own javadoc -- so this hands back the tile, not a field.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, TANK_TYPE.get(), (tile, side) -> tile);

        // A flood gate's tank is a single, non-stacking slot, registered directly the same way TilePump's own
        // tank field is -- unlike TileTank, there is no aggregating column to walk.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLOOD_GATE_TYPE.get(), (tile, side) -> tile.tank);

        // The auto-workbench implements IMjRedstoneReceiver directly (no separate battery-backed receiver field
        // the way TilePump/TileChute have -- see TileAutoWorkbenchBase's own javadoc), so the tile itself is
        // handed back here, the same way TileTank hands back itself for its fluid capability above.
        event.registerBlockEntity(Capabilities.Item.BLOCK, AUTO_WORKBENCH_ITEMS_TYPE.get(), (tile, side) -> tile.itemManager.getHandlerForFace(side));
        event.registerBlockEntity(MjCapabilities.RECEIVER, AUTO_WORKBENCH_ITEMS_TYPE.get(), (tile, side) -> tile);
        event.registerBlockEntity(TilesAPI.HAS_WORK, AUTO_WORKBENCH_ITEMS_TYPE.get(), (tile, side) -> tile);
    }
}
