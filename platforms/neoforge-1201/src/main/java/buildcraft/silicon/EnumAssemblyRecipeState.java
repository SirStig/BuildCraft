/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon;

/** How the assembly table currently regards one possible recipe output -- unchanged from 1.12.2. */
public enum EnumAssemblyRecipeState {
    POSSIBLE,
    SAVED,
    SAVED_ENOUGH,
    SAVED_ENOUGH_ACTIVE
}
