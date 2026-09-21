/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Sets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.BCLog;

public final class NBTUtilBC {
    @SuppressWarnings("WeakerAccess")
    public static final CompoundTag NBT_NULL = new CompoundTag();

    private NBTUtilBC() {
    }

    public static <N extends Tag> Optional<N> toOptional(N value) {
        return value == NBTUtilBC.NBT_NULL ? Optional.empty() : Optional.of(value);
    }

    /**
     * Recursively merges {@code source} onto {@code destination}, with {@link #NBT_NULL} as a deletion marker
     * for a key that should be dropped rather than overwritten.
     *
     * <p>{@link CompoundTag#merge(CompoundTag)} exists on this target, but it is a shallow per-key overwrite
     * with no notion of a deletion sentinel, so it does not have the semantics this method needs. This is
     * ported by hand rather than delegated to it.
     */
    @Nullable
    public static Tag merge(@Nullable Tag destination, @Nullable Tag source) {
        if (source == null) {
            return null;
        }
        if (destination == null) {
            return source;
        }
        if (destination.getId() == Tag.TAG_COMPOUND && source.getId() == Tag.TAG_COMPOUND) {
            CompoundTag destCompound = (CompoundTag) destination;
            CompoundTag sourceCompound = (CompoundTag) source;
            CompoundTag result = new CompoundTag();
            Set<String> keys = Sets.union(destCompound.keySet(), sourceCompound.keySet());
            for (String key : keys) {
                if (!sourceCompound.contains(key)) {
                    result.put(key, destCompound.get(key));
                } else if (sourceCompound.get(key) != NBT_NULL) {
                    if (!destCompound.contains(key)) {
                        result.put(key, sourceCompound.get(key));
                    } else {
                        Tag merged = merge(destCompound.get(key), sourceCompound.get(key));
                        if (merged != null) {
                            result.put(key, merged);
                        }
                    }
                }
            }
            return result;
        }
        return source;
    }

    /**
     * Reads the arbitrary NBT compound BuildCraft has stashed on this stack, or an empty one if there is none.
     *
     * <p><b>The contract here is weaker than the 1.12.2 method it replaces.</b> {@code stack.getTagCompound()}
     * returned a live reference into the stack: callers routinely did
     * {@code NBTUtilBC.getItemData(stack).setInteger(...)} and the mutation stuck with no further call needed,
     * because item NBT <em>was</em> the stack's persistent state.
     *
     * <p>Item NBT does not exist on this target -- it is a data component ({@link DataComponents#CUSTOM_DATA},
     * holding a {@link CustomData}) -- and a {@link CustomData} is copy-on-read: {@link CustomData#copyTag()}
     * hands back a detached copy that mutating does nothing to the stack. Reading and writing are two
     * different operations now. Callers that used to read-then-mutate must switch to {@link #updateItemData},
     * or to a read via this method paired with an explicit {@link #setItemData}.
     *
     * <p>Nothing in this port calls this yet -- {@code buildcraft.lib.tile}, {@code .block} and {@code .item}
     * are not ported -- so there are no existing call sites to have gotten this wrong at the time of writing.
     * Audit every future caller against the paragraph above rather than assuming the old idiom still works.
     */
    public static CompoundTag getItemData(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** Replaces the stack's BuildCraft NBT compound outright. See {@link #getItemData} for why this exists as
     * a separate call rather than being implicit in a mutation. */
    public static void setItemData(@NotNull ItemStack stack, CompoundTag nbt) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }

