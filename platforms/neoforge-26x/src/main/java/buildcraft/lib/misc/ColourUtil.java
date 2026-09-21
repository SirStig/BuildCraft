/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

/** Colour math plus {@link DyeColor}/{@link ChatFormatting} display helpers.
 *
 * <p>{@link BCLibConfig#useColouredLabels}/{@code useHighContrastLabelColours} do not exist on this target yet
 * -- {@code buildcraft.lib.BCLibConfig} is outside this file's scope, and those two fields are the only thing
 * standing between this class and full parity. Both are hard-coded to their 1.12.2 defaults ({@code true} and
 * {@code false} respectively) below until the config module lands and can grow them back.
 *
 * <p>26.x's {@link ChatFormatting} lost {@code isColor()}, {@code getColor()}, {@code getChar()} and
 * {@code getName()} -- it is now nothing but the escape sequence and {@code stripFormatting} (confirmed against
 * the real source: text styling moved fully to {@code Style}/{@code TextColor}, and the legacy codes are kept
 * only for raw-string compatibility). The 16 colour codes are still ordinals 0-15 in the same
 * BLACK..WHITE order 1.12.2's {@code TextFormatting} used, so {@link #isColourCode} reproduces {@code isColor()}
 * from the ordinal rather than a method call. 1.20.1 keeps the real method -- see that copy of this file. */
public class ColourUtil {
    public static final char MINECRAFT_FORMAT_CHAR;
    public static final String COLOUR_SPECIAL_START;

    /** {@code BCLibConfig.useColouredLabels}'s 1.12.2 default. See the class javadoc. */
    private static final boolean USE_COLOURED_LABELS = true;
    /** {@code BCLibConfig.useHighContrastLabelColours}'s 1.12.2 default. See the class javadoc. */
    private static final boolean USE_HIGH_CONTRAST_LABEL_COLOURS = false;

    public static final Function<ChatFormatting, ChatFormatting> getTextFormatForBlack =
        ColourUtil::getTextFormatForBlack;
    public static final Function<ChatFormatting, ChatFormatting> getTextFormatForWhite =
        ColourUtil::getTextFormatForWhite;

    public static final DyeColor[] COLOURS = DyeColor.values();

    /** These three parallel arrays are indexed by {@link DyeColor#getId()} (equivalently, its ordinal), which
     * runs WHITE(0)..BLACK(15) -- the reverse of 1.12.2's {@code EnumDyeColor#getDyeDamage()}, which ran
     * BLACK(0)..WHITE(15). The literal values are the same 1.12.2 data, reordered to match. */
    private static final String[] NAMES = { //
        "White", "Orange", "Magenta", "LightBlue", //
        "Yellow", "Lime", "Pink", "Gray", //
        "LightGray", "Cyan", "Purple", "Blue", //
        "Brown", "Green", "Red", "Black"//
    };
    private static final int[] DARK_HEX = { //
        0xFFFFFF, 0xFF6A00, 0xFF64FF, 0x7F9AD1, //
        0xCFC231, 0x3FAA36, 0xE585A0, 0x444444, //
        0x888888, 0x36809E, 0x843FBF, 0x3441A2, //
        0x5C3A24, 0x394C1E, 0xA33835, 0x2D2D2D //
    };
    private static final int[] LIGHT_HEX = { //
        0xe4e4e4, 0xEA7835, 0xD943C6, 0x66AAFF, //
        0xFFD91C, 0x39D52E, 0xD97199, 0x7A7A7A, //
        0xa0a7a7, 0x299799, 0x7e34bf, 0x253193, //
        0x89502D, 0x007F0E, 0xBE2B27, 0x181414 //
    };
    private static final String[] DYES = new String[16];
    private static final Map<String, DyeColor> nameToColourMap;
    private static final int[] FACE_TO_COLOUR;

    private static final ChatFormatting[] FORMATTING_VALUES = ChatFormatting.values();

    private static final ChatFormatting[] COLOUR_TO_FORMAT = new ChatFormatting[16];
    private static final ChatFormatting[] REPLACE_FOR_WHITE = new ChatFormatting[16];
    private static final ChatFormatting[] REPLACE_FOR_BLACK = new ChatFormatting[16];
    private static final ChatFormatting[] REPLACE_FOR_WHITE_HIGH_CONTRAST = new ChatFormatting[16];
    private static final ChatFormatting[] REPLACE_FOR_BLACK_HIGH_CONTRAST = new ChatFormatting[16];
    private static final ChatFormatting[] FACE_TO_FORMAT = new ChatFormatting[6];

