/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** These used to be ore dictionary names (structural change 9 in PORTING.md: {@code OreDictionary.registerOre}
 * became a tag JSON). BuildCraft's own gear tags already exist at {@code data/c/tags/item/gears/<material>.json}
 * ({@code buildcraft:gear_wood} and friends), so the wood/stone/iron/gold/diamond constants below are the
 * {@link TagKey} equivalent of the old ore names, unchanged in what they mean, just spelled differently.
 *
 * <p>{@code GLASS_COLOURLESS} is dropped rather than guessed at: no {@code c:} tag for it exists yet (the block
 * it described, the factory tank, has not been ported), and inventing a tag id here without a JSON file behind
 * it would just be a broken reference. Whoever ports {@code buildcraft.factory} should add it back once the
 * tag exists. */
public class OredictionaryNames {
    public static final TagKey<Item> GEAR_WOOD = tag("gears/wooden");
    public static final TagKey<Item> GEAR_STONE = tag("gears/stone");
    public static final TagKey<Item> GEAR_IRON = tag("gears/iron");
    public static final TagKey<Item> GEAR_GOLD = tag("gears/gold");
    public static final TagKey<Item> GEAR_DIAMOND = tag("gears/diamond");

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }
}
