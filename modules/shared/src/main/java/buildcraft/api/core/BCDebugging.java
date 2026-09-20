/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.core;

import java.util.Locale;

/**
 * Provides a way to quickly enable or disable certain debug conditions via VM arguments, or by whether the game is
 * running in a development environment.
 *
 * <p>Ported from the 1.12.2 API. Two changes were needed to make this version-independent:
 *
 * <ul>
 * <li>Dev-environment detection no longer reflects on {@code World.getTileEntity} to spot
 *     deobfuscated names. Each platform tells us instead, via {@link #setDevEnvironment(boolean)},
 *     using whatever its loader exposes.</li>
 * <li>The status is resolved lazily rather than in a static initialiser, so a platform can set the
 *     dev flag during mod construction before any BuildCraft class reads a debug option.</li>
 * </ul>
 */
public class BCDebugging {

    public enum DebugStatus {
        NONE,
        ENABLE,
        LOGGING_ONLY,
        ALL
    }

    enum DebugLevel {
        LOG,
        COMPLEX;

        final String lowerCaseName = name().toLowerCase(Locale.ROOT);
    }

    /** Set by the platform before any debug option is read; null means "not told yet". */
    private static volatile Boolean devEnvironment;

    private static volatile DebugStatus debugStatus;

    /**
     * Tells BuildCraft whether the game is running from a development environment. Platforms call this during
     * mod construction -- on NeoForge, from {@code !FMLLoader.isProduction()}.
     */
    public static void setDevEnvironment(boolean dev) {
        devEnvironment = dev;
    }

    private static DebugStatus status() {
        DebugStatus status = debugStatus;
        if (status == null) {
            synchronized (BCDebugging.class) {
                status = debugStatus;
                if (status == null) {
                    status = debugStatus = resolveStatus();
                }
            }
        }
        return status;
    }

    private static DebugStatus resolveStatus() {
        // Debugging is on in the dev environment and off for normal players. A VM argument
        // ("-Dbuildcraft.debug=...") overrides that:
        // - "enable"  turn debugging on even outside a dev environment
        // - "disable" turn ALL debugging off, and stay quiet even in a dev environment
        // - "log"     major debug options only: registry setup, API usage, and so on
        // - "all"     everything. Lots of spam. Not recommended.
        String value = System.getProperty("buildcraft.debug");
        DebugStatus status;
        if ("enable".equals(value)) {
            status = DebugStatus.ENABLE;
        } else if ("all".equals(value)) {
            status = DebugStatus.ALL;
        } else if ("disable".equals(value)) {
            status = DebugStatus.NONE;
        } else if ("log".equals(value)) {
            status = DebugStatus.LOGGING_ONLY;
        } else if (Boolean.TRUE.equals(devEnvironment)) {
            status = DebugStatus.ENABLE;
        } else {
            // Most likely a built jar -- don't spam people with info they probably don't need.
            status = DebugStatus.NONE;
        }

        if (status == DebugStatus.ALL) {
            BCLog.logger.info("[debugger] Debugging automatically enabled for ALL of buildcraft. Prepare for log spam.");
        } else if (status == DebugStatus.LOGGING_ONLY) {
            BCLog.logger.info("[debugger] Debugging automatically enabled for some non-spammy parts of buildcraft.");
        } else if (status == DebugStatus.ENABLE) {
            BCLog.logger.info("[debugger] Debugging not automatically enabled for all of buildcraft. Logging all possible debug options.");
            BCLog.logger.info("              To enable it for only logging messages add \"-Dbuildcraft.debug=log\" to your launch VM arguments");
            BCLog.logger.info("              To enable it for ALL debugging \"-Dbuildcraft.debug=all\" to your launch VM arguments");
            BCLog.logger.info("              To remove this message and all future ones add \"-Dbuildcraft.debug=disable\" to your launch VM arguments");
        }
        return status;
    }

    private static boolean isAllOn(DebugLevel type) {
        DebugStatus status = status();
        return switch (type) {
            case COMPLEX -> status == DebugStatus.ALL;
            case LOG -> status == DebugStatus.ALL || status == DebugStatus.LOGGING_ONLY;
        };
    }

    public static boolean shouldDebugComplex(String string) {
        return shouldDebug(string, DebugLevel.COMPLEX);
    }

    public static boolean shouldDebugLog(String string) {
        return shouldDebug(string, DebugLevel.LOG);
    }

    private static boolean shouldDebug(String option, DebugLevel type) {
        String prop = getProp(option);
        String actual = System.getProperty(prop);
        if ("false".equals(actual)) {
            BCLog.logger.info("[debugger] Debugging manually disabled for \"" + option + "\" (" + type + ").");
            return false;
        } else if ("true".equals(actual)) {
            BCLog.logger.info("[debugger] Debugging enabled for \"" + option + "\" (" + type + ").");
            return true;
        }
        if (isAllOn(type)) {
            BCLog.logger.info("[debugger] Debugging automatically enabled for \"" + option + "\" (" + type + ").");
            return true;
        }
        if ("complex".equals(actual) || type.lowerCaseName.equals(actual)) {
            BCLog.logger.info("[debugger] Debugging enabled for \"" + option + "\" (" + type + ").");
            return true;
        } else if (status() != DebugStatus.NONE) {
            BCLog.logger.info("[debugger] To enable debugging for " + option + " add the option \"-D" + prop
                + "=true\" to your launch config as a VM argument (" + type + ").");
        }
        return false;
    }

    private static String getProp(String string) {
        return "buildcraft." + string + ".debug";
    }
}