    private static final Pattern ALL_FORMAT_MATCHER = Pattern.compile("(?i)§[0-9A-Za-z]");

    static {
        MINECRAFT_FORMAT_CHAR = '§';
        COLOUR_SPECIAL_START = MINECRAFT_FORMAT_CHAR + "z" + MINECRAFT_FORMAT_CHAR;
        for (int i = 0; i < 16; i++) {
            DYES[i] = "dye" + NAMES[i];
            REPLACE_FOR_WHITE[i] = REPLACE_FOR_WHITE_HIGH_CONTRAST[i] = FORMATTING_VALUES[i];
            REPLACE_FOR_BLACK[i] = REPLACE_FOR_BLACK_HIGH_CONTRAST[i] = FORMATTING_VALUES[i];
        }

        replaceColourForWhite(ChatFormatting.WHITE, ChatFormatting.GRAY);
        replaceColourForWhite(ChatFormatting.YELLOW, ChatFormatting.GOLD);
        replaceColourForWhite(ChatFormatting.AQUA, ChatFormatting.BLUE);
        replaceColourForWhite(ChatFormatting.GREEN, ChatFormatting.DARK_GREEN);

        replaceColourForBlack(ChatFormatting.BLACK, ChatFormatting.GRAY);
        replaceColourForBlack(ChatFormatting.DARK_GRAY, ChatFormatting.GRAY);
        replaceColourForBlack(ChatFormatting.DARK_BLUE, ChatFormatting.BLUE, ChatFormatting.AQUA);
        replaceColourForBlack(ChatFormatting.BLUE, ChatFormatting.BLUE, ChatFormatting.AQUA);
        replaceColourForBlack(ChatFormatting.DARK_PURPLE, ChatFormatting.LIGHT_PURPLE);
        replaceColourForBlack(ChatFormatting.DARK_RED, ChatFormatting.RED);
        replaceColourForBlack(ChatFormatting.DARK_GREEN, ChatFormatting.GREEN);

        COLOUR_TO_FORMAT[DyeColor.BLACK.ordinal()] = ChatFormatting.BLACK;
        COLOUR_TO_FORMAT[DyeColor.GRAY.ordinal()] = ChatFormatting.DARK_GRAY;
        COLOUR_TO_FORMAT[DyeColor.LIGHT_GRAY.ordinal()] = ChatFormatting.GRAY;
        COLOUR_TO_FORMAT[DyeColor.WHITE.ordinal()] = ChatFormatting.WHITE;

        COLOUR_TO_FORMAT[DyeColor.RED.ordinal()] = ChatFormatting.DARK_RED;
        COLOUR_TO_FORMAT[DyeColor.BLUE.ordinal()] = ChatFormatting.BLUE;
        COLOUR_TO_FORMAT[DyeColor.CYAN.ordinal()] = ChatFormatting.DARK_AQUA;
        COLOUR_TO_FORMAT[DyeColor.LIGHT_BLUE.ordinal()] = ChatFormatting.AQUA;

        COLOUR_TO_FORMAT[DyeColor.GREEN.ordinal()] = ChatFormatting.DARK_GREEN;
        COLOUR_TO_FORMAT[DyeColor.LIME.ordinal()] = ChatFormatting.GREEN;
        COLOUR_TO_FORMAT[DyeColor.BROWN.ordinal()] = ChatFormatting.GOLD;
        COLOUR_TO_FORMAT[DyeColor.YELLOW.ordinal()] = ChatFormatting.YELLOW;

        COLOUR_TO_FORMAT[DyeColor.ORANGE.ordinal()] = ChatFormatting.GOLD;
        COLOUR_TO_FORMAT[DyeColor.PURPLE.ordinal()] = ChatFormatting.DARK_PURPLE;
        COLOUR_TO_FORMAT[DyeColor.MAGENTA.ordinal()] = ChatFormatting.LIGHT_PURPLE;
        COLOUR_TO_FORMAT[DyeColor.PINK.ordinal()] = ChatFormatting.LIGHT_PURPLE;

        FACE_TO_FORMAT[Direction.UP.ordinal()] = ChatFormatting.WHITE;
        FACE_TO_FORMAT[Direction.DOWN.ordinal()] = ChatFormatting.BLACK;
        FACE_TO_FORMAT[Direction.NORTH.ordinal()] = ChatFormatting.RED;
        FACE_TO_FORMAT[Direction.SOUTH.ordinal()] = ChatFormatting.BLUE;
        FACE_TO_FORMAT[Direction.EAST.ordinal()] = ChatFormatting.YELLOW;
        FACE_TO_FORMAT[Direction.WEST.ordinal()] = ChatFormatting.GREEN;

        ImmutableMap.Builder<String, DyeColor> builder = ImmutableMap.builder();
        for (DyeColor c : COLOURS) {
            builder.put(c.getName(), c);
        }
        nameToColourMap = builder.build();

        FACE_TO_COLOUR = new int[6];
        FACE_TO_COLOUR[Direction.DOWN.ordinal()] = 0xFF_33_33_33;
        FACE_TO_COLOUR[Direction.UP.ordinal()] = 0xFF_CC_CC_CC;
    }

