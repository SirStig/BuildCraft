/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc.data;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IAreaProvider;
import buildcraft.api.core.IBox;
import buildcraft.api.core.IZone;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.VecUtil;

/** MUTABLE integer variant of {@link AABB}, with a few BC-specific methods.
 *
 * <p>1.12.2's {@code laserData}/{@code lastMin}/{@code lastMax}/{@code lastType} fields -- all
 * {@code @SideOnly(Side.CLIENT)} -- were a render-side cache comparing the previously-drawn laser box against the
 * current one, keyed off {@code LaserData_BC8}. Nothing in the geometry this class actually implements
 * ({@link IBox}/{@link IAreaProvider}, {@link #extendToEncompass}, {@link #getBoundingBox}, ...) ever reads them;
 * they exist purely for a renderer, not ported yet, to stash state on. Dropped rather than carried over with
 * nothing to populate or read them -- they come back with the rendering pass, alongside {@code LaserData_BC8}.
 *
 * <p>{@link #readData(FriendlyByteBuf)}/{@link #writeData(FriendlyByteBuf)} used {@code MessageUtil.readBlockPos}/
 * {@code writeBlockPos} in 1.12.2 (blocked -- {@code MessageUtil} needs the old {@code IMessage} networking
 * stack). {@link FriendlyByteBuf} already has {@link FriendlyByteBuf#readBlockPos()}/
 * {@link FriendlyByteBuf#writeBlockPos(BlockPos)} built in on this target, so those are called directly instead
 * and {@code MessageUtil} is never needed at all.
 *
 * <p>{@link IZone#getRandomBlockPos(RandomSource)}, inherited through {@link IBox}, takes a {@code RandomSource} now rather
 * than {@code java.util.Random} -- see that interface's own javadoc. {@link PositionUtil#randomBlockPos} still
 * takes the old {@code java.util.Random}, so {@link #getRandomBlockPos(RandomSource)} reimplements that method's
 * arithmetic directly rather than delegating to it. */
public class Box implements IBox {

    private BlockPos min, max;

    public Box() {
        reset();
    }

    public Box(BlockPos min, BlockPos max) {
        this();
        this.min = VecUtil.min(min, max);
        this.max = VecUtil.max(min, max);
    }

    public Box(BlockEntity e) {
        this(e.getBlockPos(), e.getBlockPos());
    }

    public void reset() {
        min = null;
        max = null;
    }

    public boolean isInitialized() {
        return min != null && max != null;
    }

    public void extendToEncompassBoth(BlockPos newMin, BlockPos newMax) {
        this.min = VecUtil.min(this.min, newMin, newMax);
        this.max = VecUtil.max(this.max, newMin, newMax);
    }

    public void setMin(BlockPos min) {
        if (min == null) return;
        this.min = min;
        this.max = VecUtil.max(min, max);
    }

    public void setMax(BlockPos max) {
        if (max == null) return;
        this.min = VecUtil.min(min, max);
        this.max = max;
    }

    public void initialize(IBox box) {
        reset();
        extendToEncompassBoth(box.min(), box.max());
    }

    public void initialize(IAreaProvider a) {
        reset();
        extendToEncompassBoth(a.min(), a.max());
    }

    public void initialize(CompoundTag nbt) {
        reset();
        if (nbt.contains("xMin")) {
            min = new BlockPos(nbt.getInt("xMin"), nbt.getInt("yMin"), nbt.getInt("zMin"));
            max = new BlockPos(nbt.getInt("xMax"), nbt.getInt("yMax"), nbt.getInt("zMax"));
        } else {
            min = NBTUtilBC.readBlockPos(nbt.get("min"));
            max = NBTUtilBC.readBlockPos(nbt.get("max"));
        }
        extendToEncompassBoth(min, max);
    }

    public void writeToNBT(CompoundTag nbt) {
        if (min != null) nbt.put("min", NBTUtilBC.writeBlockPos(min));
        if (max != null) nbt.put("max", NBTUtilBC.writeBlockPos(max));
    }

    public CompoundTag writeToNBT() {
        CompoundTag nbt = new CompoundTag();
        writeToNBT(nbt);
        return nbt;
    }

    public void initializeCenter(BlockPos center, int size) {
        initializeCenter(center, new BlockPos(size, size, size));
    }

    public void initializeCenter(BlockPos center, Vec3i size) {
        extendToEncompassBoth(center.subtract(size), center.offset(size));
    }

    public List<BlockPos> getBlocksInArea() {
        List<BlockPos> blocks = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            // BlockPos.betweenClosed reuses a single mutable BlockPos across the whole iteration for
            // performance, so every entry has to be copied before it can be kept in a List.
            blocks.add(pos.immutable());
        }

