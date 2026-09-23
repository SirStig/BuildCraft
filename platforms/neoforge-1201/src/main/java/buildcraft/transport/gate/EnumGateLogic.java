/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.gate;

import java.util.Locale;

/** Byte-identical port of 1.12.2's {@code buildcraft.silicon.gate.EnumGateLogic} -- no Minecraft types involved. */
public enum EnumGateLogic {
    AND,
    OR;

    public static final EnumGateLogic[] VALUES = values();

    public final String tag = name().toLowerCase(Locale.ROOT);

    public static EnumGateLogic getByOrdinal(int ord) {
        if (ord < 0 || ord >= VALUES.length) {
            return EnumGateLogic.AND;
        }
        return VALUES[ord];
    }
}
