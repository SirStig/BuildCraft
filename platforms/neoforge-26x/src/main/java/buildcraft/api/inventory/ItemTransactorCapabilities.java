/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.api.inventory;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * The item-transactor block capability, for Minecraft 26.x.
 *
 * <p>1.12.2's {@code CapUtil} declared two tokens: {@code CAP_ITEM_TRANSACTOR} (a BuildCraft-native
 * {@link IItemTransactor}) and {@code CAP_ITEMS} (the vanilla-interop {@code IItemHandler}, for cross-mod
 * compatibility). Only the first half needs declaring here. Checked via {@code javap} against the real NeoForge
 * universal jar ({@code net.neoforged.neoforge.capabilities.Capabilities$Item}): 26.x already ships its own
 * vanilla-interop capability, {@code Capabilities.Item.BLOCK}, typed
 * {@code BlockCapability<ResourceHandler<ItemResource>, Direction>} -- exactly the shape
 * {@link buildcraft.lib.tile.item.ItemHandlerManager#getHandlerForFace(Direction)} already returns (see that
 * class's own javadoc), and the same capability NeoForge itself registers for every vanilla container (chests,
 * hoppers, furnaces, ...) and for minecart-type entities (as {@code Capabilities.Item.ENTITY_AUTOMATION} --
 * confirmed by reading {@code CapabilityHooks} in the NeoForge sources jar). There is no {@code IItemHandler} on
 * this target for a second, parallel token to bridge to; declaring one here would just duplicate NeoForge's own
 * capability under a different name. So this class only ever needs to declare the BuildCraft-native half; the
 * vanilla-interop half is {@code Capabilities.Item.BLOCK}/{@code Capabilities.Item.ENTITY_AUTOMATION}, referenced
 * directly wherever it is needed ({@link buildcraft.lib.inventory.ItemTransactorHelper}).
 */
public final class ItemTransactorCapabilities {

    /** Kept local rather than referencing the mod class, so the api package stays self-contained. */
    private static final String NAMESPACE = "buildcraft";

    public static final BlockCapability<IItemTransactor, Direction> ITEM_TRANSACTOR =
        BlockCapability.createSided(Identifier.fromNamespaceAndPath(NAMESPACE, "item_transactor"), IItemTransactor.class);

    private ItemTransactorCapabilities() {}
}