        return blocks;
    }

    public List<BlockPos> getBlocksOnEdge() {
        return PositionUtil.getAllOnEdge(min, max);
    }

    @Override
    public Box expand(int amount) {
        if (!isInitialized()) return this;
        BlockPos am = new BlockPos(amount, amount, amount);
        setMin(min().subtract(am));
        setMax(max().offset(am));
        return this;
    }

    @Override
    public IBox contract(int amount) {
        return expand(-amount);
    }

    @Override
    public boolean contains(Vec3 p) {
        AABB bb = getBoundingBox();
        if (p.x < bb.minX || p.x >= bb.maxX) return false;
        if (p.y < bb.minY || p.y >= bb.maxY) return false;
        if (p.z < bb.minZ || p.z >= bb.maxZ) return false;
        return true;
    }

    public boolean contains(BlockPos i) {
        return contains(Vec3.atLowerCornerOf(i));
    }

    @Override
    public BlockPos min() {
        return min;
    }

    @Override
    public BlockPos max() {
        return max;
    }

    @Override
    public BlockPos size() {
        if (!isInitialized()) return BlockPos.ZERO;
        return max.subtract(min).offset(VecUtil.POS_ONE);
    }

    public BlockPos center() {
        return VecUtil.convertFloor(centerExact());
    }

    public Vec3 centerExact() {
        return Vec3.atLowerCornerOf(size()).scale(0.5).add(Vec3.atLowerCornerOf(min()));
    }

    @Override
    public String toString() {
        return "Box[min = " + min + ", max = " + max + "]";
    }

    public Box extendToEncompass(IBox toBeContained) {
        if (toBeContained == null) {
            return this;
        }
        extendToEncompassBoth(toBeContained.min(), toBeContained.max());
        return this;
    }

    /** IMPORTANT: Use {@link #contains(Vec3)} instead of the returned {@link AABB#contains(Vec3)} as the logic is
     * different! */
    public AABB getBoundingBox() {
        return new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max.offset(VecUtil.POS_ONE)));
    }

    public Box extendToEncompass(Vec3 toBeContained) {
        setMin(VecUtil.min(min, VecUtil.convertFloor(toBeContained)));
        setMax(VecUtil.max(max, VecUtil.convertCeiling(toBeContained)));
        return this;
    }

    public Box extendToEncompass(BlockPos toBeContained) {
        setMin(VecUtil.min(min, toBeContained));
        setMax(VecUtil.max(max, toBeContained));
        return this;
    }

    @Override
    public double distanceTo(BlockPos index) {
        return Math.sqrt(distanceToSquared(index));
    }

    @Override
    public double distanceToSquared(BlockPos index) {
        return closestInsideTo(index).distSqr(index);
    }

    public BlockPos closestInsideTo(BlockPos toTest) {
        return VecUtil.max(min, VecUtil.min(max, toTest));
    }

    @Override
    public BlockPos getRandomBlockPos(RandomSource rand) {
        // PositionUtil.randomBlockPos still takes the old java.util.Random, so this reimplements its
        // arithmetic directly -- see the class javadoc.
        BlockPos upper = max.offset(VecUtil.POS_ONE);
        return new BlockPos(
            min.getX() + rand.nextInt(upper.getX() - min.getX()),
            min.getY() + rand.nextInt(upper.getY() - min.getY()),
            min.getZ() + rand.nextInt(upper.getZ() - min.getZ())
        );
    }

    /** Delegate for {@link PositionUtil#isCorner(BlockPos, BlockPos, BlockPos)} */
    public boolean isCorner(BlockPos pos) {
        return PositionUtil.isCorner(min, max, pos);
    }

    /** Delegate for {@link PositionUtil#isOnEdge(BlockPos, BlockPos, BlockPos)} */
    public boolean isOnEdge(BlockPos pos) {
        return PositionUtil.isOnEdge(min, max, pos);
    }

    /** Delegate for {@link PositionUtil#isOnFace(BlockPos, BlockPos, BlockPos)} */
    public boolean isOnFace(BlockPos pos) {
        return PositionUtil.isOnFace(min, max, pos);
    }

    public boolean doesIntersectWith(Box box) {
        if (isInitialized() && box.isInitialized()) {
            return min.getX() <= box.max.getX() && max.getX() >= box.min.getX()//
                && min.getY() <= box.max.getY() && max.getY() >= box.min.getY() //
                && min.getZ() <= box.max.getZ() && max.getZ() >= box.min.getZ();
        }
        return false;
    }

    /** @return The intersection box (if these two boxes are intersecting) or null if they were not. */
    @Nullable
    public Box getIntersect(Box box) {
        if (doesIntersectWith(box)) {
            BlockPos min2 = VecUtil.max(min, box.min);
            BlockPos max2 = VecUtil.min(max, box.max);
            return new Box(min2, max2);
        }
        return null;
    }

    /** Calculates the total number of blocks on the edge. This is identical to (but faster than) calling
     * {@link #getBlocksOnEdge()}.{@link List#size() size()}
     *
     * @return The size of the list returned by {@link #getBlocksOnEdge()}. */
    public int getBlocksOnEdgeCount() {
        return PositionUtil.getCountOnEdge(min(), max());
    }

    public void readData(FriendlyByteBuf stream) {
        if (stream.readBoolean()) {
            min = stream.readBlockPos();
            max = stream.readBlockPos();
        } else {
            min = null;
            max = null;
        }
    }

    public void writeData(FriendlyByteBuf stream) {
        boolean isValid = isInitialized();
        stream.writeBoolean(isValid);
        if (isValid) {
            stream.writeBlockPos(min);
            stream.writeBlockPos(max);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) return false;
        Box box = (Box) obj;
        if (!Objects.equal(min, box.min)) return false;
        if (!Objects.equal(max, box.max)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(min, max);
    }
}
