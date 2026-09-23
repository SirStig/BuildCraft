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
 * Replaces the original {@code ItemSnapshot} (which covered both {@code Template} and {@code Blueprint} snapshot
 * types, plus a "used"/"unused" pair of item variants each, backed by a global {@code GlobalSavedDataSnapshots}
 * hash registry keyed off a header/key indirection). This round only ports the blueprint half, and drops the
 * registry indirection entirely: a blank stack of this item has no data on it, and a captured
 * {@link buildcraft.builders.snapshot.Blueprint} is serialized directly onto the stack's own NBT (see
 * {@link buildcraft.builders.snapshot.Blueprint#writeToStack}/{@code #readFromStack}) rather than being looked up
 * by a hash key from a world-saved registry. This is a real behavioural difference worth knowing about: two
 * stacks of a filled blueprint item are independent copies of the data, not two references to one save-file
 * entry, so duplicating the item duplicates the blueprint too (1.12.2 prevented that by design, via the shared
 * registry). Acceptable for this round's simplified, non-shareable single-blueprint-per-item slice.
 *
 * <p>Purely a marker/data-carrier type -- {@link buildcraft.builders.tile.TileArchitectTable} and
 * {@link buildcraft.builders.tile.TileBuilder} both filter their blueprint slot on {@code instanceof
 * ItemBlueprint}, exactly like {@code ItemSnapshot} was filtered on before it.
 */
public class ItemBlueprint extends Item {
    public ItemBlueprint(Properties properties) {
        super(properties);
    }
}