    private static void replaceColourForBlack(ChatFormatting colour, ChatFormatting with) {
        replaceColourForBlack(colour, with, with);
    }

    private static void replaceColourForBlack(ChatFormatting colour, ChatFormatting normal,
        ChatFormatting highContrast) {
        REPLACE_FOR_BLACK[colour.ordinal()] = normal;
        REPLACE_FOR_BLACK_HIGH_CONTRAST[colour.ordinal()] = highContrast;
    }

    private static void replaceColourForWhite(ChatFormatting colour, ChatFormatting with) {
        replaceColourForWhite(colour, with, with);
    }

    private static void replaceColourForWhite(ChatFormatting colour, ChatFormatting normal,
        ChatFormatting highContrast) {
        REPLACE_FOR_WHITE[colour.ordinal()] = normal;
        REPLACE_FOR_WHITE_HIGH_CONTRAST[colour.ordinal()] = highContrast;
    }

    @Nullable
    public static DyeColor parseColourOrNull(String string) {
        return nameToColourMap.get(string);
    }

    public static String getDyeName(DyeColor colour) {
        return DYES[colour.getId()];
    }

    public static String getName(DyeColor colour) {
        return NAMES[colour.getId()];
    }

    public static int getDarkHex(DyeColor colour) {
        return DARK_HEX[colour.getId()];
    }

    public static int getLightHex(DyeColor colour) {
        return LIGHT_HEX[colour.getId()];
    }

    public static int getColourForSide(Direction face) {
        return FACE_TO_COLOUR[face.ordinal()];
    }

    public static String[] getNameArray() {
        return Arrays.copyOf(NAMES, NAMES.length);
    }

    /** Returns a string formatted for use in a tooltip (or anything else with a black background). If
     * coloured labels are enabled then this will prefix the string with an appropriate {@link ChatFormatting}
     * colour, and postfix with {@link ChatFormatting#RESET}. See the class javadoc for why that toggle is
     * currently a fixed constant rather than a config read. */
    public static String getTextFullTooltip(DyeColor colour) {
        if (USE_COLOURED_LABELS) {
            ChatFormatting formatColour = convertColourToTextFormat(colour);
            return formatColour.toString() + getTextFormatForBlack(formatColour) + LocaleUtil.localizeColour(colour)
                + ChatFormatting.RESET;
        } else {
            return LocaleUtil.localizeColour(colour);
        }
    }

    /** Similar to {@link #getTextFullTooltip(DyeColor)}, but outputs a string using BuildCraft's own private
     * colour-escape convention for a special-purpose font renderer able to draw the full 16-colour dye palette
     * rather than vanilla's 16 chat colours. That renderer is not ported yet (it lives under
     * {@code buildcraft.lib.client}, deferred with the rest of client rendering); this only produces the string,
     * unconsumed for now. MUST be the first string used! */
    public static String getTextFullTooltipSpecial(DyeColor colour) {
        if (colour == DyeColor.BLACK || colour == DyeColor.BLUE) {
            return getTextFullTooltip(colour);
        }
        if (USE_COLOURED_LABELS) {
            ChatFormatting formatColour = convertColourToTextFormat(colour);
            return COLOUR_SPECIAL_START + Integer.toHexString(colour.getId())//
                + getTextFormatForBlack(formatColour) + LocaleUtil.localizeColour(colour) + ChatFormatting.RESET;
        }
        return LocaleUtil.localizeColour(colour);
    }

