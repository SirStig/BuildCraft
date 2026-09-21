/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.api.mj;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * The MJ block capabilities, for Minecraft 26.x.
 *
 * <p>In 1.12.2 these were {@code Capability} instances held on {@code MjAPI} and obtained by annotating a field with
 * {@code @CapabilityInject}. NeoForge replaced that with {@link BlockCapability}, which is looked up against a
 * {@code (Level, BlockPos, Direction)} and must be registered per block entity type in
 * {@code RegisterCapabilitiesEvent} -- there is no attach-by-event path any more. They are sided because BuildCraft
 * machines expose different behaviour per face (an engine provides power on its output face only).
 *
 * <p>Note {@code ResourceLocation} is named {@code Identifier} on 26.x; the 1.20.1 target still calls it
 * {@code ResourceLocation}, which is why this class is not shared.
 */
public final class MjCapabilities {

    /** Kept local rather than referencing the mod class, so the api package stays self-contained. */
    private static final String NAMESPACE = "buildcraft";

    public static final BlockCapability<IMjConnector, Direction> CONNECTOR =
        createSided("mj_connector", IMjConnector.class);

    public static final BlockCapability<IMjReceiver, Direction> RECEIVER =
        createSided("mj_receiver", IMjReceiver.class);

    public static final BlockCapability<IMjRedstoneReceiver, Direction> REDSTONE_RECEIVER =
        createSided("mj_redstone_receiver", IMjRedstoneReceiver.class);

    public static final BlockCapability<IMjReadable, Direction> READABLE =
        createSided("mj_readable", IMjReadable.class);

    public static final BlockCapability<IMjPassiveProvider, Direction> PASSIVE_PROVIDER =
        createSided("mj_passive_provider", IMjPassiveProvider.class);

    private MjCapabilities() {}

    private static <T> BlockCapability<T, Direction> createSided(String path, Class<T> type) {
        return BlockCapability.createSided(Identifier.fromNamespaceAndPath(NAMESPACE, path), type);
    }
}
