/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

import java.text.DecimalFormat;

/**
 * Constants and formatting for BuildCraft's power system, Minecraft Joules.
 *
 * <p>Ported from 1.12.2, where this class also held the five Forge {@code Capability} instances. Those are inherently
 * platform-specific -- NeoForge replaced {@code Capability} with {@code BlockCapability}, which is keyed by a
 * {@code ResourceLocation}/{@code Identifier} and registered per block entity type -- so they now live in each
 * platform's own {@code MjCapabilities}. The effect manager moved out for the same reason: its methods take a
 * {@code Level} and a {@code Vec3}.
 *
 * <p>What is left here is version-independent and shared by every target.
 */
public class MjAPI {

    /** A single minecraft joule, in micro joules (the power system base unit) */
    public static final long ONE_MINECRAFT_JOULE = 1_000_000L;

    /** The same as {@link #ONE_MINECRAFT_JOULE}, but a shorter field name */
    public static final long MJ = ONE_MINECRAFT_JOULE;

    /** The decimal format used to display values of MJ to the player. */
    public static final DecimalFormat MJ_DISPLAY_FORMAT = new DecimalFormat("#,##0.##");

    private MjAPI() {}

    /** Formats a given MJ value to a player-oriented string. Note that this does not append "MJ" to the value. */
    public static String formatMj(long microMj) {
        return MJ_DISPLAY_FORMAT.format(microMj / (double) MJ);
    }
}
