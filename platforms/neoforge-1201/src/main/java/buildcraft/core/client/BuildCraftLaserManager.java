/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.client;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import buildcraft.api.core.BuildCraftAPI;

import buildcraft.lib.client.render.laser.LaserData_BC8.LaserRow;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserSide;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;

/** Every laser type BuildCraft draws, with 1.12.2's exact sprite-row layouts.
 *
 * <p>1.12.2 read each sprite off a {@code BCCoreSprites} {@code SpriteHolder} (the unported
 * {@code SpriteHolderRegistry} system); the sprites are plain block-atlas ids now, listed in {@link #SPRITES} and
 * stitched into the block atlas by {@code assets/minecraft/atlases/blocks.json} -- see PORTING.md for why that file,
 * not a registration event, is the right mechanism on both targets. The PNGs themselves are byte-for-byte copies of
 * {@code buildcraft_resources/assets/buildcraftcore/textures/lasers/}, moved from the old {@code buildcraftcore}
 * namespace to the port's single {@code buildcraft} one.
 *
 * <p>Nothing here references a client-only class (a {@link LaserType} is only {@link ResourceLocation}s and numbers), so
 * common code may name these constants; only turning them into geometry is client-side. Only the marker types have
 * a consumer so far; the stripes/power types are declared now, with their sprites stitched, for the builder,
 * quarry, mining-well and silicon-laser renderers still to come. */
public final class BuildCraftLaserManager {

    public static final ResourceLocation SPRITE_MARKER_VOLUME_CONNECTED = sprite("marker_volume_connected");
    public static final ResourceLocation SPRITE_MARKER_VOLUME_POSSIBLE = sprite("marker_volume_possible");
    public static final ResourceLocation SPRITE_MARKER_VOLUME_SIGNAL = sprite("marker_volume_signal");
    public static final ResourceLocation SPRITE_MARKER_PATH_CONNECTED = sprite("marker_path_connected");
    public static final ResourceLocation SPRITE_MARKER_PATH_POSSIBLE = sprite("marker_path_possible");
    public static final ResourceLocation SPRITE_MARKER_DEFAULT_POSSIBLE = sprite("marker_default_possible");
    public static final ResourceLocation SPRITE_STRIPES_READ = sprite("stripes_read");
    public static final ResourceLocation SPRITE_STRIPES_WRITE = sprite("stripes_write");
    public static final ResourceLocation SPRITE_STRIPES_WRITE_DIRECTION = sprite("stripes_write_direction");
    public static final ResourceLocation SPRITE_POWER_LOW = sprite("power_low");
    public static final ResourceLocation SPRITE_POWER_MED = sprite("power_med");
    public static final ResourceLocation SPRITE_POWER_HIGH = sprite("power_high");
    public static final ResourceLocation SPRITE_POWER_FULL = sprite("power_full");

    /** Every laser sprite id, for the post-stitch presence check. */
    public static final List<ResourceLocation> SPRITES = List.of(
        SPRITE_MARKER_VOLUME_CONNECTED, SPRITE_MARKER_VOLUME_POSSIBLE, SPRITE_MARKER_VOLUME_SIGNAL,
        SPRITE_MARKER_PATH_CONNECTED, SPRITE_MARKER_PATH_POSSIBLE, SPRITE_MARKER_DEFAULT_POSSIBLE,
        SPRITE_STRIPES_READ, SPRITE_STRIPES_WRITE, SPRITE_STRIPES_WRITE_DIRECTION,
        SPRITE_POWER_LOW, SPRITE_POWER_MED, SPRITE_POWER_HIGH, SPRITE_POWER_FULL
    );

    public static final LaserType MARKER_VOLUME_CONNECTED;
    public static final LaserType MARKER_VOLUME_POSSIBLE;
    public static final LaserType MARKER_VOLUME_SIGNAL;

    public static final LaserType MARKER_PATH_CONNECTED;
    public static final LaserType MARKER_PATH_POSSIBLE;

    public static final LaserType MARKER_DEFAULT_POSSIBLE;

    public static final LaserType STRIPES_READ;
    public static final LaserType STRIPES_WRITE;
    public static final LaserType STRIPES_WRITE_DIRECTION;

    public static final LaserType POWER_LOW;// red
    public static final LaserType POWER_MED;// yellow
    public static final LaserType POWER_HIGH;// green
    public static final LaserType POWER_FULL;// blue
    public static final LaserType[] POWERS;

