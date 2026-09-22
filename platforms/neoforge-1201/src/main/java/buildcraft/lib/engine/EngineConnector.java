/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.engine;

import org.jetbrains.annotations.NotNull;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.IMjRedstoneReceiver;

/** Unchanged from 1.12.2 -- this class has no Minecraft dependency at all, only the shared MJ interfaces. It is
 * kept duplicated per platform rather than moved to {@code modules/shared} purely to stay alongside
 * {@link TileEngineBase}, which does need to be per-platform; see that class's javadoc. */
public class EngineConnector implements IMjConnector {
    public final boolean redstoneOnly;

    public EngineConnector(boolean redstoneOnly) {
        this.redstoneOnly = redstoneOnly;
    }

    @Override
    public boolean canConnect(@NotNull IMjConnector other) {
        if (other instanceof IMjReceiver receiver && receiver.canReceive()) {
            if (redstoneOnly) {
                return other instanceof IMjRedstoneReceiver;
            }
            return true;
        }
        return false;
    }
}
