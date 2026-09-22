/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import java.util.EnumSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.misc.VecUtil;

/**
 * One item moving through a pipe's internal space -- a direct port of 1.12.2's own {@code TravellingItem},
 * server-state fields only.
 *
 * <p><b>{@code clientItemLink}/{@code stackSize} (the render-thread-safe item link and cached count) stay
 * dropped</b> -- this target's renderer ({@code RenderTilePipeHolder}) reads {@link #stack} directly through
 * {@link #getStack()} instead, since nothing here ever needs to survive a network round trip through
 * {@code BuildCraftObjectCaches} the way 1.12.2's own client-only item creation packet did (this port's
 * {@code TilePipeHolder} syncs its whole NBT-serialised state instead -- see {@code PipeFlowItems}' own
 * {@code getTravellingItemsForRender} javadoc). <b>{@code interpolatePosition}/{@code getRenderPosition}/
 * {@code getRenderDirection}/{@code isVisible} are re-added below</b>, now that a real renderer exists to call
 * them -- {@link #getRenderPosition}/{@link #getRenderDirection} are close, renamed ports of the 1.12.2 originals
 * (see each method's own javadoc for what changed); {@code interpolatePosition} and {@code isVisible} are not
 * brought back under their own names, since {@link #getRenderPosition} already inlines the one real lerp
 * {@code interpolatePosition} ever did, and every item this accessor set reaches is already visible by
 * construction (a phantom item is filtered out by the renderer itself via {@link #isPhantom()}, not by a
 * dedicated "am I visible" query the original never actually varied). Nothing else changed:
 * {@code ItemStack.serializeNBT()}/{@code new ItemStack(NBTTagCompound)} become {@code ItemStack.CODEC}-based
 * round trips (this target has no item-NBT constructor at all -- stacks serialise through a codec since 1.20.5),
 * and colour/side/tried persist through the already-ported {@code NBTUtilBC.writeEnum}/{@code readEnum}/
 * {@code writeEnumSet}/{@code readEnumSet}, the same helpers {@code Pipe} itself uses for its own colour field.
 */
public class TravellingItem {
    /** The server itemstack. */
    @NotNull
    ItemStack stack;
    @Nullable
    DyeColor colour;
    boolean toCenter;
    double speed = 0.05;
    /** Absolute times (relative to {@code level.getGameTime()}) with when an item started to when it finishes. */
    long tickStarted, tickFinished;
    /** Relative time (from {@link #tickStarted}) until an event needs to be fired or this item needs changing. */
    int timeToDest;
    /** If {@link #toCenter} is true then this represents the side that the item is coming from, otherwise this
     * represents the side that the item is going to. */
    @Nullable
    Direction side;
    /** Every face this item has already tried and failed to go through. */
    EnumSet<Direction> tried = EnumSet.noneOf(Direction.class);
    /** If true then events won't be fired for this, and this item won't be dropped by the pipe. However it
     * still affects things like {@code doesContainItems}. */
    boolean isPhantom = false;

    public TravellingItem(@NotNull ItemStack stack) {
        this.stack = stack;
    }

