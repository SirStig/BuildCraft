/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.minecraftforge.eventbus.api.IEventBus;

import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.tiles.TilesAPI;

/** Central hook-up point for every {@code DeferredRegister} BuildCraft owns on 1.20.1. */
public final class BCRegistries {

    private BCRegistries() {}

    public static void register(IEventBus modBus) {
        BCCoreRegistries.register(modBus);
        // 1.20.1 lost @CapabilityInject, so every capability BuildCraft defines has to be declared here.
        modBus.addListener(MjCapabilities::register);
        modBus.addListener(TilesAPI::register);
    }
}
