/** Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license,
 * which should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.mj;

import io.netty.buffer.ByteBuf;

/**
 * Provides a basic implementation of a simple battery.
 *
 * <p>Ported from 1.12.2. The arithmetic is unchanged, including the deliberate quirk that {@link #addPower} accepts
 * power past the battery's capacity and always reports no excess -- overfilling is corrected later by shedding power,
 * which is what produces the visible spark effect on an over-supplied machine.
 *
 * <p>Two things changed so this class could be shared by both platforms:
 *
 * <ul>
 * <li>{@code tick(World, BlockPos)} and {@code losePower} are replaced by {@link #shedExcessPower()}, which does the
 *     accounting and reports how much was lost. Each platform's {@code MjEffects} calls it every tick and renders the
 *     effect, since spawning particles needs a {@code Level}.</li>
 * <li>{@code INBTSerializable} is gone -- the whole state is one long, so platforms read and write it through
 *     {@link #getStored()} and {@link #setStored(long)} using whichever NBT API their version has.</li>
 * </ul>
 */
public class MjBattery {
    private final long capacity;
    private long microJoules = 0;

    public MjBattery(long capacity) {
        this.capacity = capacity;
    }

    public void writeToBuffer(ByteBuf buffer) {
        buffer.writeLong(microJoules);
    }

    public void readFromBuffer(ByteBuf buffer) {
        microJoules = buffer.readLong();
    }

    public long addPower(long microJoulesToAdd, boolean simulate) {
        if (!simulate) {
            this.microJoules += microJoulesToAdd;
        }
        return 0;
    }

    /** Attempts to add power, but only if this is not already full.
     *
     * @param microJoulesToAdd The power to add.
     * @return The excess power. */
    public long addPowerChecking(long microJoulesToAdd, boolean simulate) {
        if (isFull()) {
            return microJoulesToAdd;
        } else {
            return addPower(microJoulesToAdd, simulate);
        }
    }

    public long extractAll() {
        return extractPower(0, microJoules);
    }

    /** Attempts to extract exactly the given amount of power.
     *
     * @param power The amount of power to extract.
     * @return True if the power was removed, false if not. */
    public boolean extractPower(long power) {
        return extractPower(power, power) > 0;
    }

    public long extractPower(long min, long max) {
        if (microJoules < min) return 0;
        long extracting = Math.min(microJoules, max);
        microJoules -= extracting;
        return extracting;
    }

    public boolean isFull() {
        return microJoules >= capacity;
    }

    public long getStored() {
        return microJoules;
    }

    /** Replaces the stored power outright. Intended for deserialisation; use {@link #addPower} during normal play. */
    public void setStored(long microJoules) {
        this.microJoules = microJoules;
    }

    public long getCapacity() {
        return capacity;
    }

    /**
     * Sheds power held above twice this battery's capacity, a 32nd of the excess per call.
     *
     * <p>Replaces the 1.12.2 {@code tick(World, Vec3d)}/{@code losePower} pair. Callers should invoke this once per
     * tick and, if the result is non-zero, show a power-loss effect at the machine's position.
     *
     * @return The amount of power lost, or 0 if the battery is not over-filled.
     */
    public long shedExcessPower() {
        if (microJoules > capacity * 2) {
            long diff = microJoules - capacity * 2;
            long lost = ceilDivide(diff, 32);
            microJoules -= lost;
            return lost;
        }
        return 0;
    }

    private static long ceilDivide(long val, long by) {
        return (val / by) + (val % by == 0 ? 0 : 1);
    }

    public String getDebugString() {
        return MjAPI.formatMj(microJoules) + " / " + MjAPI.formatMj(capacity) + " MJ";
    }
}
