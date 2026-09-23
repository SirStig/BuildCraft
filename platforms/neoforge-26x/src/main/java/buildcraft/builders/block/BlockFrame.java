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
 * Not obtainable by any recipe -- like {@code buildcraft.factory.block.BlockTube}, it is registered with
 * {@link buildcraft.lib.registry.BCRegistry#addBlock} (no {@code BlockItem}) and an empty loot table, since the
 * quarry itself is responsible for placing and clearing every frame block it ever creates
 * ({@code TileQuarry#completeAction}/{@code BlockQuarry#onRemove}).
 *
 * <p><b>Rendering scope cut:</b> 1.12.2's {@code BlockFrame} was a genuinely variable-geometry block -- a small
 * central "node" cube plus a short connector nub rendered towards every neighbouring frame/quarry block, computed
 * per-block from {@code getActualState}'s six {@code CONNECTED_MAP} properties (whose modern replacement,
 * {@code IBlockState#getActualState}, does not exist on this target at all -- see {@code BlockMarkerBase}'s own
 * javadoc for the same gap). Reproducing that would mean a full custom {@code VoxelShape}/model per connection
 * combination with no renderer in this pass to make the distinction visible anyway (see {@code TileQuarry}'s own
 * javadoc for the matching cut on the quarry block's own animated arms). This class is therefore a plain, static
 * cube -- solid to collide with and to look at, exactly the way {@code BlockTube} already simplified its own
 * non-cube 1.12.2 shape for the same reason -- not the connected lattice. It is a real, solid block in the world
 * either way; only the cosmetic per-connection geometry is missing.
 */
public class BlockFrame extends Block {
    public BlockFrame(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
