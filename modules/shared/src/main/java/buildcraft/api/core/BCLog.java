/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license,
 * which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * BuildCraft's shared logger.
 *
 * <p>Ported from the BuildCraftAPI submodule. Log4j is used rather than slf4j because it is what
 * Minecraft itself logs through on both target versions, so BuildCraft's output lands in the same
 * place it always has.
 *
 * <p>The deprecated {@code getVersion()} accessor from the 1.12.2 version is dropped: it only
 * forwarded to {@code BuildCraftAPI.getVersion()}, which was populated by FML's annotation
 * scanning and has no equivalent on modern loaders.
 */
public final class BCLog {

    public static final Logger logger = LogManager.getLogger("BuildCraft");

    private BCLog() {}

    public static void logErrorAPI(Throwable error, Class<?> classFile) {
        StringBuilder msg = new StringBuilder("API error! Please update your mods. Error: ");
        msg.append(error);
        StackTraceElement[] stackTrace = error.getStackTrace();
        if (stackTrace.length > 0) {
            msg.append(", ").append(stackTrace[0]);
        }
        logger.error(msg.toString());

        if (classFile != null) {
            msg.append("API error: ").append(classFile.getSimpleName())
               .append(" is loaded from ")
               .append(classFile.getProtectionDomain().getCodeSource().getLocation());
            logger.error(msg.toString());
        }
    }
}
