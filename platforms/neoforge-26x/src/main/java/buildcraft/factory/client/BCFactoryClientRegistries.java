/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.factory.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.factory.gui.GuiAutoCraftFluids;
import buildcraft.factory.gui.GuiAutoCraftItems;
import buildcraft.factory.tile.RenderTileTank;

import buildcraft.BCFactoryRegistries;

/**
 * Client-only menu screen and renderer registration for {@code buildcraft.factory}.
 *
 * <p>Kept in its own class, referenced <em>only</em> from behind a {@code FMLEnvironment.getDist().isClient()}
 * guard in {@code BuildCraft}'s constructor -- never unconditionally. This mirrors, but is not identical to, the
 * {@code MessageMarker.ClientPlayerLookup} precedent documented in PORTING.md's "Build and packaging gotchas"
 * section: there, one client-only field read had to be isolated into a lazily-loaded nested class because the
 * surrounding method had to run, and be verified, on both sides regardless. Here the whole *registration itself*
 * is inherently client-only (a dedicated server never needs a {@code Screen} or a {@code BlockEntityRenderer}), so
 * the correct, coarser fix is to never let a dedicated server reach this class at all -- if it only ever ran
 * through this guard, the bytecode verifier never has a reason to resolve {@link GuiAutoCraftItems} (a client-only
 * type, since {@code Screen} and everything it touches doesn't exist on a dedicated server) in the first place.
 * {@link #registerRenderers} follows the identical rule for {@link RenderTileTank}, matching
 * {@code buildcraft.energy.client.BCEnergyClientRegistries#registerRenderers}.
 */
public final class BCFactoryClientRegistries {

    private BCFactoryClientRegistries() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCFactoryRegistries.AUTO_WORKBENCH_ITEMS_MENU.get(), GuiAutoCraftItems::new);
        event.register(BCFactoryRegistries.AUTO_WORKBENCH_FLUIDS_MENU.get(), GuiAutoCraftFluids::new);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCFactoryRegistries.TANK_TYPE.get(), RenderTileTank::new);
    }
}
