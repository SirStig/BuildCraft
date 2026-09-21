/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

/**
 * A subset of colours from {@link DyeColor} that are suitable for use in LEDs or wires (or equivalent). In other
 * words they must all be uniquely identifiable from both their lit and dark colours, and not look similar to other
 * colours.
 *
 * <p>Port note: {@code EnumDyeColor.SILVER} was renamed to {@link DyeColor#LIGHT_GRAY} in 1.13. It is the same
 * colour, so the grouping below is unchanged.
 */
public enum EnumWireColour implements StringRepresentable {
    // We disallow all variants of grey and black, as they don't make much sense relative to LEDs.
    // In theory we could keep black OR dark grey LEDs (as they are very distant) but it's simpler not to.
    WHITE(DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK),
    ORANGE(DyeColor.ORANGE),
    // MAGENTA -> PINK
    LIGHT_BLUE(DyeColor.LIGHT_BLUE, DyeColor.CYAN),
    YELLOW(DyeColor.YELLOW),
    LIME(DyeColor.LIME),
    PINK(DyeColor.PINK, DyeColor.MAGENTA),
    // GRAY -> WHITE
    // LIGHT_GRAY -> WHITE
    // CYAN -> LIGHT_BLUE
    PURPLE(DyeColor.PURPLE),
    BLUE(DyeColor.BLUE),
    BROWN(DyeColor.BROWN),
    GREEN(DyeColor.GREEN),
    RED(DyeColor.RED),
    // BLACK -> WHITE
    ;

    public static final EnumWireColour[] VALUES = values();

    private static final EnumMap<DyeColor, EnumWireColour> DYE_TO_WIRE;

    static {
        DYE_TO_WIRE = new EnumMap<>(DyeColor.class);
        for (EnumWireColour wire : VALUES) {
            for (DyeColor dye : wire.similarBasedColours) {
                EnumWireColour prev = DYE_TO_WIRE.put(dye, wire);
                if (prev != null) {
                    throw new Error(wire + " attempted to override " + prev + " for the dye " + dye + "!");
                }
            }
        }

        for (DyeColor dye : DyeColor.values()) {
            if (DYE_TO_WIRE.get(dye) == null) {
                throw new Error(dye + " isn't mapped to a wire colour!");
            }
        }
    }

    /** The primary Minecraft colour that this is based on. */
    public final DyeColor primaryIdenticalColour;

    /**
     * A set of similar Minecraft colours that this single colour is based on. Always includes
     * {@link #primaryIdenticalColour}.
     */
    public final Set<DyeColor> similarBasedColours;

    EnumWireColour(DyeColor primary, DyeColor... secondary) {
        this.primaryIdenticalColour = primary;
        this.similarBasedColours = EnumSet.of(primary, secondary);
    }

    public static EnumWireColour convertToWire(DyeColor dye) {
        return DYE_TO_WIRE.get(dye);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
