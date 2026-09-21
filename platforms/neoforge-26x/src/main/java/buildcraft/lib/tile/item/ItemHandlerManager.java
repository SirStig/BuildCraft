/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import buildcraft.api.core.EnumPipePart;

/**
 * Collects a block entity's named item handlers, and which ones (if any) are reachable from each face.
 *
 * <p>1.12.2's version was itself an {@code ICapabilityProvider}: a tile held one instance and delegated
 * {@code getCapability} straight to it. There is no attach-by-provider path on this target -- capabilities are
 * registered against a {@code BlockEntityType} up front, in {@code RegisterCapabilitiesEvent} -- so, exactly as
 * {@code buildcraft.api.mj.MjCapabilityHelper} was restructured earlier in this port, this class no longer
 * implements a capability interface itself. {@link #getHandlerForFace(Direction)} is what a tile's
 * {@code RegisterCapabilitiesEvent} lookup function should call; the registration call itself belongs to
 * whichever concrete tile owns an instance of this class; none exists in the port yet.
 *
 * <p>NBT persistence moves from {@code INBTSerializable<NBTTagCompound>} to {@link ValueIOSerializable}, the
 * modern block entity serialisation hook (see PORTING.md's NBT/data-components note). Each named handler
 * already implements it -- {@link ItemHandlerSimple} inherits it from NeoForge's own
 * {@code StacksResourceHandler} -- so this only has to fan out to a {@link ValueOutput#child}/
 * {@link ValueInput#child} per key rather than build a raw NBT compound by hand.
 *
 * <p>{@code CombinedItemHandlerWrapper} is gone with {@code IItemHandler}; {@link CombinedResourceHandler} is
 * NeoForge's own generic equivalent.
 */
public class ItemHandlerManager implements ValueIOSerializable {
    public enum EnumAccess {
        /** A handler that shouldn't be accessible from outside the block entity. */
        NONE,
        /**
         * Same as {@link #NONE}, but the contents of this inventory won't be dropped when the block is removed.
         * Additionally the items are considered "free", so items can be duplicated into these slots.
         */
        PHANTOM,
        INSERT,
        EXTRACT,
        /** Full interaction is allowed. */
        BOTH
    }

    @Nullable
    public final StackChangeCallback callback;

    private final List<ItemHandlerSimple> handlersToDrop = new ArrayList<>();
    private final Map<EnumPipePart, Wrapper> wrappers = new EnumMap<>(EnumPipePart.class);
    private final Map<String, ItemHandlerSimple> handlers = new LinkedHashMap<>();

    public ItemHandlerManager(@Nullable StackChangeCallback defaultCallback) {
        this.callback = defaultCallback;
        for (EnumPipePart part : EnumPipePart.VALUES) {
            wrappers.put(part, new Wrapper());
        }
    }

    public ItemHandlerSimple addInvHandler(String key, ItemHandlerSimple handler, EnumAccess access, EnumPipePart... parts) {
        if (parts == null) {
            parts = new EnumPipePart[0];
        }
        ResourceHandler<ItemResource> external = handler;
        if (access == EnumAccess.NONE || access == EnumAccess.PHANTOM) {
            external = null;
            if (parts.length > 0) {
                throw new IllegalArgumentException(
                    "Completely useless to not allow access to multiple sides! Just don't pass any sides!");
            }
        } else if (access == EnumAccess.EXTRACT) {
            external = new WrappedItemHandlerExtract(handler);
        } else if (access == EnumAccess.INSERT) {
            external = new WrappedItemHandlerInsert(handler);
        }

        if (external != null) {
            Set<EnumPipePart> visited = EnumSet.noneOf(EnumPipePart.class);
            for (EnumPipePart part : parts) {
                if (part == null) part = EnumPipePart.CENTER;
                if (visited.add(part)) {
                    Wrapper wrapper = wrappers.get(part);
                    wrapper.handlers.add(external);
                    wrapper.genWrapper();
                }
            }
        }
        if (access != EnumAccess.PHANTOM) {
            handlersToDrop.add(handler);
        }
        handlers.put(key, handler);
        return handler;
    }

    public ItemHandlerSimple addInvHandler(String key, int size, EnumAccess access, EnumPipePart... parts) {
        return addInvHandler(key, new ItemHandlerSimple(size, callback), access, parts);
    }

    public ItemHandlerSimple addInvHandler(String key, int size, StackInsertionChecker checker, EnumAccess access, EnumPipePart... parts) {
        ItemHandlerSimple handler = new ItemHandlerSimple(size, checker, callback);
        return addInvHandler(key, handler, access, parts);
    }

    /** @param maxStackSize Replaces 1.12.2's {@code StackInsertionFunction} parameter -- see
     * {@link ItemHandlerSimple}'s class javadoc for why a plain capacity limit covers the same ground. */
    public ItemHandlerSimple addInvHandler(String key, int size, int maxStackSize, EnumAccess access, EnumPipePart... parts) {
        ItemHandlerSimple handler = new ItemHandlerSimple(size, maxStackSize);
        handler.setCallback(callback);
        return addInvHandler(key, handler, access, parts);
    }

    /** Was {@code InventoryUtil.addAll(IItemHandler, NonNullList)} in 1.12.2; inlined here since
     * {@code buildcraft.lib.misc.InventoryUtil} hasn't been ported yet and this is its only remaining use in this
     * class. */
    public void addDrops(NonNullList<ItemStack> toDrop) {
        for (ItemHandlerSimple handler : handlersToDrop) {
            for (int i = 0; i < handler.size(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    toDrop.add(stack);
                }
            }
        }
    }

    /** @return The combined handler reachable from the given face, or null if nothing is exposed there. This
     * is what a {@code RegisterCapabilitiesEvent} lookup for this tile's item capability should return. */
    @Nullable
    public ResourceHandler<ItemResource> getHandlerForFace(@Nullable Direction face) {
        return wrappers.get(EnumPipePart.fromFacing(face)).combined;
    }

    @Override
    public void serialize(ValueOutput output) {
        for (Map.Entry<String, ItemHandlerSimple> entry : handlers.entrySet()) {
            entry.getValue().serialize(output.child(entry.getKey()));
        }
    }

    @Override
    public void deserialize(ValueInput input) {
        for (Map.Entry<String, ItemHandlerSimple> entry : handlers.entrySet()) {
            input.child(entry.getKey()).ifPresent(entry.getValue()::deserialize);
        }
    }

    private static class Wrapper {
        private final List<ResourceHandler<ItemResource>> handlers = new ArrayList<>();

        @Nullable
        private ResourceHandler<ItemResource> combined;

        void genWrapper() {
            if (handlers.size() == 1) {
                // No need to wrap it.
                combined = handlers.get(0);
                return;
            }
            combined = new CombinedResourceHandler<>(handlers);
        }
    }
}
