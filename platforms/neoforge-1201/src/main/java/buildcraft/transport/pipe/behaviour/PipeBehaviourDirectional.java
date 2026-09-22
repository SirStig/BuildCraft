/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pipe.PipeBehaviour;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.collect.OrderedEnumMap;

/**
 * The abstract "which single face am I currently working" base {@code PipeBehaviourWood} and
 * {@code PipeBehaviourIron} extend. A close port of 1.12.2's own {@code PipeBehaviourDirectional}, with the
 * player-interaction and statement plumbing this port does not have reshaped or stripped out:
 *
 * <ul>
 * <li><b>Wrench-driven facing selection is back, as a cycle, through a different entry point.</b> The wood-pipe
 * batch dropped it because nothing needed it; the iron pipe does (an iron pipe stuck on an auto-picked output
 * face is useless). 1.12.2 reached it through {@code onPipeActivate(player, trace, hitX, hitY, hitZ, part)}: a
 * wrench on the centre ({@code EnumPipePart.CENTER}) called {@link #advanceFacing()}, a wrench on one of the arms
 * jumped straight to that arm's face. This port's {@code ItemWrench#useOn} never reaches a block's own
 * interaction hooks for a rotation-capable block -- it calls {@code CustomRotationHelper#attemptRotateBlock},
 * which short-circuits to {@code ICustomRotationHandler#attemptRotation} when the block itself implements it (the
 * same route {@code BlockEngineWood}/{@code BlockEngineCreative} already use). {@code BlockPipeHolder} now
 * implements that interface and forwards to {@link #advanceFacing()} here. Only the centre-click cycle is
 * reproduced: {@code BlockPipeHolder} is still a plain full cube with no per-part hit detection, so there is no
 * way to tell "the player clicked the east arm" from "the player clicked the east face of the bounding cube",
 * and treating every face click as an arm click would make cycling impossible (clicking the active face again
 * would be a no-op). Every wrench use therefore cycles, exactly like 1.12.2's own centre click.</li>
 * <li><b>The cycle order is 1.12.2's own {@code VanillaRotationHandlers.ROTATE_FACING}</b> (east, south, down,
 * west, north, up), reproduced inline as {@link #ROTATION_ORDER} with the already-ported {@link OrderedEnumMap},
 * the same way {@code TileEngineBase} already does -- {@code VanillaRotationHandlers} itself stays unported. This
 * order also drives the tick-time fallback in {@link #onTick()}, as in 1.12.2 (the wood-pipe batch had replaced
 * it with plain {@link Direction#values()} order since nothing observed the order then).</li>
 * <li><b>{@code addActions}/{@code onActionActivate} stay dropped</b> -- gates/statements are out of scope for
 * this whole module (matching {@code PipeFlowItems}'s own {@code addTriggers} drop, for the identical reason),
 * and {@code BCTransportStatements} is not ported.</li>
 * <li><b>{@code writePayload}/{@code readPayload} stay dropped; the active face still reaches the client.</b>
 * {@link #setCurrentDir} calls {@code scheduleNetworkUpdate(BEHAVIOUR)}, which on {@code TilePipeHolder} is a
 * whole-tile {@code markDirtyAndSync()} -- the client's copy of the tile rebuilds its {@code Pipe} (and so this
 * behaviour, via the NBT constructor below) from the full update tag, {@code "currentDir"} included -- and
 * {@code TilePipeHolder} additionally pushes the face onto the placed block state's {@code active} property, which
 * is what actually renders.</li>
 * <li><b>The NBT constructor assigns {@link #currentDir} directly instead of calling {@link #setCurrentDir}</b>
 * (1.12.2 called the setter). A real, reproduced bug, not a style change: during a disk-based chunk load, vanilla
 * calls {@code BlockEntity#loadAdditional} before {@code setLevel}, so {@code setCurrentDir}'s own
 * {@code getPipeLevel().isClientSide()} check threw a {@code NullPointerException} for any wooden pipe saved with
 * an active face -- and the tile's loader swallowed it, silently resetting the pipe to a bare
 * {@code pipe_holder} on every restart (the same failure shape the earlier {@code PipeFlowItems} reload fix
 * closed). Nothing here needs the setter's side effect at load time anyway: a freshly-loaded tile has nothing to
 * resync yet.</li>
 * </ul>
 */
public abstract class PipeBehaviourDirectional extends PipeBehaviour {

    /** 1.12.2's {@code VanillaRotationHandlers.ROTATE_FACING}, inline -- see this class's own javadoc. */
    public static final OrderedEnumMap<Direction> ROTATION_ORDER = new OrderedEnumMap<>(
        Direction.class, Direction.EAST, Direction.SOUTH, Direction.DOWN, Direction.WEST, Direction.NORTH,
        Direction.UP
    );

    protected EnumPipePart currentDir = EnumPipePart.CENTER;

    public PipeBehaviourDirectional(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourDirectional(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        // Not setCurrentDir: see this class's own javadoc (the level is still null during a chunk load).
        currentDir = EnumPipePart.fromFacing(NBTUtilBC.readEnum(nbt.get("currentDir"), Direction.class));
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.put("currentDir", NBTUtilBC.writeEnum(getCurrentDir()));
        return nbt;
    }

    @Override
    public void onTick() {
        if (pipe.getHolder().getPipeLevel().isClientSide()) {
            return;
        }

        if (!canFaceDirection(getCurrentDir())) {
            if (!advanceFacing()) {
                setCurrentDir(null);
            }
        }
    }

    protected abstract boolean canFaceDirection(@Nullable Direction dir);

    /**
     * Cycles to the next face in {@link #ROTATION_ORDER} after the current one that {@link #canFaceDirection}
     * accepts -- the wrench action ({@code BlockPipeHolder#attemptRotation}) and the tick-time fallback alike.
     *
     * @return True if a valid face was found (which may be the current one, if it is the only valid face -- the
     *         same semantics 1.12.2's own method had despite its "if the facing changed" doc).
     */
    public boolean advanceFacing() {
        Direction current = currentDir.face;
        for (int i = 0; i < 6; i++) {
            current = ROTATION_ORDER.next(current);
            if (canFaceDirection(current)) {
                setCurrentDir(current);
                return true;
            }
        }
        return false;
    }

    /** {@code public} here (it was {@code protected} in 1.12.2) so {@code TilePipeHolder} can push the active
     * face onto the block state's {@code active} property. */
    @Nullable
    public Direction getCurrentDir() {
        return currentDir.face;
    }

    protected void setCurrentDir(@Nullable Direction setTo) {
        if (this.currentDir.face == setTo) {
            return;
        }
        this.currentDir = EnumPipePart.fromFacing(setTo);
        if (!pipe.getHolder().getPipeLevel().isClientSide()) {
            pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
    }
}