    /** Reads, mutates and writes back the stack's BuildCraft NBT compound in one call -- the closest
     * equivalent to the 1.12.2 read-and-mutate idiom that this target's copy-on-read components allow. */
    public static void updateItemData(@NotNull ItemStack stack, Consumer<CompoundTag> action) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, action);
    }

    public static IntArrayTag writeBlockPos(BlockPos pos) {
        if (pos == null) {
            throw new NullPointerException("Cannot return a null NBTTag -- pos was null!");
        }
        return new IntArrayTag(new int[] { pos.getX(), pos.getY(), pos.getZ() });
    }

    @SuppressWarnings("unused")
    public static CompoundTag writeBlockPosAsCompound(BlockPos pos) {
        if (pos == null) {
            throw new NullPointerException("Cannot return a null NBTTag -- pos was null!");
        }
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("x", pos.getX());
        nbt.putInt("y", pos.getY());
        nbt.putInt("z", pos.getZ());
        return nbt;
    }

    @Nullable
    public static BlockPos readBlockPos(@Nullable Tag base) {
        if (base == null) {
            return null;
        }
        switch (base.getId()) {
            case Tag.TAG_INT_ARRAY: {
                int[] array = ((IntArrayTag) base).getAsIntArray();
                if (array.length == 3) {
                    return new BlockPos(array[0], array[1], array[2]);
                }
                return null;
            }
            case Tag.TAG_COMPOUND: {
                CompoundTag nbt = (CompoundTag) base;
                BlockPos pos = null;
                if (nbt.contains("i")) {
                    int i = nbt.getIntOr("i", 0);
                    int j = nbt.getIntOr("j", 0);
                    int k = nbt.getIntOr("k", 0);
                    pos = new BlockPos(i, j, k);
                } else if (nbt.contains("x")) {
                    int x = nbt.getIntOr("x", 0);
                    int y = nbt.getIntOr("y", 0);
                    int z = nbt.getIntOr("z", 0);
                    pos = new BlockPos(x, y, z);
                } else if (nbt.contains("pos")) {
                    return readBlockPos(nbt.get("pos"));
                } else {
                    BCLog.logger.warn(
                        "Attempted to read a block positions from a compound tag without the correct sub-tags! ("
                            + base + ")",
                        new Throwable()
                    );
                }
                return pos;
            }
        }
        BCLog.logger.warn("Attempted to read a block position from an invalid tag! (" + base + ")", new Throwable());
        return null;
    }

    public static ListTag writeVec3d(Vec3 vec3) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(vec3.x));
        list.add(DoubleTag.valueOf(vec3.y));
        list.add(DoubleTag.valueOf(vec3.z));
        return list;
    }

    @Nullable
    public static Vec3 readVec3d(@Nullable Tag nbt) {
        if (nbt instanceof ListTag list) {
            return readVec3d(list);
        }
        return null;
    }

    public static Vec3 readVec3d(ListTag list) {
        return new Vec3(list.getDoubleOr(0, 0), list.getDoubleOr(1, 0), list.getDoubleOr(2, 0));
    }

    private static final String NULL_ENUM_STRING = "_NULL";

    public static <E extends Enum<E>> Tag writeEnum(@Nullable E value) {
        if (value == null) {
            return StringTag.valueOf(NULL_ENUM_STRING);
        }
        return StringTag.valueOf(value.name());
    }

    @Nullable
    public static <E extends Enum<E>> E readEnum(@Nullable Tag nbt, Class<E> clazz) {
        if (nbt instanceof StringTag stringTag) {
            String value = stringTag.value();
            if (NULL_ENUM_STRING.equals(value)) {
                return null;
            }
            try {
                return Enum.valueOf(clazz, value);
            } catch (IllegalArgumentException t) {
                // In case we didn't find the constant
                BCLog.logger.warn("Tried and failed to read the value(" + value + ") from " + clazz.getSimpleName(), t);
                return null;
            }
        } else if (nbt instanceof ByteTag byteTag) {
            byte value = byteTag.byteValue();
            if (value < 0 || value >= clazz.getEnumConstants().length) {
                return null;
            } else {
                return clazz.getEnumConstants()[value];
            }
        } else if (nbt == null) {
            return null;
        } else {
            BCLog.logger.warn(new IllegalArgumentException("Tried to read an enum value when it was not a string! This is probably not good!"));
            return null;
        }
    }

    public static Tag writeDoubleArray(double[] data) {
        ListTag list = new ListTag();
        for (double d : data) {
            list.add(DoubleTag.valueOf(d));
        }
        return list;
    }

    public static double[] readDoubleArray(@Nullable Tag tag, int intendedLength) {
        double[] arr = new double[intendedLength];
        if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size() && i < intendedLength; i++) {
                arr[i] = list.getDoubleOr(i, 0);
            }
        }
        return arr;
    }

    /** Writes an {@link EnumSet} to a {@link Tag}. The returned type will either be {@link ByteTag} or
     * {@link ByteArrayTag}.
     *
     * @param clazz The class that the {@link EnumSet} is of. This is required as we have no way of getting the class
     *            from the set. */
    public static <E extends Enum<E>> Tag writeEnumSet(EnumSet<E> set, Class<E> clazz) {
        E[] constants = clazz.getEnumConstants();
        if (constants == null) throw new IllegalArgumentException("Not an enum type " + clazz);
        BitSet bitset = new BitSet();
        for (E e : constants) {
            if (set.contains(e)) {
                bitset.set(e.ordinal());
            }
        }
        byte[] bytes = bitset.toByteArray();
        if (bytes.length == 1) {
            return ByteTag.valueOf(bytes[0]);
        } else {
            return new ByteArrayTag(bytes);
        }
    }

    public static <E extends Enum<E>> EnumSet<E> readEnumSet(@Nullable Tag tag, Class<E> clazz) {
        E[] constants = clazz.getEnumConstants();
        if (constants == null) throw new IllegalArgumentException("Not an enum type " + clazz);
        byte[] bytes;
        if (tag instanceof ByteTag byteTag) {
            bytes = new byte[] { byteTag.byteValue() };
        } else if (tag instanceof ByteArrayTag byteArrayTag) {
            bytes = byteArrayTag.getAsByteArray();
        } else {
            bytes = new byte[] {};
            BCLog.logger.warn("[lib.nbt] Tried to read an enum set from " + tag);
        }
        BitSet bitset = BitSet.valueOf(bytes);
        EnumSet<E> set = EnumSet.noneOf(clazz);
        for (E e : constants) {
            if (bitset.get(e.ordinal())) {
                set.add(e);
            }
        }
        return set;
    }

    public static ListTag writeCompoundList(Stream<CompoundTag> stream) {
        ListTag list = new ListTag();
        stream.forEach(list::add);
        return list;
    }

    /** {@link ListTag#compoundStream()} does exactly this walk natively on this target, so the manual
     * index/cast loop the 1.12.2 version needed is gone. */
    public static Stream<CompoundTag> readCompoundList(@Nullable Tag list) {
        if (list == null) {
            return Stream.empty();
        }
        if (!(list instanceof ListTag listTag)) {
            throw new IllegalArgumentException();
        }
        return listTag.compoundStream();
    }

    public static ListTag writeStringList(Stream<String> stream) {
        ListTag list = new ListTag();
        stream.map(StringTag::valueOf).forEach(list::add);
        return list;
    }

    public static Stream<String> readStringList(@Nullable Tag list) {
        if (list == null) {
            return Stream.empty();
        }
        if (!(list instanceof ListTag listTag)) {
            throw new IllegalArgumentException();
        }
        return IntStream.range(0, listTag.size()).mapToObj(i -> listTag.getStringOr(i, ""));
    }
}
