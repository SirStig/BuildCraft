/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import net.minecraftforge.items.IItemHandler;

/** A form of {@link IItemHandler} that provides insertion-checking functionality via {@link StackInsertionChecker}.
 * Unported consumers (the GUI/slot layer, deferred with the rest of {@code lib.gui}) will implement this against
 * whatever handler they wrap once that layer is ported. */
public interface IItemHandlerAdv extends IItemHandler, StackInsertionChecker {}
