/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

/** A click on a statement slot, as forwarded from the GUI. */
public record StatementMouseClick(int button, boolean shift) {

    public boolean isShift() {
        return shift;
    }

    public int getButton() {
        return button;
    }
}
