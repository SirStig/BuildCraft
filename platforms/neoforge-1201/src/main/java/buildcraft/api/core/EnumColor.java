/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

/**
 * BuildCraft's own colour list, kept only for the places that still index by its ordering.
 *
 * <p>Two parts of the 1.12.2 class did not survive the port and are not coming back here:
 *
 * <ul>
 * <li>The {@code @SideOnly(Side.CLIENT)} sprite registry ({@code registerSprites}/{@code getSprite}). Sprite
 *     handling is part of the rendering rewrite, and a client-only static on a common class is exactly the shape
 *     that {@code @SideOnly} used to paper over; there is no equivalent annotation now.</li>
 * <li>{@code getLocalizedName()}, which called the client-only {@code I18n}. Translation is resolved at the
 *     point of display now, so this exposes {@link #getTranslationKey()} and lets the caller wrap it in a
 *     {@code Component}.</li>
 * </ul>
 *
 * @deprecated Use Minecraft's {@link DyeColor} in as many places as possible.
 */
@Deprecated
public enum EnumColor implements StringRepresentable {

    BLACK(DyeColor.BLACK),
    RED(DyeColor.RED),
    GREEN(DyeColor.GREEN),
    BROWN(DyeColor.BROWN),
    BLUE(DyeColor.BLUE),
    PURPLE(DyeColor.PURPLE),
    CYAN(DyeColor.CYAN),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY),
    GRAY(DyeColor.GRAY),
    PINK(DyeColor.PINK),
    LIME(DyeColor.LIME),
    YELLOW(DyeColor.YELLOW),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE),
    MAGENTA(DyeColor.MAGENTA),
    ORANGE(DyeColor.ORANGE),
    WHITE(DyeColor.WHITE);

    public static final EnumColor[] VALUES = values();

    public static final String[] NAMES = { "Black", "Red", "Green", "Brown", "Blue", "Purple", "Cyan", "LightGray",
        "Gray", "Pink", "Lime", "Yellow", "LightBlue", "Magenta", "Orange", "White" };
    public static final int[] DARK_HEX = { 0x2D2D2D, 0xA33835, 0x394C1E, 0x5C3A24, 0x3441A2, 0x843FBF, 0x36809E,
        0x888888, 0x444444, 0xE585A0, 0x3FAA36, 0xCFC231, 0x7F9AD1, 0xFF64FF, 0xFF6A00, 0xFFFFFF };
    public static final int[] LIGHT_HEX = { 0x181414, 0xBE2B27, 0x007F0E, 0x89502D, 0x253193, 0x7e34bf, 0x299799,
        0xa0a7a7, 0x7A7A7A, 0xD97199, 0x39D52E, 0xFFD91C, 0x66AAFF, 0xD943C6, 0xEA7835, 0xe4e4e4 };

    /**
     * The vanilla dye this colour corresponds to.
     *
     * <p>This replaces the {@code DYES} array of ore dictionary names ({@code "dyeBlack"} and friends). The ore
     * dictionary is gone; dyes are matched by {@link DyeColor} or by tag now.
     */
    public final DyeColor dye;

    EnumColor(DyeColor dye) {
        this.dye = dye;
    }

    public int getDarkHex() {
        return DARK_HEX[ordinal()];
    }

    public int getLightHex() {
        return LIGHT_HEX[ordinal()];
    }

    public static EnumColor fromId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return WHITE;
        }
        return VALUES[id];
    }

    /** Replaces {@code fromDye(String)}, which looked up an ore dictionary name. */
    public static EnumColor fromDye(DyeColor dye) {
        for (EnumColor colour : VALUES) {
            if (colour.dye == dye) {
                return colour;
            }
        }
        return WHITE;
    }

    @Nullable
    public static EnumColor fromName(String name) {
        for (int id = 0; id < NAMES.length; id++) {
            if (NAMES[id].equals(name)) {
                return VALUES[id];
            }
        }
        return null;
    }

    public static EnumColor getRand(RandomSource rand) {
        return VALUES[rand.nextInt(VALUES.length)];
    }

    public EnumColor getNext() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public EnumColor getPrevious() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public EnumColor inverse() {
        return VALUES[VALUES.length - 1 - ordinal()];
    }

    /** The translation key for this colour, for the caller to resolve into a {@code Component}. */
    public String getTranslationKey() {
        return "color." + name().replace("_", ".").toLowerCase(Locale.ENGLISH);
    }

    public String getBasicTag() {
        return name().replace("_", ".").toLowerCase(Locale.ENGLISH);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return NAMES[ordinal()];
    }
}
