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

import buildcraft.api.enums.EnumLaserTableType;
import buildcraft.api.mj.MjCapabilities;

import buildcraft.lib.registry.BCRegistry;

import buildcraft.silicon.block.BlockLaser;
import buildcraft.silicon.block.BlockLaserTable;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.container.ContainerAssemblyTable;
import buildcraft.silicon.container.ContainerIntegrationTable;
import buildcraft.silicon.tile.TileAdvancedCraftingTable;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.silicon.tile.TileChargingTable;
import buildcraft.silicon.tile.TileIntegrationTable;
import buildcraft.silicon.tile.TileLaser;

/**
 * Registrations belonging to the old {@code buildcraftsilicon} module's standalone laser-powered machines (the
 * beam emitter, plus the assembly table, advanced crafting table, integration table and charging table it feeds).
 * Mirrors {@link BCFactoryRegistries}' structure exactly.
 *
 * <p>Wires/gates/pluggables/facades -- also nominally under 1.12.2's {@code buildcraft.silicon} package -- are a
 * different agent's scope this round and have no registrations here yet.
 *
 * <p>{@link EnumLaserTableType#PROGRAMMING_TABLE} has no block here -- see {@code BlockLaserTable}'s own javadoc
 * for why 1.12.2's {@code TileProgrammingTable_Neptune} is confirmed-dead code, not a real feature to port.
 */
public final class BCSiliconRegistries {

    private BCSiliconRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** 1.12.2's {@code buildcraftsilicon:laser} ({@code Material.IRON}) -- the laser-beam emitter that feeds every
     * table below its MJ. See {@code TileLaser}'s own javadoc for the machine logic and {@code BlockLaser}'s for
     * the block. */
    public static final DeferredBlock<BlockLaser> LASER = REGISTRY.addBlockAndItem(
        "laser",
        BlockLaser::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileLaser>> LASER_TYPE =
        REGISTRY.addBlockEntity("laser", TileLaser::new, LASER);

    /** 1.12.2's {@code buildcraftsilicon:assembly_table} ({@code Material.IRON}) -- same default properties as
     * every other iron-tier BuildCraft machine ({@code buildcraft.factory.block.BlockChute}'s own precedent).
     * {@code noOcclusion()} keeps 1.12.2's {@code isOpaqueCube()/isFullCube() -> false} (a thin table-top, not a
     * full cube -- see {@link BlockLaserTable}'s own javadoc for why the actual custom shape isn't ported yet). */
    public static final DeferredBlock<BlockLaserTable> ASSEMBLY_TABLE = REGISTRY.addBlockAndItem(
        "assembly_table",
        properties -> new BlockLaserTable(EnumLaserTableType.ASSEMBLY_TABLE, properties),
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileAssemblyTable>> ASSEMBLY_TABLE_TYPE =
        REGISTRY.addBlockEntity("assembly_table", TileAssemblyTable::new, ASSEMBLY_TABLE);

    public static final DeferredHolder<MenuType<?>, MenuType<ContainerAssemblyTable>> ASSEMBLY_TABLE_MENU =
        REGISTRY.addMenu("assembly_table", ContainerAssemblyTable::new);

    /** Same default properties as {@link #ASSEMBLY_TABLE} -- see that field's own javadoc. */
    public static final DeferredBlock<BlockLaserTable> ADVANCED_CRAFTING_TABLE = REGISTRY.addBlockAndItem(
        "advanced_crafting_table",
        properties -> new BlockLaserTable(EnumLaserTableType.ADVANCED_CRAFTING_TABLE, properties),
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileAdvancedCraftingTable>> ADVANCED_CRAFTING_TABLE_TYPE =
        REGISTRY.addBlockEntity("advanced_crafting_table", TileAdvancedCraftingTable::new, ADVANCED_CRAFTING_TABLE);

    public static final DeferredHolder<MenuType<?>, MenuType<ContainerAdvancedCraftingTable>> ADVANCED_CRAFTING_TABLE_MENU =
        REGISTRY.addMenu("advanced_crafting_table", ContainerAdvancedCraftingTable::new);

    /** Same default properties as {@link #ASSEMBLY_TABLE} -- see that field's own javadoc. */
    public static final DeferredBlock<BlockLaserTable> INTEGRATION_TABLE = REGISTRY.addBlockAndItem(
        "integration_table",
        properties -> new BlockLaserTable(EnumLaserTableType.INTEGRATION_TABLE, properties),
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileIntegrationTable>> INTEGRATION_TABLE_TYPE =
        REGISTRY.addBlockEntity("integration_table", TileIntegrationTable::new, INTEGRATION_TABLE);

    public static final DeferredHolder<MenuType<?>, MenuType<ContainerIntegrationTable>> INTEGRATION_TABLE_MENU =
        REGISTRY.addMenu("integration_table", ContainerIntegrationTable::new);

    /** Same default properties as {@link #ASSEMBLY_TABLE} -- see that field's own javadoc. No menu: 1.12.2's
     * charging table never opened one either -- see {@code TileChargingTable}'s own javadoc. */
    public static final DeferredBlock<BlockLaserTable> CHARGING_TABLE = REGISTRY.addBlockAndItem(
        "charging_table",
        properties -> new BlockLaserTable(EnumLaserTableType.CHARGING_TABLE, properties),
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileChargingTable>> CHARGING_TABLE_TYPE =
        REGISTRY.addBlockEntity("charging_table", TileChargingTable::new, CHARGING_TABLE);

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCSiliconRegistries::registerCapabilities);
    }

    /** Every table's item inventory is reachable from every side, matching 1.12.2's {@code EnumPipePart.VALUES}
     * wiring on each -- see each tile's own constructor. The laser's own {@code mjReceiver} is exposed the same
     * way {@code TileMiner}/{@code TileChute} expose theirs -- see {@link BCFactoryRegistries}. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MjCapabilities.RECEIVER, LASER_TYPE.get(), (tile, side) -> tile.mjReceiver);
        event.registerBlockEntity(MjCapabilities.READABLE, LASER_TYPE.get(), (tile, side) -> tile.mjReceiver);

        event.registerBlockEntity(Capabilities.Item.BLOCK, ASSEMBLY_TABLE_TYPE.get(),
            (tile, side) -> tile.itemManager.getHandlerForFace(side));

        event.registerBlockEntity(Capabilities.Item.BLOCK, ADVANCED_CRAFTING_TABLE_TYPE.get(),
            (tile, side) -> tile.itemManager.getHandlerForFace(side));

        event.registerBlockEntity(Capabilities.Item.BLOCK, INTEGRATION_TABLE_TYPE.get(),
            (tile, side) -> tile.itemManager.getHandlerForFace(side));
    }
}
