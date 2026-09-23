/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.wire;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.transport.EnumWirePart;
import buildcraft.api.transport.IWireEmitter;
import buildcraft.api.transport.WireNode;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pluggable.PipePluggable;

/**
 * The real wire network/signal logic this port's own {@link SimplePipeWireManager} scaffolding was deliberately
 * missing (see that class's own javadoc) -- a port of 1.12.2's {@code buildcraft.transport.wire.WireSystem} onto
 * this batch's already-existing {@code IWireManager}/{@code EnumWirePart}/{@code WireNode} foundation.
 *
 * <p><b>What is genuinely different from 1.12.2, and why.</b> 1.12.2 built a persisted, incrementally-maintained
 * graph of every wire network in the world ({@code WorldSavedDataWireSystems}), diffed it every tick, and pushed
 * changes to watching players through two bespoke payloads ({@code MessageWireSystems}/
 * {@code MessageWireSystemsPowered}). None of that plumbing is ported: per this batch's own scope notes, a
 * bespoke payload class is only worth adding when this port's established "sync via
 * {@code markDirtyAndSync()}/full-NBT tag" convention genuinely cannot do the job -- and here it cannot even in
 * principle, since a wire network's powered state spans many block entities at once, not one tile's own NBT. So
 * instead of maintaining and syncing a persisted graph, this class recomputes the connected component from
 * scratch, on demand, every time {@link SimplePipeWireManager#isPowered}/{@code isAnyPowered} is asked -- a plain
 * breadth-first walk over {@link WireNode}s, starting from one {@link EnumWirePart} and stopping the moment any
 * reachable {@link IWireEmitter} answers {@code true}. This is correct (it is the same reachability question
 * 1.12.2's graph answered) but not optimised for very large networks, since nothing caches the walk across
 * queries -- an acceptable trade this round (see this module's own PORTING.md entry), and one that also means
 * there is no separate persisted state to save, load, or keep in sync with the world at all: the tile's own
 * {@code parts}/colour map (already real, already NBT-synced) is the only durable state a wire needs.
 *
 * <p>{@link #canWireConnect} is 1.12.2's own {@code WireSystem.canWireConnect}, renamed-only: a wire crosses a
 * pipe boundary only where the two pipe segments are already connected to each other, neither face is blocked by
 * a blocking {@link PipePluggable} (a facade, a gate, a blocker plug), and -- for two structure pipes
 * specifically -- their dye colours either match or are unset (the "coloured pipe network" rule).
 */
public final class WireNetwork {

    private WireNetwork() {}

    /** True if a wire could cross from {@code holder} to its neighbour on {@code side}, even if no wire is
     * actually placed there -- 1.12.2's own {@code WireSystem.canWireConnect}. */
    public static boolean canWireConnect(IPipeHolder holder, Direction side) {
        IPipe pipe = holder.getPipe();
        if (pipe == null) {
            return false;
        }
        IPipe oPipe = holder.getNeighbourPipe(side);
        if (oPipe == null) {
            return false;
        }
        if (pipe.isConnected(side)) {
            return true;
        }
        PipePluggable plug = holder.getPluggable(side);
        PipePluggable oPlug = oPipe.getHolder().getPluggable(side.getOpposite());
        if ((plug != null && plug.isBlocking()) || (oPlug != null && oPlug.isBlocking())) {
            return false;
        }
        if (pipe.getDefinition().flowType == PipeApi.flowStructure
            || oPipe.getDefinition().flowType == PipeApi.flowStructure) {
            return pipe.getColour() == null || oPipe.getColour() == null || pipe.getColour() == oPipe.getColour();
        }
        return false;
    }

    /** True if {@code startPart} on {@code startHolder} is part of a same-coloured, electrically-connected wire
     * network in which at least one reachable {@link IWireEmitter} (a gate pluggable, currently) is emitting
     * that colour -- 1.12.2's {@code WireSystem}'s BFS constructor plus {@code WireSystem#update}, fused into one
     * on-demand walk. See this class's own javadoc for why nothing here is cached between calls. */
    public static boolean isPowered(IPipeHolder startHolder, EnumWirePart startPart) {
        DyeColor colour = startHolder.getWireManager().getColorOfPart(startPart);
        if (colour == null) {
            return false;
        }
        Level level = startHolder.getPipeLevel();
        if (level == null) {
            return false;
        }

        Set<WireNode> visited = new HashSet<>();
        Set<BlockPos> emitterBlocksChecked = new HashSet<>();
        Deque<WireNode> queue = new ArrayDeque<>();
        queue.add(new WireNode(startHolder.getPipePos(), startPart));

        while (!queue.isEmpty()) {
            WireNode node = queue.poll();
            if (!visited.add(node)) {
                continue;
            }
            IPipeHolder holder = holderAt(level, node.pos);
            if (holder == null) {
                continue;
            }
            if (holder.getWireManager().getColorOfPart(node.part) != colour) {
                // Geometrically reachable, but no matching wire actually sits here -- 1.12.2's own "walked but
                // never added to the system" case. Stop expanding from here.
                continue;
            }
            if (emitterBlocksChecked.add(node.pos)) {
                for (Direction face : Direction.values()) {
                    PipePluggable plug = holder.getPluggable(face);
                    if (plug instanceof IWireEmitter emitter && emitter.isEmitting(colour)) {
                        return true;
                    }
                }
            }
            for (Direction face : Direction.values()) {
                WireNode oNode = node.offset(face);
                if (oNode.pos.equals(node.pos) || canWireConnect(holder, face)) {
                    queue.add(oNode);
                }
            }
        }
        return false;
    }

    @Nullable
    private static IPipeHolder holderAt(Level level, BlockPos pos) {
        BlockEntity tile = level.getBlockEntity(pos);
        return tile instanceof IPipeHolder holder ? holder : null;
    }
}
