/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The border block a {@code buildcraft.builders.tile.TileQuarry} places around its claimed area while it works.
 * Mirrors the 26.x class of the same name -- see that one's javadoc for the connected-strut rendering this drops
 * (a plain static cube instead) and for why it has no {@code BlockItem}/loot table.
 */
public class BlockFrame extends Block {
    public BlockFrame(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
