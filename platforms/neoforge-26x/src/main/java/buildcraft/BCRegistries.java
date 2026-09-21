/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
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
