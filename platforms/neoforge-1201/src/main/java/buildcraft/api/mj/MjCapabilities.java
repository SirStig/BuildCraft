/** Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license,
 * which should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.mj;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
 * The MJ capabilities, for Minecraft 1.20.1.
 *
 * <p>The 26.x target has a class of the same name, but the two cannot be shared: 1.20.1 still uses Forge's
 * {@code Capability} looked up through a {@link CapabilityToken}, whereas 26.x uses NeoForge's
 * {@code BlockCapability} keyed by an {@code Identifier}. 1.20.1 also lost {@code @CapabilityInject} (which is what
 * the 1.12.2 code used) in the 1.16 rewrite, so each capability has to be declared in
 * {@link #register(RegisterCapabilitiesEvent)}.
 */
public final class MjCapabilities {

    public static final Capability<IMjConnector> CONNECTOR =
        CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<IMjReceiver> RECEIVER =
        CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<IMjRedstoneReceiver> REDSTONE_RECEIVER =
        CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<IMjReadable> READABLE =
        CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<IMjPassiveProvider> PASSIVE_PROVIDER =
        CapabilityManager.get(new CapabilityToken<>() {});

    private MjCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(IMjConnector.class);
        event.register(IMjReceiver.class);
        event.register(IMjRedstoneReceiver.class);
        event.register(IMjReadable.class);
        event.register(IMjPassiveProvider.class);
    }
}