    static {
        {
            ResourceLocation sprite = SPRITE_MARKER_VOLUME_CONNECTED;
            LaserRow capStart = new LaserRow(sprite, 0, 0, 2, 2);
            LaserRow start = new LaserRow(sprite, 0, 0, 16, 2);
            LaserRow[] middle = { //
                    new LaserRow(sprite, 0, 2, 16, 4), new LaserRow(sprite, 0, 4, 16, 6), new LaserRow(sprite, 0, 6, 16, 8), //
                    new LaserRow(sprite, 0, 8, 16, 10), new LaserRow(sprite, 0, 10, 16, 12), new LaserRow(sprite, 0, 12, 16, 14) //
            };
            LaserRow end = new LaserRow(sprite, 0, 14, 16, 16);
            LaserRow capEnd = new LaserRow(sprite, 14, 14, 16, 16);
            MARKER_VOLUME_CONNECTED = new LaserType(capStart, start, middle, end, capEnd);
        }
        {
            ResourceLocation sprite = SPRITE_MARKER_PATH_CONNECTED;
            LaserRow capStart = new LaserRow(sprite, 0, 0, 3, 3);
            LaserRow start = new LaserRow(sprite, 0, 0, 16, 3);
            LaserRow[] middle = { //
                    new LaserRow(sprite, 0, 4, 16, 7, LaserSide.TOP, LaserSide.BOTTOM),//
                    new LaserRow(sprite, 0, 8, 16, 11, LaserSide.LEFT, LaserSide.RIGHT) //
            };
            LaserRow end = new LaserRow(sprite, 0, 12, 16, 15);
            LaserRow capEnd = new LaserRow(sprite, 13, 12, 16, 15);
            MARKER_PATH_CONNECTED = new LaserType(capStart, start, middle, end, capEnd);
        }
        {
            ResourceLocation sprite = SPRITE_MARKER_VOLUME_POSSIBLE;
            LaserRow capStart = new LaserRow(sprite, 0, 0, 1, 1);
            LaserRow start = new LaserRow(sprite, 0, 0, 16, 1);
            LaserRow[] middle = { //
                    new LaserRow(sprite, 0, 1, 16, 2), new LaserRow(sprite, 0, 2, 16, 3),//
                    new LaserRow(sprite, 0, 3, 16, 4), new LaserRow(sprite, 0, 4, 16, 5),//
                    new LaserRow(sprite, 0, 5, 16, 6), new LaserRow(sprite, 0, 6, 16, 7),//
                    new LaserRow(sprite, 0, 7, 16, 8), new LaserRow(sprite, 0, 8, 16, 9),//
                    new LaserRow(sprite, 0, 9, 16, 10), new LaserRow(sprite, 0, 10, 16, 11),//
                    new LaserRow(sprite, 0, 11, 16, 12), new LaserRow(sprite, 0, 12, 16, 13),//
                    new LaserRow(sprite, 0, 13, 16, 14), new LaserRow(sprite, 0, 14, 16, 15),//
            };
            LaserRow end = new LaserRow(sprite, 0, 15, 16, 16);
            LaserRow capEnd = new LaserRow(sprite, 15, 15, 16, 16);
            MARKER_VOLUME_POSSIBLE = new LaserType(capStart, start, middle, end, capEnd);
        }
        MARKER_VOLUME_SIGNAL = new LaserType(MARKER_VOLUME_CONNECTED, SPRITE_MARKER_VOLUME_SIGNAL);
        MARKER_PATH_POSSIBLE = new LaserType(MARKER_VOLUME_POSSIBLE, SPRITE_MARKER_PATH_POSSIBLE);
        MARKER_DEFAULT_POSSIBLE = new LaserType(MARKER_VOLUME_POSSIBLE, SPRITE_MARKER_DEFAULT_POSSIBLE);

        STRIPES_READ = new LaserType(MARKER_VOLUME_CONNECTED, SPRITE_STRIPES_READ);
        STRIPES_WRITE = new LaserType(MARKER_VOLUME_CONNECTED, SPRITE_STRIPES_WRITE);
        STRIPES_WRITE_DIRECTION = new LaserType(MARKER_PATH_CONNECTED, SPRITE_STRIPES_WRITE_DIRECTION);

        POWER_LOW = new LaserType(MARKER_VOLUME_POSSIBLE, SPRITE_POWER_LOW);
        POWER_MED = new LaserType(MARKER_VOLUME_POSSIBLE, SPRITE_POWER_MED);
        POWER_HIGH = new LaserType(MARKER_VOLUME_POSSIBLE, SPRITE_POWER_HIGH);
        POWER_FULL = new LaserType(MARKER_VOLUME_POSSIBLE, SPRITE_POWER_FULL);
        POWERS = new LaserType[] {POWER_LOW, POWER_MED, POWER_HIGH, POWER_FULL};
    }

    private BuildCraftLaserManager() {}

    private static ResourceLocation sprite(String name) {
        return BuildCraftAPI.nameToResourceId("lasers/" + name);
    }
}
