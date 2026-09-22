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

/**
 * The abstract "which single face am I currently working" base {@code PipeBehaviourWood} (and, in 1.12.2, every
 * other directional behaviour -- none of the rest are ported yet) extends. A close port of 1.12.2's own
 * {@code PipeBehaviourDirectional}, with the player-interaction and statement plumbing this batch's own scope
 * notes (PORTING.md) deliberately does not add stripped out:
 *
 * <ul>
 * <li><b>Wrench-driven facing selection is dropped</b> ({@code onPipeActivate}, and the {@code RayTraceResult}/
 * {@code EnumPipePart} hit-part parameter it depended on to tell which face of the pipe block was actually
 * clicked). Nothing in this port has built that "which face was clicked" hit-detection for a pipe yet -- the
 * previous pipe batch's own scope notes already deferred all pipe interaction. This is not load-bearing: the
 * automatic fallback below ({@link #advanceFacing()}) already picks a valid facing on its own the moment one
 * exists, so a pipe placed against exactly one inventory works with zero player interaction at all. A pipe with
 * more than one valid neighbour will auto-pick whichever face {@link Direction#values()} finds first that
 * satisfies {@link #canFaceDirection}, not necessarily the one a player would have chosen by hand -- a real,
 * honest behavioural gap versus 1.12.2, not a bug.</li>
 * <li><b>{@code addActions}/{@code onActionActivate} are dropped</b> -- gates/statements are out of scope for
 * this whole module (matching {@code PipeFlowItems}'s own {@code addTriggers} drop, for the identical reason),
 * and {@code BCTransportStatements} is not ported.</li>
 * <li><b>{@code writePayload}/{@code readPayload} are dropped</b> -- no client rendering exists in this batch to
 * sync the active facing to, the same "no renderer yet" deferral already established throughout this module.</li>
 * <li><b>The face-cycling order in {@link #advanceFacing()} is plain {@link Direction#values()} ordinal
 * order</b>, not 1.12.2's {@code VanillaRotationHandlers.ROTATE_FACING}/{@code OrderedEnumMap} -- neither is
 * ported anywhere in this port yet, and nothing in this batch's scope needs a specific cycling order any more,
 * now that wrench interaction (the only thing that ever cared about the order faces were tried in) is dropped
 * above.</li>
 * </ul>
 */
public abstract class PipeBehaviourDirectional extends PipeBehaviour {

    protected EnumPipePart currentDir = EnumPipePart.CENTER;

    public PipeBehaviourDirectional(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourDirectional(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        setCurrentDir(NBTUtilBC.readEnum(nbt.get("currentDir"), Direction.class));
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
     * Picks the first valid facing found by iterating {@link Direction#values()} in plain ordinal order -- see
     * this class's own javadoc for why this is a deliberately simpler fallback than 1.12.2's own
     * wrench-cycle-aware order.
     *
     * @return True if the facing direction changed.
     */
    public boolean advanceFacing() {
        for (Direction dir : Direction.values()) {
            if (canFaceDirection(dir)) {
                setCurrentDir(dir);
                return true;
            }
        }
        return false;
    }

    @Nullable
    protected Direction getCurrentDir() {
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
