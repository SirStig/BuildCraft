/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
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
