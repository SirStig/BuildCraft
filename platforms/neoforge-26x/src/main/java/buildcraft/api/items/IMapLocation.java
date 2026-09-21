/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.items;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IBox;
import buildcraft.api.core.IZone;

/** The map location item: a marked point, area, path or zone the player can hand to a machine. */
public interface IMapLocation extends INamedItem {

    /**
     * What a given map location stack is marking.
     *
     * <p>1.12.2 stored this in the stack's damage value, and the enum carried a {@code meta} field plus static
     * {@code getFromStack}/{@code setToStack} helpers that read and wrote it. Item damage is no longer a subtype
     * discriminator -- it is durability, and on 26.x stack state lives in data components -- so those helpers are
     * gone and {@link IMapLocation#getMapType} / {@link IMapLocation#setMapType} take their place. The item owns
     * its own storage, which is the right place for the decision: it knows whether it wants a component, and the
     * API no longer has to.
     *
     * <p>The ordinal is kept stable because the values are written to disk by whatever storage the item picks.
     */
    enum MapLocationType implements StringRepresentable {
        CLEAN,
        SPOT,
        AREA,
        PATH,
        ZONE,
        /** Like {@link #PATH} but repeats around in a loop. */
        PATH_REPEATING;

        public static final MapLocationType[] VALUES = values();

        private final String serializedName = name().toLowerCase(Locale.ROOT);

        public static MapLocationType fromIndex(int index) {
            if (index < 0 || index >= VALUES.length) {
                return CLEAN;
            }
            return VALUES[index];
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /** @return What this stack is marking. */
    MapLocationType getMapType(@NotNull ItemStack stack);

    /** Sets what this stack is marking. */
    void setMapType(@NotNull ItemStack stack, MapLocationType type);

    /**
     * Usable for {@link MapLocationType#SPOT}.
     *
     * @return The point representing the map location, or null if this stack does not hold one.
     */
    @Nullable
    BlockPos getPoint(@NotNull ItemStack stack);

    /**
     * Usable for {@link MapLocationType#SPOT} and {@link MapLocationType#AREA}.
     *
     * @return The box representing the map location, or null if this stack does not hold one.
     */
    @Nullable
    IBox getBox(@NotNull ItemStack stack);

    /**
     * Usable for {@link MapLocationType#SPOT}, {@link MapLocationType#AREA} and {@link MapLocationType#ZONE}.
     * {@link MapLocationType#PATH} needs to be handled separately.
     *
     * @return An {@link IZone} representing the map location -- also an {@link IBox} for SPOT and AREA -- or null.
     */
    @Nullable
    IZone getZone(@NotNull ItemStack stack);

    /**
     * Usable for {@link MapLocationType#SPOT} and {@link MapLocationType#PATH}.
     *
     * @return The path the map location stores, or an empty list if it holds none.
     */
    List<BlockPos> getPath(@NotNull ItemStack stack);

    /**
     * Usable for {@link MapLocationType#SPOT} only.
     *
     * @return The side of the spot, or null if this stack does not hold one.
     */
    @Nullable
    Direction getPointSide(@NotNull ItemStack stack);
}
