/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.data;

import java.util.zip.GZIPInputStream;

/**
 * Format identifiers and magic numbers for BuildCraft's NBT compressor, used by blueprints and books.
 *
 * <p>These are wire-format constants, so none of them may change value: a blueprint written by 1.12.2
 * BuildCraft has to keep loading. The class is Minecraft-free and lives in {@code :shared} for that reason --
 * the reader and writer differ per platform, but what the bytes mean does not.
 *
 * <p>The javadoc on {@link #VANILLA} used to link {@code CompressedStreamTools}; that class still exists but
 * its read signature changed (it takes an {@code NbtAccounter} rather than an {@code NBTSizeTracker}), and
 * naming it here would drag Minecraft onto this module's classpath, so it is named in prose instead.
 */
public final class NbtSquishConstants {

    /**
     * The default written NBT tag type, as produced by Minecraft's own {@code CompressedStreamTools} write/read
     * pair.
     *
     * <p>Generally more suited to smaller NBT tags, and it writes fairly quickly. Can quickly use up a lot of
     * space for larger or more complex tags, so it is recommended that you also pass it through a GZIP
     * compressor to take up much less space.
     */
    public static final int VANILLA = 0;
    public static final int VANILLA_COMPRESSED = 1;

    /**
     * BuildCraft's own NBT compressor: puts every tag type into a dictionary and then refers to the dictionary
     * for every tag written out. Gets much better space usage than {@link #VANILLA}, at the cost of time.
     */
    public static final int BUILDCRAFT_V1 = 2;
    public static final int BUILDCRAFT_V1_COMPRESSED = 3;

    public static final int BUILDCRAFT_MAGIC_1 = 0xbc;
    public static final int BUILDCRAFT_MAGIC_2 = 0xa1;
    /** The magic identifier for this type of file. First byte is BC, second is A1. */
    public static final int BUILDCRAFT_MAGIC = (BUILDCRAFT_MAGIC_1 << 8) | BUILDCRAFT_MAGIC_2;

    // GZIP uses the opposite byte order to us, so swap it around for us.
    public static final int GZIP_MAGIC_1 = GZIPInputStream.GZIP_MAGIC & 0xff;
    public static final int GZIP_MAGIC_2 = GZIPInputStream.GZIP_MAGIC >> 8;
    public static final int GZIP_MAGIC = (GZIP_MAGIC_1 << 8) | GZIP_MAGIC_2;

    // The flags used by BUILDCRAFT_V1 to check the existence of each dictionary.
    public static final int FLAG_HAS_BYTES = 1 << 0;
    public static final int FLAG_HAS_SHORTS = 1 << 1;
    public static final int FLAG_HAS_INTS = 1 << 2;
    public static final int FLAG_HAS_LONGS = 1 << 3;
    public static final int FLAG_HAS_FLOATS = 1 << 4;
    public static final int FLAG_HAS_DOUBLES = 1 << 5;
    public static final int FLAG_HAS_BYTE_ARRAYS = 1 << 6;
    public static final int FLAG_HAS_INT_ARRAYS = 1 << 7;
    public static final int FLAG_HAS_STRINGS = 1 << 8;
    public static final int FLAG_HAS_COMPLEX = 1 << 9;

    // Complex types.
    public static final int COMPLEX_COMPOUND = 0;
    public static final int COMPLEX_LIST = 1;
    public static final int COMPLEX_LIST_PACKED = 2;

    private NbtSquishConstants() {
    }
}
