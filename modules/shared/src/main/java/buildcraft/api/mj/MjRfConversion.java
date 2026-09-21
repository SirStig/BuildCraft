/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

/**
 * The exchange rate between BuildCraft's MJ and the Forge/NeoForge energy unit (historically RF).
 *
 * <p>Pure arithmetic over {@link MjAPI}'s constants, so it is shared rather than duplicated per platform.
 */
public final class MjRfConversion {

    /** Maximum MJ per RF, or a minimum of 5 RF to make 1 MJ. */
    public static final long MAX_MJ_PER_RF = MjAPI.MJ / 5;

    /** Minimum MJ per RF, or a maximum of 10,000 RF to make 1 MJ. */
    public static final long MIN_MJ_PER_RF = MjAPI.MJ / 10_000;

    /** Default MJ per RF. */
    public static final long DEFAULT_MJ_PER_RF = MjAPI.MJ / 10;

    /** Micro MJ per 1 int RF. */
    public final long mjPerRf;

    /**
     * True if {@link #mjPerRf} was forced to {@link #DEFAULT_MJ_PER_RF} because the value passed in was out of
     * bounds. This distinguishes that from it being explicitly set to the default.
     */
    public final boolean usingDefaultValue;

    private MjRfConversion(long mjPerRf) {
        if (MIN_MJ_PER_RF <= mjPerRf && mjPerRf <= MAX_MJ_PER_RF) {
            this.usingDefaultValue = false;
            this.mjPerRf = mjPerRf;
        } else {
            this.usingDefaultValue = true;
            this.mjPerRf = DEFAULT_MJ_PER_RF;
        }
    }

    /** @param mjPerRf Micro Minecraft Joules per 1 RF. */
    public static MjRfConversion createRaw(long mjPerRf) {
        return new MjRfConversion(mjPerRf);
    }

    /** @param configMjPerRf {@link MjAPI#MJ} per RF. This is rounded to the nearest 100 micro MJ. */
    public static MjRfConversion createParsed(double configMjPerRf) {
        long value = Math.round(configMjPerRf * 10_000);
        return new MjRfConversion(value * MjAPI.MJ / 10_000);
    }

    public static MjRfConversion createDefault() {
        // -10 is always out of range, so the constructor substitutes the default.
        return new MjRfConversion(-10);
    }
}
