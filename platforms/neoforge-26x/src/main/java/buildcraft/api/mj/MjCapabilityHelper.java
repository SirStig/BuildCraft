/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Registers every MJ capability a block entity supports, in one call.
 *
 * <p>This is a restructure rather than a port. In 1.12.2 {@code MjCapabilityHelper} was an
 * {@code ICapabilityProvider}: a machine held an instance and delegated {@code getCapability} to it, and the
 * helper worked out which of the five MJ interfaces the machine implemented with a chain of {@code instanceof}
 * at construction time.
 *
 * <p>There is no attach-by-provider path on 26.x. A capability is registered against a
 * {@link BlockEntityType} up front, in {@link RegisterCapabilitiesEvent}, with a lookup function called per
 * query. So the {@code instanceof} chain moves from construction to registration -- it now decides which
 * capabilities to register at all, rather than which to answer -- and the result is strictly better: a machine
 * that is not an {@link IMjReceiver} no longer has a receiver capability that returns null.
 *
 * <p>Call it from your {@code RegisterCapabilitiesEvent} listener:
 *
 * <pre>{@code
 * MjCapabilityHelper.registerAll(event, BCCoreBlockEntities.ENGINE.get());
 * }</pre>
 */
public final class MjCapabilityHelper {

    private MjCapabilityHelper() {
    }

    /**
     * Registers each MJ capability that {@code BE} actually implements, plus the Forge energy bridge when MJ to
     * RF auto-conversion is enabled.
     *
     * <p>The block entity class must implement {@link IMjConnector}; the other four are optional and detected
     * per instance, because a machine may expose different interfaces depending on its state.
     */
    public static <BE extends BlockEntity> void registerAll(
        RegisterCapabilitiesEvent event,
        BlockEntityType<BE> type
    ) {
        event.registerBlockEntity(MjCapabilities.CONNECTOR, type, (be, side) -> as(be, IMjConnector.class));
        event.registerBlockEntity(MjCapabilities.RECEIVER, type, (be, side) -> as(be, IMjReceiver.class));
        event.registerBlockEntity(
            MjCapabilities.REDSTONE_RECEIVER, type, (be, side) -> as(be, IMjRedstoneReceiver.class)
        );
        event.registerBlockEntity(MjCapabilities.READABLE, type, (be, side) -> as(be, IMjReadable.class));
        event.registerBlockEntity(
            MjCapabilities.PASSIVE_PROVIDER, type, (be, side) -> as(be, IMjPassiveProvider.class)
        );

        event.registerBlockEntity(Capabilities.Energy.BLOCK, type, (be, side) -> {
            if (!IMjToRfStatus.get().isAutoconvertEnabled()) {
                return null;
            }
            IMjConnector connector = as(be, IMjConnector.class);
            if (connector == null) {
                return null;
            }
            return new MjToRfAutoConvertor(connector);
        });
    }

    @Nullable
    private static <T> T as(BlockEntity be, Class<T> type) {
        return type.isInstance(be) ? type.cast(be) : null;
    }
}
