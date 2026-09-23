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
 * The {@code Template} half of the original {@code ItemSnapshot} -- see {@link ItemBlueprint}'s own javadoc for
 * how this port replaces that class and drops its shared hash-registry indirection. A blank stack carries no
 * data; a captured {@link buildcraft.builders.snapshot.Template} is serialized directly onto the stack's own NBT
 * via {@link buildcraft.builders.snapshot.Template#writeToStack}/{@code #readFromStack}.
 *
 * <p>Purely a marker/data-carrier type this round -- nothing yet filters a slot on {@code instanceof
 * ItemTemplate} the way {@link buildcraft.builders.tile.TileArchitectTable}/{@code TileBuilder} do for
 * {@link ItemBlueprint}, since neither tile has a template-flavoured mode wired up yet (see {@code Template}'s
 * own javadoc for that followup).
 */
public class ItemTemplate extends Item {
    public ItemTemplate(Properties properties) {
        super(properties);
    }
}
