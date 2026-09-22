/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.api.inventory;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
 * The item-transactor block capability, for Minecraft 1.20.1.
 *
 * <p>1.12.2's {@code CapUtil} declared two tokens: {@code CAP_ITEM_TRANSACTOR} (a BuildCraft-native
 * {@link IItemTransactor}) and {@code CAP_ITEMS} (the vanilla-interop {@code IItemHandler}). Only the first half
 * needs declaring here -- the vanilla-interop half is still {@link ForgeCapabilities#ITEM_HANDLER}, an existing
 * Forge-fork token that {@link buildcraft.lib.tile.item.ItemHandlerManager} already exposes (see that class's
 * own javadoc) and {@link buildcraft.lib.inventory.ItemTransactorHelper} already knows how to query, so there is
 * nothing left for this class to declare a second time.
 *
 * <p>The 26.x target has a class of the same name, but the two cannot be shared: 1.20.1 still uses Forge's
 * {@code Capability} looked up through a {@link CapabilityToken}, whereas 26.x uses NeoForge's
 * {@code BlockCapability} keyed by an {@code Identifier} -- see {@code MjCapabilities}' own javadoc for the same
 * split, applied here to a single capability instead of five.
 */
public final class ItemTransactorCapabilities {

    public static final Capability<IItemTransactor> ITEM_TRANSACTOR =
        CapabilityManager.get(new CapabilityToken<>() {});

    private ItemTransactorCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(IItemTransactor.class);
    }
}
