/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Base class for a BuildCraft block that carries a block entity -- the 1.12.2 {@code BlockBCTile_Neptune}.
 *
 * <p>In 1.12.2 a block declared a tile by overriding {@code hasTileEntity}/{@code createTileEntity}. That pair is
 * gone; a block now implements {@link EntityBlock}, and the block entity type is registered separately and bound to
 * the blocks it is valid for. Subclasses supply {@code newBlockEntity} and, if they need one, {@code getTicker}.
 */
public abstract class BlockBCTile extends Block implements EntityBlock {

    protected BlockBCTile(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
