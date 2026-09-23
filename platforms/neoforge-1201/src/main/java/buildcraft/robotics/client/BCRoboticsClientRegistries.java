/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.robotics.client;

import net.minecraft.client.gui.screens.MenuScreens;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import buildcraft.robotics.gui.GuiZonePlanner;

import buildcraft.BCRoboticsRegistries;

/**
 * Client-only menu screen registration for {@code buildcraft.robotics}. Mirrors
 * {@code buildcraft.energy.client.BCEnergyClientRegistries} exactly.
 */
public final class BCRoboticsClientRegistries {

    private BCRoboticsClientRegistries() {}

    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(BCRoboticsRegistries.ZONE_PLANNER_MENU.get(), GuiZonePlanner::new));
    }
}
