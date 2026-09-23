/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import net.minecraft.core.Direction;

/** Lets a {@link buildcraft.api.transport.pipe.PipeBehaviour} override {@link PipeFlowPower}'s default per-side
 * power request -- see the 26.x copy of this file for the full account (identical on both targets; nothing here
 * touches a Minecraft type beyond {@link Direction}). */
public interface IPipeTransportPowerHook {

    /** Override default behavior on receiving energy into the pipe.
     *
     * @return The amount of power used, or -1 for default behavior. */
    int receivePower(Direction from, long val);

    /** Override default requested power. */
    int requestPower(Direction from, long amount);
}
