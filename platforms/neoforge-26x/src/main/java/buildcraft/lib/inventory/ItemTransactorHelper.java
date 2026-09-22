/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.inventory.ItemTransactorCapabilities;

/**
 * Trimmed port of 1.12.2's {@code ItemTransactorHelper}: only {@code getTransactor}/{@code move}, which is all
 * {@code buildcraft.factory.tile.TileChute} (this file's only caller so far) actually needs.
 *
 * <p>{@code getInjectable}/{@code wrapInjectable}/{@code insertAllBypass} are dropped -- they exist only for
 * {@code buildcraft.api.transport.IInjectable}/{@code PipeApi}, which belong to the entirely unported
 * {@code transport} (pipes) module, the same "not needed for this task's real caller" scope note already
 * established for {@code ItemMarkerConnector}'s own deferred sub-features. {@code getTransactor(InventoryPlayer)}/
 * {@code getTransactorForEntity} are dropped too -- nothing in this port calls them yet.
 * {@code createDroppingTransactor} is unused by every caller in this port (it only ever backed a pipe output
 * dropping items into the world) and is dropped for the same reason.
 *
 * <p>1.12.2's single {@code getTransactor(ICapabilityProvider, EnumFacing)} split into two overloads here, because
 * a block entity's capability and an entity's capability are fundamentally different lookups on this target --
 * one goes through {@code Level#getCapability(BlockCapability, BlockPos, Direction)} (see
 * {@code TileEngineBase#getReceiverToPower}, already ported, for the identical idiom), the other through
 * {@code Entity#getCapability(EntityCapability, Direction)}. There is no common {@code ICapabilityProvider}
 * supertype left for both to share (contrast the 1.20.1 copy of this class, which still has one). Both fall
 * through to {@code Capabilities.Item.BLOCK}/{@code Capabilities.Item.ENTITY_AUTOMATION} -- NeoForge's own
 * vanilla-interop token -- rather than a BuildCraft-declared second capability; see
 * {@code ItemTransactorCapabilities}'s own javadoc for why. The old {@code ISidedInventory}/{@code IInventory}
 * fallback (for a neighbour that exposes neither capability) is gone with it: confirmed by reading
 * {@code CapabilityHooks} in the NeoForge sources jar, NeoForge itself already registers
 * {@code Capabilities.Item.BLOCK} for every vanilla container block entity and {@code Capabilities.Item.ENTITY}/
 * {@code ENTITY_AUTOMATION} for minecart-type entities, so a neighbour that looks like an inventory always answers
 * one of the two capability queries above; there is nothing left for a manual {@code Container} fallback to catch.
 *
 * <p>{@code move}'s {@code boolean simulate} parameter is gone with {@code IItemHandler}; the whole "peek what
 * src can give up, offer exactly that to dst, then take only what dst actually accepted" dance is rebuilt on
 * {@link Transaction} nesting instead, matching the trick {@link AbstractInvItemTransactor} already uses for its
 * own all-or-nothing insert.
 */
public final class ItemTransactorHelper {

    private ItemTransactorHelper() {}

    /** Looks up the item transactor a neighbouring block entity exposes at {@code pos}, from {@code side}.
     * {@code null} if {@code level} is {@code null} yields {@link NoSpaceTransactor#INSTANCE}, matching 1.12.2's
     * own null-provider handling. */
    @NotNull
    public static IItemTransactor getTransactor(@Nullable Level level, BlockPos pos, Direction side) {
        if (level == null) {
            return NoSpaceTransactor.INSTANCE;
        }
        IItemTransactor trans = level.getCapability(ItemTransactorCapabilities.ITEM_TRANSACTOR, pos, side);
        if (trans != null) {
            return trans;
        }
        return wrap(level.getCapability(Capabilities.Item.BLOCK, pos, side));
    }

    /** As {@link #getTransactor(Level, BlockPos, Direction)}, but for an entity rather than a block position --
     * covers minecart chests and anything else registering {@code Capabilities.Item.ENTITY_AUTOMATION}. */
    @NotNull
    public static IItemTransactor getTransactor(@Nullable Entity entity, Direction side) {
        if (entity == null) {
            return NoSpaceTransactor.INSTANCE;
        }
        return wrap(entity.getCapability(Capabilities.Item.ENTITY_AUTOMATION, side));
    }

    @NotNull
    private static IItemTransactor wrap(@Nullable ResourceHandler<ItemResource> handler) {
        if (handler == null) {
            return NoSpaceTransactor.INSTANCE;
        }
        return new ItemHandlerWrapper(handler);
    }

    /** Attempts to move up to {@code maxItems} from {@code src} to {@code dst}, one real (committed) step at a
     * time -- mirrors 1.12.2's own loop, which kept moving single items until nothing more would go.
     *
     * @return The number of items moved. */
    public static int move(IItemTransactor src, IItemTransactor dst, int maxItems) {
        int moved = 0;
        while (moved < maxItems) {
            int m;
            try (Transaction transaction = Transaction.openRoot()) {
                m = moveSingle(src, dst, maxItems - moved, transaction);
                if (m > 0) {
                    transaction.commit();
                }
            }
            if (m <= 0) {
                break;
            }
            moved += m;
        }
        return moved;
    }

    /** Moves at most one "peek" worth of items: simulates an extraction from {@code src} to see what is
     * available and acceptable to {@code dst}, offers exactly that to {@code dst}, then extracts for real only as
     * much as {@code dst} actually accepted. Matches 1.12.2's {@code moveSingle0}, rebuilt on nested
     * {@link Transaction}s instead of a {@code boolean simulate} parameter. */
    private static int moveSingle(IItemTransactor src, IItemTransactor dst, int maxItems, Transaction transaction) {
        ResourceStack<ItemResource> potential;
        try (Transaction peek = Transaction.open(transaction)) {
            // Pre-filter by what dst can accept, exactly like 1.12.2's `dst::canPartiallyAccept` reference --
            // otherwise a src slot dst can't take would block every other slot behind it in this call.
            IStackFilter destAccepts = stack -> dst.canPartiallyAccept(ItemResource.of(stack), stack.getCount(), peek);
            potential = src.extract(destAccepts, 1, maxItems, peek);
            // Never committed: this is only a peek at what src could give up.
        }
        if (potential == null || potential.isEmpty()) {
            return 0;
        }

        ItemResource resource = potential.resource();
        int inserted = dst.insert(resource, potential.amount(), false, transaction);
        if (inserted <= 0) {
            return 0;
        }

        IStackFilter exact = stack -> resource.equals(ItemResource.of(stack));
        ResourceStack<ItemResource> taken = src.extract(exact, inserted, inserted, transaction);
        int takenAmount = taken == null ? 0 : taken.amount();
        if (takenAmount != inserted) {
            throw new IllegalStateException(
                "One of the two transactors (either src = " + src.getClass() + " or dst = " + dst.getClass() + ")"
                    + " didn't respect the movement flags! ( potential = " + potential + ", inserted = " + inserted
                    + ", taken = " + taken + " )"
            );
        }
        return takenAmount;
    }
}