    public TravellingItem(CompoundTag nbt, long tickNow, HolderLookup.Provider registries) {
        Tag stackTag = nbt.get("stack");
        if (stackTag == null) {
            stack = ItemStack.EMPTY;
        } else {
            stack = ItemStack.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), stackTag)
                .result()
                .orElse(ItemStack.EMPTY);
        }
        colour = NBTUtilBC.readEnum(nbt.get("colour"), DyeColor.class);
        toCenter = nbt.getBooleanOr("toCenter", false);
        speed = nbt.getDoubleOr("speed", 0.05);
        if (speed < 0.001) {
            // Just to make sure that we don't have an invalid speed
            speed = 0.001;
        }
        tickStarted = nbt.getIntOr("tickStarted", 0) + tickNow;
        tickFinished = nbt.getIntOr("tickFinished", 0) + tickNow;
        timeToDest = nbt.getIntOr("timeToDest", 0);

        side = NBTUtilBC.readEnum(nbt.get("side"), Direction.class);
        if (side == null || timeToDest == 0) {
            // Older version safety net, matching 1.12.2's own fallback.
            toCenter = true;
        }
        tried = NBTUtilBC.readEnumSet(nbt.get("tried"), Direction.class);
        isPhantom = nbt.getBooleanOr("isPhantom", false);
    }

    public CompoundTag writeToNbt(long tickNow, HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        if (!stack.isEmpty()) {
            ItemStack.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .result()
                .ifPresent(tag -> nbt.put("stack", tag));
        }
        nbt.put("colour", NBTUtilBC.writeEnum(colour));
        nbt.putBoolean("toCenter", toCenter);
        nbt.putDouble("speed", speed);
        nbt.putInt("tickStarted", (int) (tickStarted - tickNow));
        nbt.putInt("tickFinished", (int) (tickFinished - tickNow));
        nbt.putInt("timeToDest", timeToDest);
        nbt.put("side", NBTUtilBC.writeEnum(side));
        nbt.put("tried", NBTUtilBC.writeEnumSet(tried, Direction.class));
        if (isPhantom) {
            nbt.putBoolean("isPhantom", true);
        }
        return nbt;
    }

    public int getCurrentDelay(long tickNow) {
        long diff = tickFinished - tickNow;
        if (diff < 0) {
            return 0;
        } else {
            return (int) diff;
        }
    }

    public void genTimings(long now, double distance) {
        tickStarted = now;
        timeToDest = (int) Math.ceil(distance / speed);
        tickFinished = now + timeToDest;
    }

    public boolean canMerge(TravellingItem with) {
        if (isPhantom || with.isPhantom) {
            return false;
        }
        return toCenter == with.toCenter
            && colour == with.colour
            && side == with.side
            && Math.abs(tickFinished - with.tickFinished) < 4
            && stack.getMaxStackSize() >= stack.getCount() + with.stack.getCount()
            && StackUtil.canMerge(stack, with.stack);
    }

    /** Attempts to merge the two travelling items together, if they are close enough. */
    public boolean mergeWith(TravellingItem with) {
        if (canMerge(with)) {
            this.stack.grow(with.stack.getCount());
            return true;
        }
        return false;
    }

    // Rendering -- re-added surface, see this class's own javadoc for why.

    /** @return The real server itemstack, for a renderer to draw directly -- never {@code null}, may be
     *          {@link ItemStack#isEmpty()} (the renderer's own job to skip, matching every other item-empty check
     *          in this class). */
    public ItemStack getStack() {
        return stack;
    }

    /** @return True if this item is phantom bookkeeping only (see {@link #isPhantom}'s own field javadoc) -- a
     *          renderer must skip these entirely, per this batch's own scope. */
    public boolean isPhantom() {
        return isPhantom;
    }

    /** This item's real-time render position, in the same block-local space every other renderer in this port
     * already uses (block origin at {@code (0,0,0)}; {@code pos} lets a caller offset that origin, matching
     * 1.12.2's own signature exactly, though every real caller in this port passes {@link BlockPos#ZERO} and
     * translates the whole {@code PoseStack} to the tile's own world position beforehand instead, the standard
     * modern {@code BlockEntityRenderer} convention).
     *
     * <p>A close, renamed port of 1.12.2's own {@code getRenderPosition(BlockPos, long, float, PipeFlowItems)}:
     * {@code EnumFacing} -> {@link Direction}, {@code Vec3d} -> {@link Vec3}, {@code VecUtil.offset}/
     * {@code VecUtil.scale} unchanged in shape. Inlines 1.12.2's own separate {@code interpolatePosition} helper
     * directly (that method took an already-clamped {@code interp} plus two endpoints and did nothing else; with
     * only one real caller left there is no reason to keep it split out). One real, deliberate fix over the
     * original: the original divided by {@code tickFinished - tickStarted} unconditionally, which is genuinely
     * {@code 0} for a same-tick, zero-distance item (an {@code insertItemsForce} call with {@code distance == 0}
     * produces exactly this -- confirmed by re-reading {@link PipeFlowItems#insertItemsForce}'s own
     * {@code genTimings(now, 0)} call) -- a {@code float} {@code 0f/0f} division that resolves to {@code NaN},
     * which then survives {@code Math.min}/{@code Math.max} unclamped (NaN compares false against everything),
     * silently breaking any renderer relying on this the way this port's own piston-rod render already had to
     * guard against an equivalent first-frame divide-by-zero. Guarded here by treating a non-positive duration
     * as "already arrived" ({@code interp = 1}), which is also the semantically correct answer for a zero-length
     * trip. */
    public Vec3 getRenderPosition(BlockPos pos, long tick, float partialTick, PipeFlowItems flow) {
        long diff = tickFinished - tickStarted;
        long afterTick = tick - tickStarted;

        float interp = diff <= 0 ? 1f : (afterTick + partialTick) / diff;
        interp = Math.max(0f, Math.min(1f, interp));

        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 vecSide = side == null ? center : VecUtil.offset(center, side, flow.getPipeLength(side));

        Vec3 vecFrom = toCenter ? vecSide : center;
        Vec3 vecTo = toCenter ? center : vecSide;

        return VecUtil.scale(vecFrom, 1 - interp).add(VecUtil.scale(vecTo, interp));
    }

    /** This item's current facing for rendering purposes: the side it is heading towards if it has already
     * left the centre, or the opposite of the side it came from if it is still travelling towards the centre --
     * matching 1.12.2's own {@code getRenderDirection}'s intent exactly.
     *
     * <p>Unlike the 1.12.2 original (which took {@code (long tick, float partialTicks)} and computed the same
     * clamped interpolation fraction {@link #getRenderPosition} does, only to never actually read the result),
     * this drops both parameters entirely: direction only ever changes when a fresh {@link TravellingItem}
     * replaces this one at the pipe's centre ({@link PipeFlowItems#onItemReachCenter}/{@code onItemReachEnd}
     * always construct a new instance with its own already-correct {@link #toCenter}/{@link #side}, never
     * mutate an in-flight item's own direction), so there was never a per-frame value for the original's own
     * unused parameters to produce in the first place -- confirmed by re-reading the original method body, not
     * assumed from the signature alone. */
    @Nullable
    public Direction getRenderDirection() {
        return toCenter ? (side == null ? null : side.getOpposite()) : side;
    }
}
