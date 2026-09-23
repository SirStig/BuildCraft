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

import buildcraft.lib.registry.BCRegistry;

import buildcraft.robotics.block.BlockZonePlanner;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.tile.TileZonePlanner;

/**
 * Registrations belonging to the old {@code buildcraftrobotics} module -- see the 26.x copy of this class for the
 * confirmation that this module is, in its entirety, the Zone Planner (not a robot system), and for why the
 * {@code _Neptune}-suffixed programming table pair is genuinely dead 1.12.2 code, not ported here either. Mirrors
 * {@link BCEnergyRegistries}'s structure. No capability registration listener is needed: the paintbrush storage
 * grid is exposed by {@link TileZonePlanner#getCapability} itself, and it is {@code EnumAccess.NONE} there
 * anyway (matching 1.12.2's own inaccessible-from-outside choice), so that lookup always answers empty.
 */
public final class BCRoboticsRegistries {

    private BCRoboticsRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** Same default properties as every other machine ported so far ({@code BCEnergyRegistries#ENGINE_STONE},
     * {@code BCFactoryRegistries#CHUTE}, ...): hardness 5, resistance 10, {@code SoundType.METAL}. */
    public static final RegistryObject<BlockZonePlanner> ZONE_PLANNER = REGISTRY.addBlockAndItem(
        "zone_planner", () -> new BlockZonePlanner(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 10.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileZonePlanner>> ZONE_PLANNER_TYPE =
        REGISTRY.addBlockEntity("zone_planner", TileZonePlanner::new, ZONE_PLANNER);

    /** See {@code BCRegistry#addMenu}'s own javadoc for why this exists, and
     * {@code buildcraft.robotics.client.BCRoboticsClientRegistries} for the separate client-only screen
     * registration this pairs with. */
    public static final RegistryObject<MenuType<ContainerZonePlanner>> ZONE_PLANNER_MENU =
        REGISTRY.addMenu("zone_planner", ContainerZonePlanner::new);

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
