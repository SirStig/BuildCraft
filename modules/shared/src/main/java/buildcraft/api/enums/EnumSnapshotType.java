/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.enums;

/** The two kinds of snapshot a builder can work from: a template (shape only) or a blueprint (shape and blocks). */
public enum EnumSnapshotType {
    TEMPLATE(900),
    BLUEPRINT(300);

    public static final EnumSnapshotType[] VALUES = values();

    /** How many blocks of this snapshot kind a builder may place in a single tick. */
    public final int maxPerTick;

    EnumSnapshotType(int maxPerTick) {
        this.maxPerTick = maxPerTick;
    }
}
