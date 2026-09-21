/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api;

import java.util.Locale;

/**
 * BuildCraft's feature modules.
 *
 * <p>In 1.12.2 these were nine separate mods, each installable on its own, and this enum existed to answer
 * "is that one present?" -- by asking FML for a mod id, guarded by a check that loading had reached preInit,
 * because before that the answer would have been wrong.
 *
 * <p>They are one mod now. FML's {@code parent} mechanism, which held the nine together, no longer exists, so
 * the port collapses them into a single {@code buildcraft} id; the boundaries survive as packages. That makes
 * {@link #isLoaded()} constant, and the preInit guard pointless -- there is no longer a window in which the
 * answer is unknown.
 *
 * <p>The enum is kept, rather than deleted, because addons compile against it and because the names are still
 * how BuildCraft talks about its own subdivisions. The {@code loadedModules}/{@code missingModules} arrays and
 * the {@code ModelResourceLocation} helpers are not carried over: the first two are meaningless now, and the
 * third belongs to the rendering rewrite.
 */
public enum BCModules implements IBuildCraftMod {
    LIB,
    /** Base module for all of BuildCraft. */
    CORE,
    // Potentially optional modules adding more BuildCraft functionality.
    BUILDERS,
    ENERGY,
    FACTORY,
    ROBOTICS,
    SILICON,
    TRANSPORT,
    /** Optional module for compatibility with other mods. */
    COMPAT;

    public static final BCModules[] VALUES = values();

    /** The single mod id everything BuildCraft registers under. Was {@code "buildcraft" + lowerCaseName}. */
    public static final String MOD_ID = "buildcraft";

    public final String lowerCaseName = name().toLowerCase(Locale.ROOT);

    /** Slightly hacky, but it works, as this is all English. */
    public final String camelCaseName = name().charAt(0) + lowerCaseName.substring(1);

    /**
     * @return Always true. Every module ships inside the one mod now, so if this class is loaded then so is
     *         every module.
     */
    public boolean isLoaded() {
        return true;
    }

    /**
     * @return {@code buildcraft}, for every module.
     * @deprecated Each module no longer has its own mod id; use {@link #MOD_ID}.
     */
    @Override
    @Deprecated
    public String getModId() {
        return MOD_ID;
    }
}
