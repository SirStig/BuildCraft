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

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.items.IItemHandler;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.inventory.ItemTransactorCapabilities;

import buildcraft.lib.misc.StackUtil;

/**
 * Trimmed port of 1.12.2's {@code ItemTransactorHelper}: only {@code getTransactor}/{@code move}, which is all
 * {@code buildcraft.factory.tile.TileChute} (this file's only caller so far) actually needs. See the 26.x copy of
 * this class for why {@code getInjectable}/{@code wrapInjectable}/{@code insertAllBypass} and
 * {@code createDroppingTransactor} are dropped -- the same reasoning applies unchanged here.
 *
 * <p>Unlike 26.x, this target still has {@link ICapabilityProvider}, implemented by both {@code BlockEntity} and
 * {@code Entity} (confirmed via {@code javap}: both extend {@code CapabilityProvider}), so 1.12.2's single
 * {@code getTransactor(ICapabilityProvider, Direction)} signature survives unchanged -- there is no need to split
 * it into a block-entity overload and an entity overload the way 26.x does. {@code CapUtil.CAP_ITEM_TRANSACTOR}/
 * {@code CAP_ITEMS} become {@link ItemTransactorCapabilities#ITEM_TRANSACTOR}/{@link ForgeCapabilities#ITEM_HANDLER}
 * respectively, and the result is unwrapped from a {@code LazyOptional} instead of returned as a plain
 * (possibly-null) reference.
 */
public final class ItemTransactorHelper {

    private ItemTransactorHelper() {}

    @NotNull
    public static IItemTransactor getTransactor(@Nullable ICapabilityProvider provider, Direction face) {
        if (provider == null) {
            return NoSpaceTransactor.INSTANCE;
        }

        IItemTransactor trans = provider.getCapability(ItemTransactorCapabilities.ITEM_TRANSACTOR, face).orElse(null);
        if (trans != null) {
            return trans;
        }

        IItemHandler handler = provider.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
        if (handler == null) {
            if (provider instanceof WorldlyContainer sided) {
                return new SidedInventoryWrapper(sided, face);
            }
            if (provider instanceof Container container) {
                return new InventoryWrapper(container);
            }
            return NoSpaceTransactor.INSTANCE;
        }
        if (handler instanceof IItemTransactor transactor) {
            return transactor;
        }
        return new ItemHandlerWrapper(handler);
    }

    /** Attempts to move up to {@code maxItems} from {@code src} to {@code dst}.
     *
     * @return The number of items moved. */
    public static int move(IItemTransactor src, IItemTransactor dst, int maxItems) {
        int moved = 0;
        while (true) {
            int m = moveSingle0(src, dst, maxItems - moved);
            if (m == 0) {
                break;
            } else {
                moved += m;
            }
        }
        return moved;
    }

    private static int moveSingle0(IItemTransactor src, IItemTransactor dst, int maxItems) {
        IStackFilter filter = dst::canPartiallyAccept;
        ItemStack potential = src.extract(filter, 1, maxItems, true);
        if (potential.isEmpty()) return 0;
        ItemStack leftOver = dst.insert(potential, false, false);
        int toTake = potential.getCount() - leftOver.getCount();
        IStackFilter exactFilter = stack -> StackUtil.canMerge(stack, potential);
        ItemStack taken = src.extract(exactFilter, toTake, toTake, false);
        if (taken.getCount() != toTake) {
            String msg = "One of the two transactors (either src = ";
            msg += src.getClass() + " or dst = " + dst.getClass() + ")";
            msg += " didn't respect the movement flags! ( potential = " + potential;
            msg += ", leftOver = " + leftOver + ", taken = " + taken;
            msg += ", count = " + toTake + " )";
            throw new IllegalStateException(msg);
        }
        return toTake;
    }
}
