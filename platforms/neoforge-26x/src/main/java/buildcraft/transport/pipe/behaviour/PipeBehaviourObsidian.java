/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.util.List;
import java.util.WeakHashMap;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.transport.pipe.IFlowFluid;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.inventory.TransactorEntityItem;
import buildcraft.lib.misc.BoundingBoxUtil;
import buildcraft.lib.misc.VecUtil;

/**
 * The obsidian pipe's real 1.12.2 mechanic, confirmed by reading {@code PipeBehaviourObsidian} directly rather
 * than assumed from general BuildCraft knowledge: it is not explosive at all. It is an MJ-powered magnet -- when
 * it has exactly one open (unconnected) face and is fed power, it reaches out through that face (up to 4 blocks)
 * and force-inserts dropped item entities straight into the pipe.
 *
 * <p>{@code trySuckEntity} is trimmed to the {@link ItemEntity} case only -- 1.12.2's own
 * {@code ItemTransactorHelper.getTransactorForEntity} (which also covered minecart-style inventory entities) is
 * not ported anywhere in this codebase (see {@code ItemTransactorHelper}'s own javadoc); dropped-item pickup is
 * this pipe's real, recognisable purpose, so {@link TransactorEntityItem} (already used by {@code TileChute}'s
 * own item pickup) is reused directly for it. Suck-in-a-minecart's-inventory is a documented scope cut, not an
 * oversight. The fluid branch (`// TODO: Fluid extraction!`) was already an unimplemented stub in 1.12.2 itself,
 * so nothing is lost leaving it unported here either.
 */
public class PipeBehaviourObsidian extends PipeBehaviour implements IMjRedstoneReceiver {
    private static final long POWER_PER_ITEM = MjAPI.MJ / 2;
    private static final long POWER_PER_METRE = MjAPI.MJ / 4;

    private static final double INSERT_SPEED = 0.04;
    private static final int DROP_GAP = 20;

    /** Map of recently dropped item to the tick when it can be picked up -- stops a pipe re-sucking its own
     * just-ejected drop back in on the very next tick. */
    private final WeakHashMap<ItemEntity, Long> entityDropTime = new WeakHashMap<>();
    private int toWaitTicks = 0;

    public PipeBehaviourObsidian(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourObsidian(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        // Saves us from writing out the entity item's own identity -- see 1.12.2's identical comment.
        toWaitTicks = DROP_GAP;
    }

    @Override
    public void onTick() {
        if (pipe.getHolder().getPipeLevel().isClientSide()) {
            return;
        }
        if (toWaitTicks > 0) {
            toWaitTicks--;
        }
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourObsidian);
    }

    @Override
    public void onEntityCollide(Entity entity) {
        if (pipe.getHolder().getPipeLevel().isClientSide()) {
            return;
        }
        Direction openFace = getOpenFace();
        if (openFace != null) {
            trySuckEntity(entity, openFace, Long.MAX_VALUE, false);
        }
    }

    private Direction getOpenFace() {
        Direction openFace = null;
        for (Direction face : Direction.values()) {
            if (pipe.isConnected(face)) {
                if (openFace == null) {
                    openFace = face.getOpposite();
                } else {
                    return null;
                }
            }
        }
        return openFace;
    }

    private AABB getSuckingBox(Direction openFace, int distance) {
        AABB bb = BoundingBoxUtil.makeAround(VecUtil.convertCenter(pipe.getHolder().getPipePos()), 0.4);
        return switch (openFace) {
            case WEST -> bb.move(-distance, 0, 0).inflate(0.5, distance, distance);
            case EAST -> bb.move(distance, 0, 0).inflate(0.5, distance, distance);
            case DOWN -> bb.move(0, -distance, 0).inflate(distance, 0.5, distance);
            case UP -> bb.move(0, distance, 0).inflate(distance, 0.5, distance);
            case NORTH -> bb.move(0, 0, -distance).inflate(distance, distance, 0.5);
            case SOUTH -> bb.move(0, 0, distance).inflate(distance, distance, 0.5);
        };
    }

    /** @return The left over power. */
    private long trySuckEntity(Entity entity, Direction faceFrom, long power, boolean simulate) {
        if (entity.isRemoved() || entity instanceof LivingEntity || !(entity instanceof ItemEntity itemEntity)) {
            return power;
        }

        Long tickPickupObj = entityDropTime.get(itemEntity);
        if (tickPickupObj != null) {
            long tickPickup = tickPickupObj;
            long tickNow = pipe.getHolder().getPipeLevel().getGameTime();
            if (tickNow < tickPickup) {
                return power;
            } else {
                entityDropTime.remove(itemEntity);
            }
        }

        PipeFlow flow = pipe.getFlow();
        if (!(flow instanceof IFlowItems flowItem)) {
            if (flow instanceof IFlowFluid) {
                // TODO: Fluid extraction! (also an unimplemented stub in 1.12.2's own version of this pipe.)
            }
            return power;
        }

        long powerReqPerItem;
        int max;
        if (power == Long.MAX_VALUE) {
            max = Integer.MAX_VALUE;
            powerReqPerItem = 0;
        } else {
            double distance = Math.sqrt(entity.distanceToSqr(VecUtil.convertCenter(pipe.getHolder().getPipePos())));
            powerReqPerItem = (long) (Math.max(1, distance) * POWER_PER_METRE + POWER_PER_ITEM);
            max = (int) (power / powerReqPerItem);
        }
        if (max <= 0) {
            return power;
        }

        ResourceStack<ItemResource> extracted;
        try (Transaction transaction = Transaction.openRoot()) {
            extracted = new TransactorEntityItem(itemEntity).extract(null, 1, max, transaction);
            if (extracted != null && !extracted.isEmpty() && !simulate) {
                transaction.commit();
            }
        }
        if (extracted == null || extracted.isEmpty()) {
            return power;
        }
        if (!simulate) {
            ItemStack stack = extracted.resource().toStack(extracted.amount());
            flowItem.insertItemsForce(stack, faceFrom, null, INSERT_SPEED);
        }
        return power - powerReqPerItem * extracted.amount();
    }

    @PipeEventHandler
    public void onPipeDrop(PipeEventItem.Drop drop) {
        entityDropTime.put(drop.getEntity(), pipe.getHolder().getPipeLevel().getGameTime() + DROP_GAP);
    }

    // IMjRedstoneReceiver

    @Override
    public boolean canConnect(@NotNull IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        final long power = 512 * MjAPI.MJ;
        return power - receivePower(power, true);
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        if (toWaitTicks > 0) {
            return microJoules;
        }
        Direction openFace = getOpenFace();
        if (openFace == null) {
            return microJoules;
        }

        for (int d = 1; d < 5; d++) {
            AABB aabb = getSuckingBox(openFace, d);
            List<Entity> discoveredEntities = pipe.getHolder().getPipeLevel().getEntities((Entity) null, aabb, e -> true);

            for (Entity entity : discoveredEntities) {
                long leftOver = trySuckEntity(entity, openFace, microJoules, simulate);
                if (leftOver < microJoules) {
                    return leftOver;
                }
            }
        }
        return microJoules - MjAPI.MJ;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    @SuppressWarnings("unchecked")
    public <T> T getCapability(BlockCapability<T, Direction> capability, @org.jetbrains.annotations.Nullable Direction facing) {
        if (capability == MjCapabilities.CONNECTOR || capability == MjCapabilities.RECEIVER
            || capability == MjCapabilities.REDSTONE_RECEIVER) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }
}
