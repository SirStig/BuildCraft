/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.IPipeExtensionManager;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.BCTransportRegistries;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * 1.12.2's own {@code PipeExtensionManager} lets a stripes pipe carrying a pipe item lay (or, for a registered
 * "retraction" material -- the void pipe -- retract) a whole pipe run one block per tick, by relocating the
 * stripes pipe's own block entity via a {@code BlockSnapshot}/{@code FakePlayer}/cancellable-placement-event
 * dance so other mods' block-protection listeners still see and can veto each step.
 *
 * <p><b>{@link #requestPipeExtension} is real as of this pass, for the plain "extend" case.</b> Real 1.12.2 queues
 * the request and processes it at the end of the current world tick (a {@code TickEvent.WorldTickEvent}
 * {@code Phase.END} listener on {@code MinecraftForge.EVENT_BUS}) rather than acting inline from inside the
 * stripes pipe's own item-drop event handler -- ported the same way here ({@link #onLevelTick}, a
 * {@code LevelTickEvent.Post} listener; this enum is registered onto {@code NeoForge.EVENT_BUS} from
 * {@code BCTransportRegistries} at exactly the spot 1.12.2's own {@code BCTransportRegistries} registers it onto
 * {@code MinecraftForge.EVENT_BUS}). Acting immediately instead would tear down the very
 * {@code TilePipeHolder}/{@code PipeBehaviourStripes} instance whose own event-handler call stack is still on the
 * stack at that point ({@code PipeBehaviourStripes#onDrop} -> {@code StripesRegistry#handleItem} ->
 * {@code StripesHandlerPipes#handle} -> here).
 *
 * <p>The relocation itself ({@link #extend}) is simplified from 1.12.2's own ~100-line version: it still moves the
 * stripes pipe's block entity one block into the open space ahead and leaves a freshly-placed pipe of the fed
 * item's own {@link PipeDefinition} behind at the vacated position -- exactly 1.12.2's own "the stripes pipe moves
 * forward, the new pipe is placed behind it" result (see {@link IPipeExtensionManager#requestPipeExtension}'s own
 * javadoc) -- but does it with two plain {@code Level#removeBlock}/{@code Level#setBlock} calls plus
 * {@code TilePipeHolder#onPlacedBy} (the same "place a pipe of this definition here" primitive
 * {@code BlockPipeHolder#setPlacedBy} already uses for an ordinary player placement, and the one this port's own
 * RCON rig already uses directly -- see {@code BlockPipeHolder#getDrops}' own javadoc) instead of 1.12.2's own
 * {@code BlockSnapshot}/cancellable-{@code BlockEvent.PlaceEvent} replay. That replay existed purely so other
 * mods' block-protection listeners could veto each step of the relocation; nothing in this port's scope depends on
 * that interop yet, so it is dropped rather than reproduced.
 *
 * <p>One consequence worth naming: the relocated stripes pipe is a <b>fresh</b> {@code Pipe} at the new position,
 * not a copy of the old one's NBT, so accumulated state (MJ battery charge, dye colour) does not survive an
 * extension -- only the owner carries over (read from the old tile before it is removed, then stamped onto both
 * new tiles via a {@code FakePlayer} of that owner, matching
 * {@link buildcraft.transport.pipe.behaviour.PipeBehaviourStripes}'s own established {@code fakePlayerProvider}
 * precedent). Chaining several extensions in a row (feeding several pipe items one after another) still lays a
 * genuine multi-segment run with no extra bookkeeping needed here: each call only ever looks at the stripes pipe's
 * own current position, which the previous call already relocated -- the same implicit "the world's own block
 * layout is the run" property 1.12.2's own version relies on.
 *
 * <p><b>Retraction ({@link #registerRetractionPipe}) is still a documented scope cut, not attempted this round.</b>
 * A registered retraction pipe (the void pipe) is recognised and declined explicitly in
 * {@link #requestPipeExtension} rather than silently mishandled -- but a decline does not reach plain item
 * ejection the way it does for every other {@code IStripesHandlerItem}: {@code StripesHandlerPipes} declining just
 * falls through to the next lower-priority handler in {@code StripesRegistry}'s own dispatch order, and every pipe
 * item's own {@code ItemPipeHolder} is also a {@code BlockItem}, so {@code StripesHandlerPlaceBlock} (unmodified,
 * out of this pass's scope) catches it instead and places it as a plain block at the open face -- confirmed live
 * over RCON: a fed void pipe item ends up placed one block ahead of the stripes pipe with no relocation, leaving
 * the stripes pipe with two connections (and consequently no open face at all) rather than retracting. This is not
 * new: every pipe item hit this same fallback before this pass, since the previous stub declined unconditionally.
 * Un-laying a run needs to walk backward along the pipe network re-deriving each previous segment's own item and
 * pipe definition, which is materially more bookkeeping than the plain forward-extension case above; left as a
 * clearly separate follow-up, at which point this fallback stops applying to the void pipe too.
 */
public enum PipeExtensionManager implements IPipeExtensionManager {
    INSTANCE;

    private final Set<PipeDefinition> retractionPipeDefs = new HashSet<>();
    private final Map<Level, List<ExtensionRequest>> requests = new IdentityHashMap<>();

    @Override
    public boolean requestPipeExtension(Level level, BlockPos pos, Direction dir, IStripesActivator stripes, ItemStack stack) {
        if (level.isClientSide() || stack.isEmpty() || !(stack.getItem() instanceof IItemPipe itemPipe)) {
            return false;
        }
        if (retractionPipeDefs.contains(itemPipe.getDefinition())) {
            // Retraction is a documented follow-up -- see this class's own javadoc -- so a retraction pipe (the
            // void pipe) is declined here exactly like "no handler wanted this item" would be.
            return false;
        }
        requests.computeIfAbsent(level, l -> new ArrayList<>()).add(new ExtensionRequest(pos, dir, stripes, stack.copy()));
        return true;
    }

    @Override
    public void registerRetractionPipe(PipeDefinition pipeDefinition) {
        if (pipeDefinition != null) {
            retractionPipeDefs.add(pipeDefinition);
        }
    }

    /** Runs every {@link #requestPipeExtension} queued for {@code event.getLevel()} once that level's own tile
     * ticking has already finished for this tick -- see this class's own javadoc for why this cannot run inline. */
    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        List<ExtensionRequest> pending = requests.remove(event.getLevel());
        if (pending == null || pending.isEmpty() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ExtensionRequest request : pending) {
            extend(serverLevel, request);
        }
    }

    private void extend(ServerLevel level, ExtensionRequest request) {
        BlockPos targetPos = request.pos().relative(request.dir());
        if (!level.getBlockState(targetPos).canBeReplaced() || BuildCraftAPI.fakePlayerProvider == null) {
            sendBack(request, request.stack());
            return;
        }
        if (!(level.getBlockEntity(request.pos()) instanceof TilePipeHolder stripesTile) || stripesTile.getPipe() == null) {
            // The stripes pipe itself is gone by the time this ran (broken, chunk unloaded) -- nothing to extend.
            sendBack(request, request.stack());
            return;
        }
        PipeDefinition stripesDef = stripesTile.getPipe().getDefinition();
        ItemPipeHolder stripesItem = BCTransportRegistries.getItemForPipe(stripesDef);
        if (stripesItem == null) {
            sendBack(request, request.stack());
            return;
        }
        GameProfile owner = stripesTile.getOwner();
        FakePlayer player = BuildCraftAPI.fakePlayerProvider.getFakePlayer(level, owner, request.pos());

        // Step 1: vacate the stripes pipe's own position. BlockPipeHolder keeps the same TilePipeHolder java
        // instance across an in-place state change (block identity does not change), so the old tile has to
        // genuinely go away first, or the fresh Pipe stamped on below would share event-bus handler registrations
        // with the stripes Pipe it is replacing.
        level.removeBlock(request.pos(), false);

        // Step 2: the fed item's own pipe is laid where the stripes pipe used to be.
        level.setBlock(request.pos(), BCTransportRegistries.PIPE_HOLDER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(request.pos()) instanceof TilePipeHolder newSegment)) {
            return;
        }
        newSegment.onPlacedBy(player, request.stack().copyWithCount(1));

        // Step 3: the stripes pipe itself moves forward into the space that was just checked clear.
        level.setBlock(targetPos, BCTransportRegistries.PIPE_HOLDER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(targetPos) instanceof TilePipeHolder newStripes) {
            newStripes.onPlacedBy(player, new ItemStack(stripesItem));
        }

        // Any extra copies in the fed stack beyond the one just placed are sent back, exactly matching
        // IPipeExtensionManager#requestPipeExtension's own contract ("only one item is used; the rest is sent
        // back").
        if (request.stack().getCount() > 1) {
            sendBack(request, request.stack().copyWithCount(request.stack().getCount() - 1));
        }
    }

    private void sendBack(ExtensionRequest request, ItemStack stack) {
        if (!request.stripes().sendItem(stack.copy(), request.dir())) {
            request.stripes().dropItem(stack, request.dir());
        }
    }

    private record ExtensionRequest(BlockPos pos, Direction dir, IStripesActivator stripes, ItemStack stack) {}
}
