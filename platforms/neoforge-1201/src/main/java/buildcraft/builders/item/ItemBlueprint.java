/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.item;

import net.minecraft.world.item.Item;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for how this replaces 1.12.2's
 * {@code ItemSnapshot} and why duplicating a filled stack duplicates the captured blueprint too (no shared
 * hash-registry indirection this round). Identical on both targets: the item itself carries no NBT-shape
 * assumptions, only {@link buildcraft.builders.snapshot.Blueprint#writeToStack}/{@code #readFromStack} do, and
 * those already branch on the two targets' different item-data APIs internally.
 */
public class ItemBlueprint extends Item {
    public ItemBlueprint(Properties properties) {
        super(properties);
    }
}
