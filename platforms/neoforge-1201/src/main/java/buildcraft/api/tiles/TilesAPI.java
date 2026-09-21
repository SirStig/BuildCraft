/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.tiles;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
 * The block entity capabilities, for Minecraft 1.20.1.
 *
 * <p>1.12.2 built these through {@code CapabilitiesHelper.registerCapability(Class)}, a BuildCraft helper that
 * supplied the no-op storage and null factory the old system demanded but never used. Neither argument exists any
 * more, so the helper is not ported.
 *
 * <p>The 26.x target has a class of the same name that cannot be shared with this one: it uses NeoForge's
 * {@code BlockCapability} keyed by an {@code Identifier}, whereas 1.20.1 uses Forge's {@link Capability} looked up
 * through a {@link CapabilityToken} and declared in {@link #register(RegisterCapabilitiesEvent)}. This mirrors
 * {@code buildcraft.api.mj.MjCapabilities}.
 */
public final class TilesAPI {

    public static final Capability<IControllable> CONTROLLABLE =
        CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<IHasWork> HAS_WORK =
        CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<IHeatable> HEATABLE =
        CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<ITileAreaProvider> TILE_AREA_PROVIDER =
        CapabilityManager.get(new CapabilityToken<>() {});

    private TilesAPI() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(IControllable.class);
        event.register(IHasWork.class);
        event.register(IHeatable.class);
        event.register(ITileAreaProvider.class);
    }
}
