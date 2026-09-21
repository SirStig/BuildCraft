/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

/**
 * Marker interface for laser targets. Implement it on your block.
 *
 * <p>It is used by BuildCraft lasers for optimisation purposes: a laser can reject a position by its block
 * without loading the block entity.
 */
public interface ILaserTargetBlock {
}
