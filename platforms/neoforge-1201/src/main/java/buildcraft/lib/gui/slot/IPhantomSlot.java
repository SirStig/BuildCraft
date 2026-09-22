/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.slot;

/** Phantom slots don't "use" items -- they are used for blueprint patterns and filters. A click on one of these
 * sets its content to match whatever is on the cursor rather than picking the item up; see
 * {@link buildcraft.lib.gui.ContainerBCTile#clicked} for the actual interception. */
public interface IPhantomSlot {
    /** @return True if this slot can hold a count other than 0 (empty) or 1 (filled), false to cap it at 1. */
    boolean canAdjustCount();
}
