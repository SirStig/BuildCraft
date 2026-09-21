/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import org.jetbrains.annotations.Nullable;

/**
 * The base class for all pipe events.
 *
 * <p>Some event classes can be cancelled with {@link #cancel()}, but only if {@link #canBeCancelled} is true.
 * Refer to individual classes for whether they can be cancelled and what cancelling does.
 *
 * <p>This is BuildCraft's own event bus, not the loader's, so it is unaffected by the {@code @Cancelable} to
 * {@code ICancellableEvent} change in {@code buildcraft.api.events}.
 */
public abstract class PipeEvent {

    public final boolean canBeCancelled;
    public final IPipeHolder holder;

    private boolean canceled = false;

    public PipeEvent(IPipeHolder holder) {
        this.canBeCancelled = false;
        this.holder = holder;
    }

    /** @deprecated Cancellation is going to be removed at some point in the future. */
    @Deprecated
    protected PipeEvent(boolean canBeCancelled, IPipeHolder holder) {
        this.canBeCancelled = canBeCancelled;
        this.holder = holder;
    }

    public void cancel() {
        if (canBeCancelled) {
            canceled = true;
        }
    }

    public boolean isCanceled() {
        return canceled;
    }

    /**
     * Called after every event handler has received this pipe event, to pick up simple mistakes when
     * implementing pipe event handlers.
     *
     * @return Null if there are no state errors, or a message describing what is wrong, which may be
     *         incomplete.
     */
    @Nullable
    public String checkStateForErrors() {
        if (canceled && !canBeCancelled) {
            return "Somehow cancelled an event that isn't marked as such!";
        }
        return null;
    }
}
