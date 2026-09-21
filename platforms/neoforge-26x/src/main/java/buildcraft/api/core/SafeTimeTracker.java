/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import net.minecraft.world.level.Level;

/**
 * Provides a way of tracking time in the world, without requiring manual ticking.
 *
 * <p>{@code World.getTotalWorldTime()} is now {@code Level.getGameTime()} (inherited from {@code LevelAccessor});
 * the semantics -- monotonic ticks since world creation, unaffected by the day/night cycle being frozen -- are
 * the same, so the logic below is unchanged.
 */
public class SafeTimeTracker {

    private long lastMark = Long.MIN_VALUE;
    private long duration = -1;
    private long randomRange = 0;
    private long lastRandomDelay = 0;
    private long internalDelay = 1;

    /** Only use this if the delay time changes, say if you use this to determine when a refining should be complete. */
    public SafeTimeTracker() {
    }

    public SafeTimeTracker(long delay) {
        internalDelay = delay;
    }

    /**
     * In many situations it is a bad idea to have all objects of the same kind waiting for the exact same amount of
     * time, as that can lead to them all synchronising and doing their work on the same tick. When created with a
     * random range, the mark set on reaching the expected delay is pushed out by a random number in [0, range), so
     * the event takes between 0 and {@code range} more ticks to run.
     */
    public SafeTimeTracker(long delay, long random) {
        internalDelay = delay;
        randomRange = random;
    }

    /** Returns true if the internal delay has passed since the last time mark was called successfully. */
    public boolean markTimeIfDelay(Level level) {
        return markTimeIfDelay(level, internalDelay);
    }

    /** Returns true if the given delay has passed since the last time mark was called successfully. */
    public boolean markTimeIfDelay(Level level, long delay) {
        if (level == null) {
            return false;
        }

        long currentTime = level.getGameTime();

        if (currentTime < lastMark) {
            lastMark = currentTime;
            return false;
        } else if (lastMark + delay + lastRandomDelay <= currentTime) {
            duration = currentTime - lastMark;
            lastMark = currentTime;
            lastRandomDelay = (long) (Math.random() * randomRange);
            return true;
        } else {
            return false;
        }
    }

    public long durationOfLastDelay() {
        return duration > 0 ? duration : 0;
    }

    public void markTime(Level level) {
        lastMark = level.getGameTime();
    }
}
