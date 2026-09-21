/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.Random;

import net.minecraft.server.level.ServerLevel;

/** Utilities based around more complex (but common) usages of {@link Random}. */
public class RandUtil {
    /** Creates a {@link Random} instance for a specific generator, for the specified chunk, in the specified level.
     *
     * <p>Takes a {@link ServerLevel} rather than a {@code Level}: the seed lives on the server's level data now,
     * and a client-side Level has no access to it. Chunk generation is server-side anyway, so every caller of
     * this already had one.
     * 
     * @param level The server level to generate for.
     * @param chunkX The chunk X co-ord to generate for.
     * @param chunkY The chunk X co-ord to generate for.
     * @param magicNumber The magic number, specific to the generator. Each different generator that calls this should
     *            have a different number, so that different generators don't start by generating structures in the same
     *            place. It is recommended that you generate a random number once, and place it statically in the
     *            generator class (Perhaps by using <code>new SecureRandom().nextLong()</code>).
     * @return A {@link Random} instance that starts off with the same seed given the same arguments. */
    public static Random createRandomForChunk(ServerLevel level, int chunkX, int chunkY, long magicNumber) {
        long worldSeed = level.getSeed();
        return createRandomForChunk(worldSeed, chunkX, chunkY, magicNumber);
    }

    /** Creates a {@link Random} instance for a specific generator, for the specified chunk, for a given level seed
     * 
     * @param worldSeed The seed of a level to generate for.
     * @param chunkX The chunk X co-ord to generate for.
     * @param chunkY The chunk X co-ord to generate for.
     * @param magicNumber The magic number, specific to the generator. Each different generator that calls this should
     *            have a different number, so that different generators don't start by generating structures in the same
     *            place. It is recommended that you generate a random number once, and place it statically in the
     *            generator class (Perhaps by using <code>new SecureRandom().nextLong()</code>).
     * @return A {@link Random} instance that starts off with the same seed given the same arguments. */
    public static Random createRandomForChunk(long worldSeed, int chunkX, int chunkY, long magicNumber) {
        // Ensure we have the same seed for the same chunk
        // (this is similar to the code that calls IWorldGenerator.generate)
        Random worldRandom = new Random(worldSeed);
        long xSeed = worldRandom.nextLong() >> 2 + 1L;
        long zSeed = worldRandom.nextLong() >> 2 + 1L;
        long chunkSeed = (xSeed * chunkX + zSeed * chunkY) ^ worldSeed;
        // XOR our own number so that we differ from other generators
        chunkSeed ^= magicNumber;
        return new Random(chunkSeed);
    }
}
