/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import net.minecraftforge.eventbus.api.IEventBus;

import buildcraft.api.mj.MjCapabilities;

/** Central hook-up point for every {@code DeferredRegister} BuildCraft owns on 1.20.1. */
public final class BCRegistries {

    private BCRegistries() {}

    public static void register(IEventBus modBus) {
        BCCoreRegistries.register(modBus);
        // 1.20.1 lost @CapabilityInject, so every capability BuildCraft defines has to be declared here.
        modBus.addListener(MjCapabilities::register);
    }
}
