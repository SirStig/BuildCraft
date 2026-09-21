/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

/**
 * Whether BuildCraft machines should expose themselves as Forge energy handlers, and at what rate.
 *
 * <p>1.12.2 resolved this by asking {@code BCModules.LIB.isLoaded()} and, if so, reflecting
 * {@code Class.forName("buildcraft.lib.BCLibConfig$MjToRfStatus").newInstance()}. That existed because the API
 * shipped as a standalone jar that could be present without BuildCraft itself, and because the eight mod ids
 * could be installed independently. Neither is true now -- there is a single {@code buildcraft} mod id, and the
 * api package is compiled into it -- so the reflection is replaced by {@link #set}, called by the config during
 * startup.
 *
 * <p>Until something calls {@link #set}, the default reports conversion off at the default rate, which is what
 * the 1.12.2 fallback did when lib was absent.
 */
public interface IMjToRfStatus {

    MjRfConversion getConversion();

    boolean isAutoconvertEnabled();

    static IMjToRfStatus get() {
        return Holder.current;
    }

    /** Installs the real implementation. Called by BuildCraft's config; other mods should not call it. */
    static void set(IMjToRfStatus status) {
        Holder.current = status;
    }

    /** Holds the mutable current status without exposing a settable static on the interface itself. */
    final class Holder {
        private static final IMjToRfStatus DEFAULT = new IMjToRfStatus() {
            private final MjRfConversion conversion = MjRfConversion.createDefault();

            @Override
            public MjRfConversion getConversion() {
                return conversion;
            }

            @Override
            public boolean isAutoconvertEnabled() {
                return false;
            }
        };

        private static volatile IMjToRfStatus current = DEFAULT;

        private Holder() {
        }
    }
}
