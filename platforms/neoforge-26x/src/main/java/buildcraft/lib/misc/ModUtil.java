/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import org.jetbrains.annotations.Nullable;

import net.neoforged.fml.ModList;

/** {@code Loader}/{@code ModContainer} (FML's pre-1.13 mod-discovery API) is gone; {@link ModList} is its
 * successor, and a mod's display name now lives on {@code IModInfo} rather than directly on the container. */
public class ModUtil {

    @Nullable
    public static String getNameOfMod(String domain) {
        return ModList.get().getModContainerById(domain)
            .map(mod -> mod.getModInfo().getDisplayName())
            .orElse(null);
    }
}
