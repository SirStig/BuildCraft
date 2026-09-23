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

import buildcraft.api.enums.EnumLaserTableType;

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
 * beam emitter, plus the four tables it feeds). Mirrors the 26.x class of the same name -- see that one's own
 * javadoc for the module-scope and programming-table notes. No {@code registerCapabilities} here: on this target
 * each tile answers {@code getCapability} itself (see {@code TileLaserTableBase}'s and {@code TileLaser}'s own
 * javadoc), matching {@code buildcraft.factory.tile.TileAutoWorkbenchBase}'s own precedent -- there is no
 * {@code RegisterCapabilitiesEvent} on 1.20.1 Forge/NeoForge.
 */
public final class BCSiliconRegistries {

    private BCSiliconRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** See the 26.x copy of this field's own javadoc. */
    public static final RegistryObject<BlockLaser> LASER = REGISTRY.addBlockAndItem("laser",
        () -> new BlockLaser(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileLaser>> LASER_TYPE =
        REGISTRY.addBlockEntity("laser", TileLaser::new, LASER);

    /** See the 26.x copy of this field's own javadoc. */
    public static final RegistryObject<BlockLaserTable> ASSEMBLY_TABLE = REGISTRY.addBlockAndItem("assembly_table",
        () -> new BlockLaserTable(EnumLaserTableType.ASSEMBLY_TABLE, BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileAssemblyTable>> ASSEMBLY_TABLE_TYPE =
        REGISTRY.addBlockEntity("assembly_table", TileAssemblyTable::new, ASSEMBLY_TABLE);

    public static final RegistryObject<MenuType<ContainerAssemblyTable>> ASSEMBLY_TABLE_MENU =
        REGISTRY.addMenu("assembly_table", ContainerAssemblyTable::new);

    public static final RegistryObject<BlockLaserTable> ADVANCED_CRAFTING_TABLE = REGISTRY.addBlockAndItem("advanced_crafting_table",
        () -> new BlockLaserTable(EnumLaserTableType.ADVANCED_CRAFTING_TABLE, BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileAdvancedCraftingTable>> ADVANCED_CRAFTING_TABLE_TYPE =
        REGISTRY.addBlockEntity("advanced_crafting_table", TileAdvancedCraftingTable::new, ADVANCED_CRAFTING_TABLE);

    public static final RegistryObject<MenuType<ContainerAdvancedCraftingTable>> ADVANCED_CRAFTING_TABLE_MENU =
        REGISTRY.addMenu("advanced_crafting_table", ContainerAdvancedCraftingTable::new);

    public static final RegistryObject<BlockLaserTable> INTEGRATION_TABLE = REGISTRY.addBlockAndItem("integration_table",
        () -> new BlockLaserTable(EnumLaserTableType.INTEGRATION_TABLE, BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileIntegrationTable>> INTEGRATION_TABLE_TYPE =
        REGISTRY.addBlockEntity("integration_table", TileIntegrationTable::new, INTEGRATION_TABLE);

    public static final RegistryObject<MenuType<ContainerIntegrationTable>> INTEGRATION_TABLE_MENU =
        REGISTRY.addMenu("integration_table", ContainerIntegrationTable::new);

    /** No menu -- 1.12.2's charging table never opened one either, see {@code TileChargingTable}'s own javadoc. */
    public static final RegistryObject<BlockLaserTable> CHARGING_TABLE = REGISTRY.addBlockAndItem("charging_table",
        () -> new BlockLaserTable(EnumLaserTableType.CHARGING_TABLE, BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileChargingTable>> CHARGING_TABLE_TYPE =
        REGISTRY.addBlockEntity("charging_table", TileChargingTable::new, CHARGING_TABLE);

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
