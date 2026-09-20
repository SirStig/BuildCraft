/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import net.neoforged.bus.api.IEventBus;

/**
 * Central hook-up point for every {@code DeferredRegister} BuildCraft owns.
 *
 * <p>Registration is being ported module by module; each module gets its own holder class
 * and is attached here as it lands.
 */
public final class BCRegistries {

    private BCRegistries() {}

    public static void register(IEventBus modBus) {
        BCCoreRegistries.register(modBus);
    }
}
