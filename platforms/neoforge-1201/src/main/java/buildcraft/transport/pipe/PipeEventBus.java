/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import buildcraft.api.transport.pipe.PipeEvent;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventPriority;

/**
 * Dispatches {@link PipeEvent}s to every {@code @PipeEventHandler}-annotated method registered with it, exactly
 * the way 1.12.2's own {@code PipeEventBus} did -- {@code PipeBehaviourCobble#modifySpeed} and every other
 * {@code @PipeEventHandler} static method in this port relies on this exact dispatch mechanism working, not a
 * simplified stand-in for it.
 *
 * <p>A close, direct port. {@code MethodHandle}/{@code Modifier}/{@code Parameter} are plain
 * {@code java.lang.reflect}/{@code java.lang.invoke} API, completely untouched by the Minecraft-side port, so the
 * only thing that changed is dropping 1.12.2's {@code BCDebugging.shouldDebugLog}-gated state-validation calls
 * around {@link #fireEvent} -- no debug-flag system is ported anywhere else in this port either, and those checks
 * were opt-in diagnostics, not behaviour, so removing the gate they hung on removes nothing functional.
 *
 * <p>Byte-identical on both platforms: nothing in this class touches a Minecraft type at all.
 */
public class PipeEventBus {

    private static final Map<Class<?>, List<Handler>> allHandlers = new HashMap<>();

    private final List<LocalHandler> currentHandlers = new ArrayList<>();

    private static List<LocalHandler> getAndBindHandlers(Object obj) {
        Class<?> cls = obj instanceof Class ? (Class<?>) obj : obj.getClass();

        List<Handler> handlerList = getHandlers(cls);
        List<LocalHandler> list = new ArrayList<>();
        for (Handler handler : handlerList) {
            LocalHandler bound = handler.bindTo(obj);
            /* The handler will be null if a class was registered but the method was not static */
            if (bound != null) {
                list.add(bound);
            }
        }
        return list;
    }

    private static List<Handler> getHandlers(Class<?> cls) {
        List<Handler> cached = allHandlers.get(cls);
        if (cached != null) {
            return cached;
        }

        List<Handler> list = new ArrayList<>();
        Class<?> superCls = cls.getSuperclass();
        if (superCls != null) {
            list.addAll(getHandlers(superCls));
        }
        for (Method m : cls.getDeclaredMethods()) {
            PipeEventHandler annot = m.getAnnotation(PipeEventHandler.class);
            if (annot == null) {
                continue;
            }

            Parameter[] params = m.getParameters();
            if (params.length != 1) {
                throw new IllegalStateException(
                    "Cannot annotate " + m + " with @PipeEventHandler as it had an incorrect number of parameters ("
                        + Arrays.toString(params) + ")"
                );
            }
            Parameter p = params[0];
            if (!PipeEvent.class.isAssignableFrom(p.getType())) {
                throw new IllegalStateException(
                    "Cannot annotate " + m + " with @PipeEventHandler as it did not take a pipe event! (" + p.getType() + ")"
                );
            }

            MethodHandle mh;
            try {
                mh = MethodHandles.publicLookup().unreflect(m);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                    "Cannot annotate " + m + " with @PipeEventHandler as there was a problem with it!", e
                );
            }
            boolean isStatic = Modifier.isStatic(m.getModifiers());
            String methodName = m.toString();
            list.add(new Handler(annot.priority(), annot.receiveCancelled(), isStatic, methodName, mh, p.getType()));
        }

        allHandlers.put(cls, list);
        return list;
    }

    public void registerHandler(Object obj) {
        if (obj == null) {
            return;
        }
        currentHandlers.addAll(getAndBindHandlers(obj));
        Collections.sort(currentHandlers);
    }

    public void unregisterHandler(Object obj) {
        if (obj == null) {
            return;
        }
        currentHandlers.removeIf(next -> next.target == obj);
    }

    /** Sends this event to all of the registered handlers.
     *
     * @return True if at least one event handler was called, false if none were. */
    public boolean fireEvent(PipeEvent event) {
        boolean handled = false;
        for (LocalHandler handler : currentHandlers) {
            handled |= handler.handleEvent(event);
        }
        return handled;
    }

    private static final class Handler {
        final PipeEventPriority priority;
        final boolean receiveCanceled, isStatic;
        final String methodName;
        final MethodHandle handle;
        final Class<?> eventClassHandled;

        Handler(
            PipeEventPriority priority, boolean receiveCanceled, boolean isStatic, String methodName,
            MethodHandle handle, Class<?> eventClassHandled
        ) {
            this.priority = priority;
            this.receiveCanceled = receiveCanceled;
            this.isStatic = isStatic;
            this.methodName = methodName;
            this.handle = handle;
            this.eventClassHandled = eventClassHandled;
        }

        LocalHandler bindTo(Object obj) {
            // If its not a static method then we cannot pass the class to the handler, so we won't bind it
            if (!isStatic && obj instanceof Class<?>) {
                return null;
            }
            MethodHandle bound = isStatic ? handle : handle.bindTo(obj);
            return new LocalHandler(priority, receiveCanceled, obj, methodName, eventClassHandled, bound);
        }
    }

    private static final class LocalHandler implements Comparable<LocalHandler> {
        final PipeEventPriority priority;
        final boolean receiveCanceled;
        final Object target;
        final String methodName;
        final Class<?> classHandled;
        final MethodHandle handle;

        LocalHandler(
            PipeEventPriority priority, boolean receiveCanceled, Object target, String methodName,
            Class<?> classHandled, MethodHandle handle
        ) {
            this.priority = priority;
            this.receiveCanceled = receiveCanceled;
            this.target = target;
            this.methodName = methodName;
            this.classHandled = classHandled;
            this.handle = handle;
        }

        boolean handleEvent(PipeEvent event) {
            if (!receiveCanceled && event.isCanceled()) {
                return false;
            }

            if (classHandled.isAssignableFrom(event.getClass())) {
                try {
                    handle.invoke(event);
                    return true;
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            }
            return false;
        }

        @Override
        public int compareTo(LocalHandler o) {
            return priority.compareTo(o.priority);
        }
    }
}