    /** Returns a string formatted for use in a tooltip (or anything else with a black background). If
     * coloured labels are enabled then this will prefix the string with an appropriate {@link ChatFormatting}
     * colour, and postfixed with {@link ChatFormatting#RESET}. See the class javadoc for why that toggle is
     * currently a fixed constant rather than a config read. */
    public static String getTextFullTooltip(Direction face) {
        if (USE_COLOURED_LABELS) {
            ChatFormatting formatColour = convertFaceToTextFormat(face);
            return formatColour.toString() + getTextFormatForBlack(formatColour) + LocaleUtil.localizeFacing(face)
                + ChatFormatting.RESET;
        } else {
            return LocaleUtil.localizeFacing(face);
        }
    }

    /** True for the 16 colour codes (BLACK..WHITE, ordinals 0-15); false for the 6 text-formatting codes
     * (OBFUSCATED..RESET). Reproduces the {@code isColor()} method 1.20.1's {@link ChatFormatting} still has
     * and this target's does not -- see the class javadoc. */
    private static boolean isColourCode(ChatFormatting format) {
        return format.ordinal() < 16;
    }

    /** Returns a {@link ChatFormatting} colour that will display correctly on a black background, so it won't use any
     * of the darker colours (as they will be difficult to see). */
    public static ChatFormatting getTextFormatForBlack(ChatFormatting in) {
        if (isColourCode(in)) {
            if (USE_HIGH_CONTRAST_LABEL_COLOURS) {
                return REPLACE_FOR_BLACK_HIGH_CONTRAST[in.ordinal()];
            } else {
                return REPLACE_FOR_BLACK[in.ordinal()];
            }
        } else {
            return in;
        }
    }

    /** Returns a {@link ChatFormatting} colour that will display correctly on a white background, so it won't use any
     * of the lighter colours (as they will be difficult to see). */
    public static ChatFormatting getTextFormatForWhite(ChatFormatting in) {
        if (isColourCode(in)) {
            if (USE_HIGH_CONTRAST_LABEL_COLOURS) {
                return REPLACE_FOR_WHITE_HIGH_CONTRAST[in.ordinal()];
            } else {
                return REPLACE_FOR_WHITE[in.ordinal()];
            }
        } else {
            return in;
        }
    }

    /** Converts a {@link DyeColor} into an equivalent {@link ChatFormatting} for display. */
    public static ChatFormatting convertColourToTextFormat(DyeColor colour) {
        return COLOUR_TO_FORMAT[colour.ordinal()];
    }

    /** Converts a {@link Direction} into an equivalent {@link ChatFormatting} for display. */
    public static ChatFormatting convertFaceToTextFormat(Direction face) {
        return FACE_TO_FORMAT[face.ordinal()];
    }

    public static int swapArgbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = (argb >> 0) & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    public static DyeColor getNext(DyeColor colour) {
        int ord = colour.ordinal() + 1;
        return COLOURS[ord & 15];
    }

    public static DyeColor getNextOrNull(@Nullable DyeColor colour) {
        if (colour == null) {
            return COLOURS[0];
        } else if (colour == COLOURS[COLOURS.length - 1]) {
            return null;
        } else {
            return getNext(colour);
        }
    }

    public static DyeColor getPrev(DyeColor colour) {
        int ord = colour.ordinal() + 16 - 1;
        return COLOURS[ord & 15];
    }

    public static DyeColor getPrevOrNull(@Nullable DyeColor colour) {
        if (colour == null) {
            return COLOURS[COLOURS.length - 1];
        } else if (colour == COLOURS[0]) {
            return null;
        } else {
            return getPrev(colour);
        }
    }

    /** Similar to {@link ChatFormatting#stripFormatting(String)}, but also removes every special char that
     * {@link #getTextFullTooltipSpecial(DyeColor)} can add. */
    public static String stripAllFormatCodes(String string) {
        return ALL_FORMAT_MATCHER.matcher(string).replaceAll("");
    }
}
