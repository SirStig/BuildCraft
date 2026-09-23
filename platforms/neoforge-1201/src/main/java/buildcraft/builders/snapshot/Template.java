/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.BitSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.filler.IFilledTemplate;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for what this drops relative to the original
 * {@code common}/1.12.2 {@code Template} (no shared {@code Snapshot} base, no content-hash {@code Key}, no
 * {@code facing}/{@code offset}/rotation) and for the followup note on wiring a template-flavoured
 * {@code TileBuilder} build mode.
 *
 * <p>The only 1.20.1-specific difference from the 26.x file is NBT shape, exactly like this port's
 * {@link Blueprint}: {@link CompoundTag} getters return values directly here rather than {@code Optional}, and
 * {@link NBTUtilBC#getItemData} hands back a live, mutable tag rather than a detached copy, so
 * {@link #writeToStack} mutates it in place instead of going through {@code updateItemData}.
 */
public class Template {
    public BlockPos size = BlockPos.ZERO;
    public BitSet data = new BitSet();

    public int index(int x, int y, int z) {
        return ((z * size.getY()) + y) * size.getX() + x;
    }

    public int getDataSize() {
        return size.getX() * size.getY() * size.getZ();
    }

    public boolean isEmpty() {
        return getDataSize() == 0;
    }

    /** Flips every flag in the template -- ported unchanged from the original's own {@code Template#invert}. */
    public void invert() {
        data.flip(0, getDataSize());
    }

    /** Captures presence (non-air) rather than exact state for every position in the box
     * {@code [min, min + size)} -- the template equivalent of {@link Blueprint#capture}. */
    public static Template capture(Level level, BlockPos min, BlockPos size) {
        Template template = new Template();
        template.size = size;
        template.data = new BitSet(size.getX() * size.getY() * size.getZ());
        for (int z = 0; z < size.getZ(); z++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) {
                    BlockState state = level.getBlockState(min.offset(x, y, z));
                    template.data.set(template.index(x, y, z), !state.isAir());
                }
            }
        }
        return template;
    }

    /** A live view of this template's own backing {@link #data}, for the Filler's "template" pattern (or any
     * other {@link IFilledTemplate} consumer) to read and mutate directly -- exactly the original's own
     * {@code getFilledTemplate()} contract, not a snapshot copy. */
    public FilledTemplate getFilledTemplate() {
        return new FilledTemplate();
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("size", NBTUtilBC.writeBlockPos(size));
        nbt.putByteArray("data", data.toByteArray());
        return nbt;
    }

    public static Template deserializeNBT(CompoundTag nbt) {
        Template template = new Template();
        BlockPos size = NBTUtilBC.readBlockPos(nbt.get("size"));
        template.size = size == null ? BlockPos.ZERO : size;
        template.data = BitSet.valueOf(nbt.getByteArray("data"));
        return template;
    }

    @Nullable
    public static Template readFromStack(ItemStack stack) {
        CompoundTag data = NBTUtilBC.getItemData(stack).getCompound("template");
        return data.isEmpty() ? null : deserializeNBT(data);
    }

    /** 1.20.1's {@link NBTUtilBC#getItemData} hands back a live tag (the stack's own, created if absent), so --
     * unlike the 26.x version, which needs {@code updateItemData} to write the mutation back -- mutating it in
     * place is enough here. */
    public static void writeToStack(ItemStack stack, Template template) {
        NBTUtilBC.getItemData(stack).put("template", template.serializeNBT());
    }

    /** Mirrors the original's inner {@code Template.FilledTemplate}, but does not need to override any of
     * {@link IFilledTemplate}'s bulk setters -- this port's {@link IFilledTemplate} already provides those as
     * default methods built on {@link #get}/{@link #set}, so only the three primitives are implemented here. */
    public class FilledTemplate implements IFilledTemplate {
        public Template getTemplate() {
            return Template.this;
        }

        private void checkPos(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= size.getX() || y >= size.getY() || z >= size.getZ()) {
                throw new IllegalArgumentException("Size: " + size + ", pos: " + new BlockPos(x, y, z));
            }
        }

        @Override
        public BlockPos getSize() {
            return size;
        }

        @Override
        public boolean get(int x, int y, int z) {
            checkPos(x, y, z);
            return data.get(index(x, y, z));
        }

        @Override
        public void set(int x, int y, int z, boolean value) {
            checkPos(x, y, z);
            data.set(index(x, y, z), value);
        }
    }
}
