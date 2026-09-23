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
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

import buildcraft.lib.registry.BCRegistry;

import buildcraft.robotics.block.BlockZonePlanner;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.tile.TileZonePlanner;

/**
 * Registrations belonging to the old {@code buildcraftrobotics} module -- confirmed by reading the actual 1.12.2
 * directory (not assumed) that this module is, in its entirety, the Zone Planner: a chunk-grid claim-and-map
 * tool, not a robot system at all (the {@code _Neptune}-suffixed programming table container/GUI pair under
 * {@code common/buildcraft/robotics/container}/{@code gui} is genuinely dead 1.12.2 code -- confirmed unused
 * there via a repo-wide reference search: nothing registers or opens
 * {@code ContainerProgrammingTable_Neptune}/{@code GuiProgrammingTable_Neptune} anywhere, in either the original
 * source or this port, so neither is ported). Mirrors {@link BCFactoryRegistries}'s structure exactly.
 */
public final class BCRoboticsRegistries {

    private BCRoboticsRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** Same default properties as every other machine ported so far ({@code BCEnergyRegistries#ENGINE_STONE},
     * {@code BCFactoryRegistries#CHUTE}, ...): hardness 5, resistance 10, {@code SoundType.METAL}. */
    public static final DeferredBlock<BlockZonePlanner> ZONE_PLANNER = REGISTRY.addBlockAndItem(
        "zone_planner", BlockZonePlanner::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileZonePlanner>> ZONE_PLANNER_TYPE =
        REGISTRY.addBlockEntity("zone_planner", TileZonePlanner::new, ZONE_PLANNER);

    /** See {@code BCRegistry#addMenu}'s own javadoc for why this exists, and
     * {@code buildcraft.robotics.client.BCRoboticsClientRegistries} for the separate client-only screen
     * registration this pairs with. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerZonePlanner>> ZONE_PLANNER_MENU =
        REGISTRY.addMenu("zone_planner", ContainerZonePlanner::new);

    /** No capability registration listener is needed here: the paintbrush storage grid is
     * {@code EnumAccess.NONE} (see {@link TileZonePlanner}, matching 1.12.2's own inaccessible-from-outside
     * choice for the same slots), so there is no external item handler to expose on any face -- unlike
     * {@code BCFactoryRegistries#CHUTE} or {@code BCEnergyRegistries#ENGINE_STONE}. */
    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
