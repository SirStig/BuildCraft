/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.robotics.client;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.robotics.gui.GuiZonePlanner;

import buildcraft.BCRoboticsRegistries;

/**
 * Client-only menu screen registration for {@code buildcraft.robotics}. Mirrors
 * {@code buildcraft.energy.client.BCEnergyClientRegistries} exactly -- see that class's own javadoc for why this
 * has to be gated at the listener registration itself, in {@code BuildCraft}'s constructor, rather than inside
 * the method body.
 */
public final class BCRoboticsClientRegistries {

    private BCRoboticsClientRegistries() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCRoboticsRegistries.ZONE_PLANNER_MENU.get(), GuiZonePlanner::new);
    }
}
