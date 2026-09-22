/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFlowType;

import buildcraft.lib.registry.BCRegistry;

import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.PipeRegistry;
import buildcraft.transport.pipe.behaviour.PipeBehaviourCobble;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Registrations belonging to the old {@code buildcrafttransport} module -- the first ones, and the first pipe
 * batch. Mirrors {@code BCFactoryRegistries}/{@code BCEnergyRegistries}' exact established structure: its own
 * private {@link BCRegistry}, block/item/block-entity registration, a {@code registerCapabilities}.
 *
 * <p><b>The one-block-many-items architecture, made concrete.</b> There is exactly one block
 * ({@link #PIPE_HOLDER}) and one block entity type ({@link #PIPE_HOLDER_TYPE}) here, matching 1.12.2's own real
 * design (see {@code common/buildcraft/transport/pipe/PipeRegistry.java}/{@code Pipe.java}) -- a future pipe
 * material is just another {@link PipeDefinition} plus another {@link ItemPipeHolder} instance registered here,
 * never a new block or tile class.
 *
 * <p>Static init order matters in this class and is deliberate: {@link #PIPE_API_INIT} (a static initializer
 * block, not a field) wires {@link PipeApi#pipeRegistry}/{@link PipeApi#flowItems} <em>before</em>
 * {@link #PIPE_COBBLESTONE} is built, because {@code PipeDefinition.PipeDefinitionBuilder#define()} calls
 * {@code PipeApi.pipeRegistry.registerPipe(...)} immediately, and {@code .flowItem()} reads
 * {@code PipeApi.flowItems} immediately -- both would NPE if this ran in the other order. Static fields and
 * blocks run top-to-bottom in one class, so the ordering below is load-bearing, not decorative.
 *
 * <p><b>{@code canBeColoured} is {@code false} on {@link #PIPE_COBBLESTONE}, a deliberate scope choice, not a
 * faithful copy of the original's own value.</b> Verified, not guessed: 1.12.2's real
 * {@code common/buildcraft/transport/BCTransportPipes.java} calls {@code builder.builder.enableColouring()}
 * once, right after defining the structure pipe, and that flag then stays set on the shared builder for every
 * pipe defined afterwards -- wood, stone, cobblestone, quartz, gold, and so on -- so the real 1.12.2 cobblestone
 * pipe <em>is</em> colourable. This batch disables it anyway: colouring a pipe means applying a dye, which needs
 * {@code CustomPaintHelper}-style GUI/interaction plumbing this batch does not add for pipes specifically (dye
 * colours on a pipe are explicitly out of scope -- see this module's own scope notes), so leaving
 * {@code canBeColoured} true would advertise a feature ({@code IPipe#setColour}) nothing in this batch can ever
 * legitimately trigger.
 *
 * <p>The id/texture-prefix naming also deliberately diverges from 1.12.2's own {@code "cobblestone_item"} (which
 * needed the {@code _item} suffix to stay unique alongside sibling {@code cobblestone_fluid}/
 * {@code cobblestone_power}/{@code cobblestone_rf} definitions sharing the same material prefix). This batch
 * registers exactly one cobblestone pipe kind, so the plain {@code "cobblestone"} id has nothing to collide
 * with.
 */
public final class BCTransportRegistries {

    private BCTransportRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    static {
        PipeApi.pipeRegistry = PipeRegistry.INSTANCE;
        PipeApi.flowItems = new PipeFlowType(PipeFlowItems::new, PipeFlowItems::new);
    }

    /** The cobblestone pipe's own {@link PipeDefinition} -- see this class's own javadoc for the
     * {@code canBeColoured}/naming notes. */
    public static final PipeDefinition PIPE_COBBLESTONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("cobblestone")
        .logic(PipeBehaviourCobble::new, PipeBehaviourCobble::new)
        .flowItem()
        .disableColouring()
        .define();

    /** Default properties match what {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft
     * block, the same reasoning already worked out for {@code BlockDecoration}/{@code BlockEngineWood}/
     * {@code BlockChute} -- see {@code BCFactoryRegistries#CHUTE}'s own javadoc. A plain full cube, matching
     * {@code BlockTank}/{@code BlockPump}'s own "no renderer yet" precedent -- see {@link BlockPipeHolder}'s own
     * javadoc. No {@code addBlockAndItem}: this block's item is the pipe-specific {@link ItemPipeHolder} below,
     * not an auto-generated plain {@code BlockItem}. */
    public static final DeferredBlock<BlockPipeHolder> PIPE_HOLDER = REGISTRY.addBlock(
        "pipe_holder", BlockPipeHolder::new,
        properties -> properties
            .mapColor(MapColor.STONE)
            .strength(0.25F, 3.0F)
            .sound(SoundType.STONE)
            .noOcclusion()
            .requiresCorrectToolForDrops()
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TilePipeHolder>> PIPE_HOLDER_TYPE =
        REGISTRY.addBlockEntity("pipe_holder", TilePipeHolder::new, PIPE_HOLDER);

    /** The one item registered in this batch: the cobblestone pipe's own placeable item, tagged with
     * {@link #PIPE_COBBLESTONE}. Referencing {@code PIPE_HOLDER.get()} inside this factory is safe despite
     * running before {@link #PIPE_HOLDER} is actually bound: {@link DeferredRegister.Items}' own factory is not
     * invoked until the item registry event fires, by which point the block registry (registered first, same
     * pattern {@code BCRegistry#addBlockAndItem}'s own {@code registerSimpleBlockItem} already relies on) has
     * already bound every {@code DeferredBlock}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_COBBLESTONE = REGISTRY.addItem(
        "pipe_item_cobblestone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_COBBLESTONE)
    );

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCTransportRegistries::registerCapabilities);
    }

    /** Wires the vanilla-interop item capability so a neighbouring machine (a real hopper, an already-ported
     * chute, ...) can push/pull items through the pipe's own ends -- matching
     * {@code BCFactoryRegistries#registerCapabilities}'s own {@code CHUTE_TYPE} precedent. The actual
     * {@code ResourceHandler<ItemResource>} implementation lives on {@code PipeFlowItems} itself (see that
     * class's own {@code getCapability} javadoc for why), reached generically through
     * {@code TilePipeHolder#getCapability} -> {@code Pipe#getCapability} -> {@code PipeBehaviour}/
     * {@code PipeFlow#getCapability}, the same dispatch chain {@link buildcraft.api.transport.pipe.IPipe}'s own
     * javadoc describes. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Item.BLOCK, PIPE_HOLDER_TYPE.get(),
            (tile, side) -> tile.getCapability(Capabilities.Item.BLOCK, side)
        );

        // PipeApi.CAP_PIPE_HOLDER/CAP_PIPE/CAP_PLUG expose the tile/pipe/pluggable objects themselves -- this
        // is what IPipe#getConnectedPipe/IPipeHolder#getNeighbourPipe (via Level#getCapability) actually rely
        // on to detect a *neighbouring pipe* as a pipe (as opposed to a plain tile), which in turn is what makes
        // Pipe#canPipesConnect ever get a chance to run between two adjacent pipe segments at all. Direct
        // 26.x-shaped equivalent of 1.12.2's own TilePipeHolder constructor
        // (caps.addCapabilityInstance(CAP_PIPE_HOLDER, this, ...); caps.addCapability(CAP_PIPE, this::getPipe,
        // ...); caps.addCapability(CAP_PLUG, this::getPluggable, ...)).
        event.registerBlockEntity(PipeApi.CAP_PIPE_HOLDER, PIPE_HOLDER_TYPE.get(), (tile, side) -> tile);
        event.registerBlockEntity(PipeApi.CAP_PIPE, PIPE_HOLDER_TYPE.get(), (tile, side) -> tile.getPipe());
        event.registerBlockEntity(PipeApi.CAP_PLUG, PIPE_HOLDER_TYPE.get(), (tile, side) -> tile.getPluggable(side));
    }
}
