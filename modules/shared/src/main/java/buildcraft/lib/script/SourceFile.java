/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.script;

public final class SourceFile {
    public final String name;
    public final int lineCount;

    public SourceFile(String name, int lineCount) {
        this.name = name;
        this.lineCount = lineCount;
    }
}