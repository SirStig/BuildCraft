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
 * A captured rectangular region that remembers only "is there a block here" for every position, ignoring which
 * exact block -- used by the Filler's "template" fill pattern and by {@link IFilledTemplate}. The 1.12.2/
 * {@code common} original ({@code buildcraft.builders.snapshot.Template}) extended a shared {@code Snapshot} base
 * (also parent to {@code Blueprint}) that carried a content hash {@code Key}, a captured {@code facing}/
 * {@code offset} pair, and rotation support via {@code Snapshot.BuildingInfo}/{@code EnumSnapshotType} dispatch.
 *
 * <p><b>This is a deliberately simplified port for this round</b> (see this port's {@link Blueprint}, which made
 * the same call: it never ported {@code Snapshot} either, and stands alone rather than sharing a base class with
 * this class). Dropped, to match: the {@code Key} content-hash/dedup machinery, {@code facing}/{@code offset}
 * capture, rotation, and {@code Snapshot.BuildingInfo}. What is kept, faithfully: the {@code size} + packed
 * {@link BitSet} shape itself (unchanged from the original {@code Template}, right down to using a
 * {@link BitSet} rather than a {@code boolean[]}), the {@link #invert()} flip, and the {@link FilledTemplate}
 * inner view implementing {@link IFilledTemplate} directly against the outer template's own backing data --
 * exactly the original's own design, where {@code getFilledTemplate()} hands back a live view rather than a copy.
 *
 * <p>Not yet wired to a capture tool/GUI this round (see the module's own dated Progress entry in PORTING.md): the
 * original's {@code TemplateBuilder} drove a {@code TileBuilder} that replayed a template by having a fake player
 * simulate an item-use ({@link buildcraft.api.template.ITemplateHandler}) at every flagged position -- a second,
 * template-flavoured build mode alongside the one {@code TileBuilder}/{@code BlueprintBuilder} already implement
 * for {@code Blueprint} in this port. Wiring that dual mode into the existing (Blueprint-only) {@code TileBuilder}/
 * {@code TileArchitectTable} pair is left as a followup; {@link #capture}/{@link #serializeNBT}/
 * {@link #readFromStack} below are real and independently usable in the meantime.
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
        template.data = BitSet.valueOf(nbt.getByteArray("data").orElse(new byte[0]));
        return template;
    }

    @Nullable
    public static Template readFromStack(ItemStack stack) {
        CompoundTag data = NBTUtilBC.getItemData(stack).getCompound("template").orElse(null);
        return data == null ? null : deserializeNBT(data);
    }

    public static void writeToStack(ItemStack stack, Template template) {
        NBTUtilBC.updateItemData(stack, nbt -> nbt.put("template", template.serializeNBT()));
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
