/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates a method that will receive a pipe event. The method must be public and take a single parameter
 * that extends {@code PipeEvent}.
 *
 * <pre>{@code
 * @PipeEventHandler
 * public void sideCheck(PipeEventItem.SideCheck sideCheck) {
 *     // Logic omitted
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PipeEventHandler {
    /**
     * The priority the handler is given. All handlers in vanilla BuildCraft use
     * {@link PipeEventPriority#NORMAL}, so this exists for other pipe mods to fire before or after all
     * BuildCraft logic has taken place.
     */
    PipeEventPriority priority() default PipeEventPriority.NORMAL;

    /** If true then the event handler will be called even if an event has already been cancelled. */
    boolean receiveCancelled() default false;
}
