/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.tiles;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * The block entity capabilities, for Minecraft 26.x.
 *
 * <p>1.12.2 built these through {@code CapabilitiesHelper.registerCapability(Class)}, a BuildCraft helper that
 * wrapped {@code CapabilityManager.INSTANCE.register} with a no-op storage and a null factory so callers did not
 * have to supply the two arguments the old system demanded but never used. Neither argument exists any more, so
 * the helper has nothing left to do and is not ported; these follow the same shape as
 * {@code buildcraft.api.mj.MjCapabilities}.
 *
 * <p>They are sided because a machine can expose different behaviour per face, and because a
 * {@link BlockCapability} must be created as either sided or not at declaration time.
 */
public final class TilesAPI {

    /** Kept local rather than referencing the mod class, so the api package stays self-contained. */
    private static final String NAMESPACE = "buildcraft";

    public static final BlockCapability<IControllable, Direction> CONTROLLABLE =
        createSided("controllable", IControllable.class);

    public static final BlockCapability<IHasWork, Direction> HAS_WORK =
        createSided("has_work", IHasWork.class);

    public static final BlockCapability<IHeatable, Direction> HEATABLE =
        createSided("heatable", IHeatable.class);

    public static final BlockCapability<ITileAreaProvider, Direction> TILE_AREA_PROVIDER =
        createSided("tile_area_provider", ITileAreaProvider.class);

    private TilesAPI() {
    }

    private static <T> BlockCapability<T, Direction> createSided(String path, Class<T> type) {
        return BlockCapability.createSided(Identifier.fromNamespaceAndPath(NAMESPACE, path), type);
    }
}
