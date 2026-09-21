/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.block;

import net.minecraft.world.level.block.Block;

/**
 * 1.12.2's {@code BlockDecoration} was a single block carrying a six-value {@code EnumDecoratedBlock}
 * blockstate property (destroy/blueprint/template/paper/leather/laser_back) -- block metadata/variant
 * subtyping is gone on this target too, so this becomes six real blocks, one per variant, the same way
 * {@link BlockSpringWater} splits 1.12.2's {@code BlockSpring}.
 *
 * <p>Unlike {@code BlockSpringWater}, no per-variant behaviour survives the split: {@code getSubBlocks} and
 * {@code damageDropped} existed only to enumerate/report metadata, which no longer exists, and
 * {@code getLightValue(state, world, pos)} -- the one piece of real per-variant behaviour, sourced from
 * {@code EnumDecoratedBlock#lightValue} -- moves to {@link Properties#lightLevel} on modern
 * {@code BlockBehaviour}, supplied per instance at construction in {@code BCCoreRegistries} rather than looked
 * up from blockstate. That leaves this class with an empty body: it exists as a named type (rather than six
 * bare {@link Block} instances) purely so other BuildCraft code has something to {@code instanceof} against,
 * matching how {@code BlockSpringWater} is kept as a real class even where 1.12.2's own class held little logic.
 */
public class BlockDecoration extends Block {
    public BlockDecoration(Properties properties) {
        super(properties);
    }
}
