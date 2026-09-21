/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;

import com.google.common.base.Splitter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Partial port of 1.12.2's {@code StringUtilBC}. {@code formatStringForWhite}/{@code formatStringForBlack} (and
 * the private {@code formatStringImpl} helper behind them) and {@code compareBasicReadable} are not ported --
 * all three need {@code ColourUtil.getTextFormatForWhite}/{@code getTextFormatForBlack}/{@code
 * stripAllFormatCodes}, and {@code ColourUtil} is not ported yet (it in turn needs {@code BCLibConfig},
 * {@code LocaleUtil} and {@code SpecialColourFontRenderer}, none of which are ported either). Everything else in
 * this class has no dependency beyond vanilla.
 */
public final class StringUtilBC {

    public static final Splitter newLineSplitter = Splitter.on("\\n");

    private static final DecimalFormat displayDecimalFormat = new DecimalFormat("#####0.00");

    /** Deactivate constructor */
    private StringUtilBC() {}

    public static List<String> splitIntoLines(String string) {
        return newLineSplitter.splitToList(string.replaceAll("\\n", "\n"));
    }

    public static String blockPosToString(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public static String blockPosAsSizeToString(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return pos.getX() + "x" + pos.getY() + "x" + pos.getZ();
    }

    /** 1.12.2 read {@code fluid.amount} and {@code fluid.getFluid().getName()} directly; the amount is
     * {@link FluidStack#getAmount()} now, and a fluid's id comes from the registry rather than from the fluid
     * itself. */
    public static String fluidToString(FluidStack fluid) {
        if (fluid == null) {
            return "null";
        }
        return fluid.getAmount() + "mb " + BuiltInRegistries.FLUID.getKey(fluid.getFluid());
    }

    // Displaying objects
    public static String vec3ToDispString(Vec3 vec) {
        if (vec == null) {
            return "null";
        }
        return displayDecimalFormat.format(vec.x) + ", " + displayDecimalFormat.format(vec.y) + ", "
            + displayDecimalFormat.format(vec.z);
    }

    public static String vec3ToDispString(Vec3i vec) {
        if (vec == null) {
            return "null";
        }
        return vec.getX() + ", " + vec.getY() + ", " + vec.getZ();
    }

    /** A direct replacement for {@link String#format(String, Object...)} which returns a descriptive string if the
     * given format is invalid. */
    public static String formatSafe(String format, Object... args) {
        if (true || Boolean.getBoolean("buildcraft.lib.misc.StringUtilBC.debugFormatSafe")) {
            return formatDirect(format, args);
        }
        try {
            return String.format(format, args);
        } catch (IllegalFormatException error) {
            return "![" + error.getMessage() + "]! for '" + format + "' " + Arrays.toString(args);
        }
    }

    /** A direct replacement for {@link String#format(String, Object...)} which includes the full format argument if
     * {@link String#format(String, Object...)} throws an {@link IllegalFormatException} */
    public static String formatDirect(String format, Object... args) {
        try {
            return String.format(format, args);
        } catch (IllegalFormatException error) {
            throw new IllegalArgumentException("Invalid format: '" + format + "'", error);
        }
    }
}
