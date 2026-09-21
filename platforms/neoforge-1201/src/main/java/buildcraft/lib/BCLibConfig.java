/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team This Source Code Form is subject to the terms of the Mozilla
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

import buildcraft.api.mj.IMjToRfStatus;
import buildcraft.api.mj.MjRfConversion;
import buildcraft.lib.chunkload.IChunkLoadingTile;
import buildcraft.lib.chunkload.IChunkLoadingTile.LoadType;

/**
 * Settings for lib, read by BuildCraft's other modules.
 *
 * <p>As the 1.12.2 javadoc put it: "in order to keep lib as close to being just a library mod as possible, these
 * are not set by a config file, but instead by BC Core." That is still true -- this remains a plain bag of
 * public static fields with defaults, not a NeoForge {@code ModConfigSpec}. Whatever eventually reads a real
 * config file (the `core` module, not yet ported) is responsible for overwriting these at startup and calling
 * {@link #refreshConfigs()}.
 *
 * <p>The 1.12.2 version mixed genuine settings in with rendering ones -- sprite-swap toggles
 * ({@code useSwappableSprites}, {@code enableAnimatedSprites}), a {@code guiConfigFile} that nothing here reads,
 * and item-render-rotation ({@link RenderRotation}, kept because it is pure {@link Direction} logic, not a GL
 * call). The purely client-rendering fields are dropped for now and will come back with the rendering rewrite,
 * rather than being carried as dead weight through it.
 */
public final class BCLibConfig {

    private BCLibConfig() {
    }

    /**
     * If true then fluid stacks will localize with something similar to "4B Water" rather than "4000mB of
     * Water".
     */
    public static boolean useBucketsStatic = true;

    /** If true then fluid stacks will localize with something similar to "4B/s" rather than "4000mB/t". */
    public static boolean useBucketsFlow = true;

    /**
     * If true then fluid stacks and MJ will be localized with longer names, for example "1.2 Buckets per
     * second" rather than "60mB/t".
     */
    public static boolean useLongLocalizedName = false;

    /** The lifespan (in seconds) that spawned items will have, when dropped by a quarry or builder, etc. */
    public static int itemLifespan = 60;

    /** The maximum number of results to display in the guide contents page for the search bar. */
    public static int maxGuideSearchCount = 1200;

    public static boolean guideShowDetail = false;

    /** The maximum number of items that the guide book will index. */
    public static int guideItemSearchLimit = 10_000;

    public static TimeGap displayTimeGap = TimeGap.SECONDS;

    /** If true then item-in-pipe rendering will use the facing parameter to rotate the item. */
    public static RenderRotation rotateTravelingItems = RenderRotation.ENABLED;

    public static ChunkLoaderType chunkLoadingType = ChunkLoaderType.AUTO;

    public static ChunkLoaderLevel chunkLoadingLevel = ChunkLoaderLevel.SELF_TILES;

    /**
     * MJ to RF conversion. Requires {@link #powerMode} to be either {@link PowerMode#MJ_AUTOCONVERT_RF} or
     * {@link PowerMode#DISPLAY_RF}.
     */
    public static MjRfConversion mjRfConversion = MjRfConversion.createDefault();

    public static PowerMode powerMode = PowerMode.MJ_ONLY;

    public static final List<Runnable> configChangeListeners = new ArrayList<>();

    /** Resets cached values across various lib classes that rely on these config options. */
    public static void refreshConfigs() {
        for (Runnable r : configChangeListeners) {
            r.run();
        }
    }

    public enum TimeGap {
        TICKS(1),
        SECONDS(20);

        private final int ticksInGap;

        TimeGap(int ticksInGap) {
            this.ticksInGap = ticksInGap;
        }

        public int convertTicksToGap(int ticks) {
            return ticks * ticksInGap;
        }

        public long convertTicksToGap(long ticks) {
            return ticks * ticksInGap;
        }

        public float convertTicksToGap(float ticks) {
            return ticks * ticksInGap;
        }

        public double convertTicksToGap(double ticks) {
            return ticks * ticksInGap;
        }
    }

    public enum RenderRotation {
        DISABLED {
            @Override
            public Direction changeFacing(Direction dir) {
                return Direction.EAST;
            }
        },
        HORIZONTALS_ONLY {
            @Override
            public Direction changeFacing(Direction dir) {
                return dir.getAxis() == Axis.Y ? Direction.EAST : dir;
            }
        },
        ENABLED {
            @Override
            public Direction changeFacing(Direction dir) {
                return dir;
            }
        };

        public abstract Direction changeFacing(Direction dir);
    }

    public enum ChunkLoaderType {
        /** Automatic chunkloading is ENABLED. */
        ON,

        /**
         * Automatic chunkloading is ENABLED when using the integrated server (singleplayer + LAN), and
         * DISABLED when using a dedicated server. Currently NOT implemented.
         */
        AUTO,

        /** Automatic chunkloading is DISABLED. Even for strict tiles (like the quarry). */
        OFF
    }

    public enum ChunkLoaderLevel {
        /** No automatic chunkloading is done. */
        NONE,

        /** Block entities implementing {@link IChunkLoadingTile} are loaded, provided they return
         * {@link LoadType#HARD}. */
        STRICT_TILES,

        /** Block entities implementing {@link IChunkLoadingTile} are loaded, provided they don't return null. */
        SELF_TILES,

        /** Every block entity. */
        ALL_TILES;

        public boolean canLoad(LoadType loadType) {
            return switch (this) {
                case NONE -> false;
                case STRICT_TILES -> loadType == LoadType.HARD;
                case SELF_TILES, ALL_TILES -> true;
            };
        }
    }

    public enum PowerMode {
        /** MJ &lt;-&gt; RF conversion disabled, all machines require MJ exclusively to operate. */
        MJ_ONLY(false),
        /** MJ &lt;-&gt; RF conversion enabled, machines accept both MJ and RF. */
        MJ_AUTOCONVERT_RF(true),
        /**
         * MJ &lt;-&gt; RF conversion enabled, machines accept both MJ and RF. Additionally machines will
         * display power amounts in RF rather than MJ.
         */
        DISPLAY_RF(true);

        final boolean autoconvert;

        PowerMode(boolean autoconvert) {
            this.autoconvert = autoconvert;
        }
    }

    /** Installed via {@link IMjToRfStatus#set} from the mod's startup, once this class's fields are live. */
    public static final class MjToRfStatus implements IMjToRfStatus {

        @Override
        public MjRfConversion getConversion() {
            return BCLibConfig.mjRfConversion;
        }

        @Override
        public boolean isAutoconvertEnabled() {
            return powerMode.autoconvert;
        }
    }
}
