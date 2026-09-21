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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import buildcraft.api.core.EnumPipePart;

import buildcraft.lib.misc.InventoryUtil;

/**
 * Collects a block entity's named item handlers, and which ones (if any) are reachable from each face.
 *
 * <p>Keeps 1.12.2's shape almost unchanged, matching {@code buildcraft.api.mj.MjCapabilityHelper}'s 1.20.1
 * port: this target still has {@link ICapabilityProvider}, so a tile can hold one instance of this class and
 * delegate {@code getCapability} to it directly, exactly as before. Contrast the 26.x port of this same file,
 * where {@code ICapabilityProvider} does not exist and this class no longer implements a capability interface
 * at all.
 *
 * <p>The one substantive change is the capability token: {@code CapUtil.CAP_ITEMS} (1.12.2's own registered
 * token) has not been ported yet, since {@code buildcraft.lib.misc.CapUtil} itself hasn't been -- this is the
 * same gap that made 26.x's inventory layer drop {@code ItemTransactorHelper}. Forge's own
 * {@link ForgeCapabilities#ITEM_HANDLER} is the equivalent built-in token and needs no port of that class to
 * use, so this is wired to that instead. {@link LazyOptional} wraps the result now (1.12.2 returned the plain
 * handler cast, pre-{@code LazyOptional} Forge); each wrapper's combined handler is wrapped in
 * {@code LazyOptional.of(...)} once, the same pattern {@code MjCapabilityHelper} already established.
 */
public class ItemHandlerManager implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public enum EnumAccess {
        /** An {@link IItemHandler} that shouldn't be accessible by external sources. */
        NONE,
        /** Same as {@link #NONE}, but the contents of this inventory won't be dropped when the block is removed.
         * Additionally the items will be considered "free", and so items can be duplicated into these slots */
        PHANTOM,
        INSERT,
        EXTRACT,
        /** Full interaction is allowed. */
        BOTH
    }

    public final StackChangeCallback callback;
    private final List<IItemHandlerModifiable> handlersToDrop = new ArrayList<>();
    private final Map<EnumPipePart, Wrapper> wrappers = new EnumMap<>(EnumPipePart.class);
    private final Map<String, INBTSerializable<CompoundTag>> handlers = new LinkedHashMap<>();

    public ItemHandlerManager(StackChangeCallback defaultCallback) {
        this.callback = defaultCallback;
        for (EnumPipePart part : EnumPipePart.VALUES) {
            wrappers.put(part, new Wrapper());
        }
    }

    public <T extends INBTSerializable<CompoundTag> & IItemHandlerModifiable> T addInvHandler(String key, T handler,
        EnumAccess access, EnumPipePart... parts) {
        if (parts == null) {
            parts = new EnumPipePart[0];
        }
        IItemHandlerModifiable external = handler;
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
        ItemHandlerSimple handler = new ItemHandlerSimple(size, callback);
        return addInvHandler(key, handler, access, parts);
    }

    public ItemHandlerSimple addInvHandler(String key, int size, StackInsertionChecker checker, EnumAccess access,
        EnumPipePart... parts) {
        ItemHandlerSimple handler = new ItemHandlerSimple(size, callback);
        handler.setChecker(checker);
        return addInvHandler(key, handler, access, parts);
    }

    /** @param maxStackSize Replaces 1.12.2's {@code StackInsertionFunction} parameter -- see
     * {@link ItemHandlerSimple}'s class javadoc for why a plain capacity limit covers the same ground. */
    public ItemHandlerSimple addInvHandler(String key, int size, int maxStackSize, EnumAccess access, EnumPipePart... parts) {
        ItemHandlerSimple handler = new ItemHandlerSimple(size, callback);
        handler.setMaxStackSize(maxStackSize);
        return addInvHandler(key, handler, access, parts);
    }

    public ItemHandlerSimple addInvHandler(String key, int size, StackInsertionChecker checker, int maxStackSize,
        EnumAccess access, EnumPipePart... parts) {
        ItemHandlerSimple handler = new ItemHandlerSimple(size, checker, callback);
        handler.setMaxStackSize(maxStackSize);
        return addInvHandler(key, handler, access, parts);
    }

    public void addDrops(NonNullList<ItemStack> toDrop) {
        for (IItemHandlerModifiable itemHandler : handlersToDrop) {
            InventoryUtil.addAll(itemHandler, toDrop);
        }
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction facing) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            Wrapper wrapper = wrappers.get(EnumPipePart.fromFacing(facing));
            return wrapper.combined.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        for (Map.Entry<String, INBTSerializable<CompoundTag>> entry : handlers.entrySet()) {
            nbt.put(entry.getKey(), entry.getValue().serializeNBT());
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        for (Map.Entry<String, INBTSerializable<CompoundTag>> entry : handlers.entrySet()) {
            String key = entry.getKey();
            if (nbt.contains(key)) {
                entry.getValue().deserializeNBT(nbt.getCompound(key));
            }
        }
    }

    private static class Wrapper {
        private final List<IItemHandlerModifiable> handlers = new ArrayList<>();
        private LazyOptional<IItemHandlerModifiable> combined = LazyOptional.empty();

        void genWrapper() {
            IItemHandlerModifiable result;
            if (this.handlers.size() == 1) {
                // No need to wrap it
                result = this.handlers.get(0);
            } else {
                IItemHandlerModifiable[] arr = this.handlers.toArray(new IItemHandlerModifiable[0]);
                result = new CombinedItemHandlerWrapper(arr);
            }
            combined = LazyOptional.of(() -> result);
        }
    }
}
