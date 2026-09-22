/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

import buildcraft.api.transport.IInjectable;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFlowType;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.registry.BCRegistry;

import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.PipeRegistry;
import buildcraft.transport.pipe.behaviour.PipeBehaviourCobble;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWood;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Registrations belonging to the old {@code buildcrafttransport} module -- the first ones, and the first pipe
 * batch. Mirrors {@code BCFactoryRegistries}/{@code BCEnergyRegistries}' exact established structure. See the
 * 26.x copy of this class for the full account of the one-block-many-items architecture, why
 * {@code canBeColoured} is {@code false} despite the real 1.12.2 cobblestone pipe being colourable, and why the
 * id/texture-prefix naming deliberately diverges from 1.12.2's own {@code "cobblestone_item"} -- unchanged here.
 *
 * <p>Unlike 26.x, 1.20.1 still has {@code ICapabilityProvider}, so {@code TilePipeHolder} exposes its own
 * capabilities directly (see that class's own javadoc) rather than through a
 * {@code RegisterCapabilitiesEvent}-registered lambda here. What this target still needs declaring, because
 * 1.20.1 lost {@code @CapabilityInject}, is the four {@link PipeApi} capability tokens themselves
 * ({@link #registerCapabilities}) -- matching {@code MjCapabilities}/{@code TilesAPI}'s own precedent.
 */
public final class BCTransportRegistries {

    private BCTransportRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    static {
        PipeApi.pipeRegistry = PipeRegistry.INSTANCE;
        PipeApi.flowItems = new PipeFlowType(PipeFlowItems::new, PipeFlowItems::new);
    }

    /** The cobblestone pipe's own {@link PipeDefinition} -- see the 26.x copy of this class's own javadoc for
     * the {@code canBeColoured}/naming notes. */
    public static final PipeDefinition PIPE_COBBLESTONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("cobblestone")
        .logic(PipeBehaviourCobble::new, PipeBehaviourCobble::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The wooden pipe's own {@link PipeDefinition} -- see the 26.x copy of this class's own javadoc for the
     * full account of why no new block/tile code was needed for this, and why {@code canBeColoured} is
     * {@code false}. */
    public static final PipeDefinition PIPE_WOOD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("wood")
        .logic(PipeBehaviourWood::new, PipeBehaviourWood::new)
        .flowItem()
        .disableColouring()
        .define();

    /** Default properties match what {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft
     * block -- see {@code BCFactoryRegistries#CHUTE}'s own javadoc. A plain full cube, matching
     * {@code BlockTank}/{@code BlockPump}'s own "no renderer yet" precedent. No {@code addBlockAndItem}: this
     * block's item is the pipe-specific {@link ItemPipeHolder} below. */
    public static final RegistryObject<BlockPipeHolder> PIPE_HOLDER = REGISTRY.addBlock(
        "pipe_holder",
        () -> new BlockPipeHolder(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(0.25F, 3.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
                .requiresCorrectToolForDrops()
        )
    );

    public static final RegistryObject<BlockEntityType<TilePipeHolder>> PIPE_HOLDER_TYPE =
        REGISTRY.addBlockEntity("pipe_holder", TilePipeHolder::new, PIPE_HOLDER);

    /** The one item registered in this batch: the cobblestone pipe's own placeable item, tagged with
     * {@link #PIPE_COBBLESTONE}. Referencing {@code PIPE_HOLDER.get()} inside this factory is safe for the same
     * reason it is on 26.x -- see that class's own javadoc. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_COBBLESTONE = REGISTRY.addItem(
        "pipe_item_cobblestone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_COBBLESTONE)
    );

    /** The wooden pipe's own placeable item, tagged with {@link #PIPE_WOOD}. No capability-registration changes
     * were needed for this on 1.20.1 -- {@code TilePipeHolder#getCapability} already falls through generically
     * to {@code pipe.getCapability}, which is how {@code PipeBehaviourWood}'s own {@code MjCapabilityHelper}
     * gets reached with zero changes here. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_WOOD = REGISTRY.addItem(
        "pipe_item_wood",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_WOOD)
    );

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCTransportRegistries::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IPipeHolder.class);
        event.register(IPipe.class);
        event.register(PipePluggable.class);
        event.register(IInjectable.class);
    }
}
