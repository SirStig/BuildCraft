/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.tools;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/** Implement this interface on an Item subclass to have that item work as a wrench for BuildCraft. */
public interface IToolWrench {

    /**
     * Called to ensure that the wrench can be used.
     *
     * @param player The player doing the wrenching.
     * @param hand Which hand was holding the wrench.
     * @param wrench The item stack that holds the wrench.
     * @param hit The object that is being wrenched.
     * @return True if wrenching is allowed, false if not.
     */
    boolean canWrench(Player player, InteractionHand hand, ItemStack wrench, HitResult hit);

    /**
     * Callback after the wrench has been used. This can be used to decrease durability or for other purposes.
     *
     * @param player The player doing the wrenching.
     * @param hand Which hand was holding the wrench.
     * @param wrench The item stack that holds the wrench.
     * @param hit The object that was wrenched.
     */
    void wrenchUsed(Player player, InteractionHand hand, ItemStack wrench, HitResult hit);
}
