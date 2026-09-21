/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.inventory.IItemTransactor.IItemExtractable;

/**
 * Lets an in-flight {@link AbstractArrow} be picked up through {@link IItemTransactor}.
 *
 * <p>{@code AbstractArrow} moved into its own {@code net.minecraft.world.entity.projectile.arrow} package on
 * this target -- 1.20.1 still has it directly under {@code .projectile} -- and {@code EntityArrow.PickupStatus}
 * is now {@link AbstractArrow.Pickup} on both.
 *
 * <p>1.12.2 could not call the protected {@code getArrowStack()}, so it fell back to guessing the item from the
 * entity's Java type (a spectral arrow or a plain one, tipped/potion arrows unhandled -- see the {@code FIXME}
 * this replaces). This target exposes {@link AbstractArrow#getPickupItemStackOrigin()} publicly, which is the
 * entity's real, already-correct item form (potion contents and all), so the guesswork is gone rather than
 * carried over.
 *
 * <p>An arrow is a single indivisible pickup, not a slotted container, so unlike
 * {@link TransactorEntityItem} there is no partial stack to snapshot -- what needs to be revertible is only
 * "has this arrow already been claimed". That is tracked as a plain boolean through {@link SnapshotJournal};
 * the entity itself is only actually removed from the world in {@link #onRootCommit}, which fires once the
 * *root* transaction commits for real -- exactly the "irreversible action" case the class is documented for.
 */
public class TransactorEntityArrow extends SnapshotJournal<Boolean> implements IItemExtractable {

    private final AbstractArrow entity;
    private boolean claimed;

    public TransactorEntityArrow(AbstractArrow entity) {
        this.entity = entity;
    }

    @Override
    protected Boolean createSnapshot() {
        return claimed;
    }

    @Override
    protected void revertToSnapshot(Boolean snapshot) {
        claimed = snapshot;
    }

    @Override
    protected void onRootCommit(Boolean originalState) {
        if (claimed) {
            entity.discard();
        }
    }

    @Nullable
    @Override
    public ResourceStack<ItemResource> extract(@Nullable IStackFilter filter, int min, int max, TransactionContext transaction) {
        if (claimed || entity.isRemoved() || entity.pickup != AbstractArrow.Pickup.ALLOWED || min > 1 || max < 1 || max < min) {
            return null;
        }

        ItemStack stack = entity.getPickupItemStackOrigin().copy();
        ItemResource resource = ItemResource.of(stack);
        if (!IItemTransactor.matches(filter, resource)) {
            return null;
        }

        updateSnapshots(transaction);
        claimed = true;
        return new ResourceStack<>(resource, stack.getCount());
    }
}
