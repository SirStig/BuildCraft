/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** {@code ForgeRegistries.BLOCKS}/{@code .ITEMS} are {@link BuiltInRegistries#BLOCK}/{@link BuiltInRegistries#ITEM}
 * now, and {@code IForgeRegistry#containsValue} has no direct replacement -- {@code Registry#getId} is the modern
 * "is this value actually registered" check, returning -1 for a value the registry has never seen. */
public class RegistryUtil {
    public static boolean isRegistered(Block block) {
        return BuiltInRegistries.BLOCK.getId(block) >= 0;
    }

    public static boolean isRegistered(Item item) {
        return BuiltInRegistries.ITEM.getId(item) >= 0;
    }
}
