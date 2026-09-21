# Porting BuildCraft to NeoForge

BuildCraft 8.0.1 targets Minecraft 1.12.2 and Forge 14.23. This branch is porting it to
NeoForge as **BuildCraft 10** (version `10.0.0-alpha`), on two targets:

| Target | Gradle project | Loader artifact | Java | Status |
| --- | --- | --- | --- | --- |
| Minecraft 26.1 / 26.2 / **26.3** | `:neoforge-26x` | `net.neoforged:neoforge` | 25 | primary |
| Minecraft 1.20.1 | `:neoforge-1201` | `net.neoforged:forge` | 17 | compatibility |

26.x is where the work goes first. 1.20.1 is kept because that is where most of the
modding community's mod branches still sit.

> The upstream `BuildCraft/BuildCraft` branches named `8.0.x-1.16.5`, `8.0.x-1.18.2` and
> `8.0.x-1.20.1` are **empty placeholders** — all three point at the same August 2023
> commit, whose `build.properties` still says `mc_version=1.12.2`. There is no upstream
> port to pick up; this branch starts from scratch.

## Layout

```
modules/expression/     Expression engine. No Minecraft references at all. DONE.
modules/shared/         Version-independent BuildCraft logic, shared by both targets.
platforms/neoforge-26x/ Minecraft 26.x port.
platforms/neoforge-1201/Minecraft 1.20.1 port.

common/                 The 1.12.2 source tree. NOT COMPILED -- this is the reference we
buildcraft_resources/   are porting from, and is deleted module by module as each one lands.
BuildCraftAPI/          git submodule; the 1.12.2 API, also being ported.
legacy/                 The old ForgeGradle 2 build files, kept for reference.
src_old_license/        Dead. It was already commented out of the 1.12.2 build.
```

The root project applies no `java` plugin on purpose, so the 1.12.2 trees at the repository
root are never fed to the compiler.

Anything that can live in `modules/shared` should: it is written once and both targets use
it. Everything that touches a Minecraft type is duplicated per platform, because the two
APIs are too far apart to bridge cheaply (see the table below) and the abstraction layer
would cost more than the duplication.

## Building

```bash
./gradlew build                          # everything
./gradlew :neoforge-26x:build            # Minecraft 26.3 (default)
./gradlew :neoforge-26x:build -Pbc.mc26=26.2
./gradlew :neoforge-1201:build           # Minecraft 1.20.1
./gradlew buildCommon                    # just the Minecraft-free modules
./gradlew :neoforge-26x:runClient        # launch the game
```

### Porting helpers

`misc/port/rename.py` applies the 1.12.2 → 26.x changes that are purely positional: package moves, type
renames with identical semantics, `setX` → `putX`, and the NBT getter mapping above. `misc/port/to1201.py`
takes a file already compiling on 26.x and produces the 1.20.1 copy, which is far less work than porting
from 1.12.2 twice.

Neither is a port. They know nothing about capabilities, the transfer API, stack NBT, rendering, block
metadata or the packet system — everything in the list below, in other words — so every file they touch
still has to be read, and the compiler is the real check. In practice they remove most of the noise and
leave the decisions.

### Testing in a real game

Dev runs (`runClient`/`runServer`) load `build/classes` directly. That is fine for most things but it does
not exercise the built jar, which is where the bundling gotchas below bite, so anything that needs real
testing goes into a Prism Launcher instance:

```bash
./gradlew installToPrism                 # both targets
./gradlew :neoforge-26x:installToPrism   # just Minecraft 26.3
./gradlew :neoforge-26x:installToPrism -Pbc.mc26=26.1
```

Each platform is mapped to the instance matching its Minecraft release and loader, and the task deletes any
previously installed BuildCraft jar first so two versions never load at once. Other mods in the instance are
left alone. Override with `-Pbc.prism.instance="<name>"` for a differently named instance, or
`-Pbc.prism.dir=<PrismLauncher data dir>` for a non-Flatpak install; the task lists the instances it can see
when it cannot find the one it wants.

Minecraft 26.x needs a **Java 25** toolchain — FancyModLoader 12 refuses to resolve against
anything older. Gradle will download one if it is not installed. Gradle itself must be 9.x;
8.x cannot drive a Java 25 toolchain.

## Progress

Ported (compiling, tested):

- `modules/expression` — all 71 hand-written files plus the 102 the generator produces.
  13 tests pass.
- `modules/shared` — 31 files: `buildcraft.lib.misc`, `buildcraft.lib.misc.data`,
  `buildcraft.lib.script`, plus `BCLog`, `BCDebugging` and `IConvertable` from the API.
  4 tests pass.
- `buildcraft.api.mj` — the MJ power API. The five interfaces, `MjAPI`'s constants and
  `MjBattery`'s arithmetic are shared; capabilities and the effect manager are per-platform.
  8 tests pass.
- Both platforms, at parity — mod entrypoint, the five gears, the creative tab,
  `MjCapabilities`, `IMjEffectManager`/`MjEffects`, `BCRegistry` (the registration layer),
  `TileBC` and `BlockBCTile` (the block entity and block bases), `VecUtil`, `RotationUtil`,
  and the power consumer tester -- the first machine to go all the way through: block, block
  entity, ticker, MJ capability, model, loot table and tag.
- **`BuildCraftAPI/api` — 217 of 251 files.** Everything except the list below, on both targets.
  Of the 34 not ported: 20 are `package-info.java` whose only content was FML's `@API`
  annotation, which no longer exists; the rest are blocked or deliberate, and listed below.
- `buildcraft.lib.nbt` (5), `.mj` (2), `.crops` (2), `.compat` (3), `.migrate` (2), `.fake` (1),
  `.json` (1), `buildcraft.lib.misc.{NBTUtilBC,StackUtil,InventoryUtil}`, and `BCLibConfig`,
  `IChunkLoadingTile`, `IBlockWithFacing`, `ILocalBlockUpdateSubscriber`,
  `buildcraft.lib.registry.PluggableRegistry` — the parts of `buildcraft.lib`'s foundation
  layer that turned out not to need the tile/net/block/item cluster below them.
  `InventoryUtil` is a partial port (the drop/spawn/`addAll`/`addToPlayer` helpers only) --
  see the `CapUtil`/`ItemTransactorHelper` entry below for the rest.
- `buildcraft.lib.misc.{ArrayUtil,MathUtil,TimeUtil,StringUtilBC,ObjectUtilBC,ModUtil,
  BoundingBoxUtil,EntityUtil,PermissionUtil,RegistryUtil,FakePlayerProvider,ChunkUtil,
  StackNbtMatcher,AdvancementUtil}` (both platforms). `StringUtilBC` is a partial port for the
  same reason `InventoryUtil` is: `formatStringForWhite`/`formatStringForBlack` and
  `compareBasicReadable` all need `ColourUtil`, which is not ported (and itself needs
  `BCLibConfig`, `LocaleUtil` and `SpecialColourFontRenderer`, none of which are ported
  either) -- see that class's own javadoc. `DebuggingTools`, the fifteenth file in this batch,
  is not ported at all: it exists solely to register a `WorldEventListenerAdapter`, which the
  "deliberately not ported" list below already explains is gone with nothing to replace it.
- `buildcraft.lib.tile.craft.IAutoCraft` (relocated to `buildcraft.lib.tile.item`, next to its
  only real dependency, `ItemHandlerSimple`).
- `buildcraft.lib.delta` (2, both platforms) — the render/GUI interpolation helper
  (`DeltaManager`/`DeltaInt`) used for smoothly animating a synced value between two known
  states. Self-contained (NBT + `PacketBufferBC` only); its only real consumer,
  `TileBC_Neptune`'s network/NBT wiring, is superseded by `TileBC` here, which doesn't wire a
  `DeltaManager` in yet -- that's `lib.net`'s message-dispatch redesign's job, not this
  package's.
- `buildcraft.lib.recipe` (8 of 12) and `buildcraft.lib.particle` (6) — recipe-adjacent
  helpers and `IEffect`-driven particle rendering. `OredictionaryNames` now points at
  BuildCraft's real published item tags rather than ore-dictionary names. The 4 skipped
  recipe files (`BCRecipeShaped`, `BCRecipeShapeless`, `IngredientNBTBC`,
  `RecipeBuilderShaped`) need the full datapack recipe redesign, deferred until a consuming
  module needs one.
- `buildcraft.lib.inventory` (27 of 27, both platforms) — `IItemTransactor`'s single
  concrete implementation, `AbstractInvItemTransactor`, plus every handler/entity/filter
  wrapper around it. On 26.x, `InventoryWrapper` wraps NeoForge's own
  `VanillaContainerWrapper`; on 1.20.1 it wraps `IItemHandler` directly, unchanged in shape
  from 1.12.2.
- `buildcraft.lib.tile.item` (both platforms) — the array-backed item handler every machine
  uses (`ItemHandlerSimple`), its manager (`ItemHandlerManager`), and the
  insert-only/extract-only/filtered wrapper family around it. On 26.x this is a rewrite
  against NeoForge's own `StacksResourceHandler`/`ItemStacksResourceHandler`, which already
  provides transaction safety and NBT persistence, letting `StackInsertionFunction` and
  `IItemHandlerAdv` be dropped entirely (see `ItemHandlerSimple`'s own javadoc for why). On
  1.20.1, which keeps `IItemHandler`, the shape stays close to 1.12.2's original --
  `StackInsertionFunction` is still dropped there too (checked against every 1.12.2 call
  site, it was only ever used via its two factory methods, never the general form), but
  `IItemHandlerAdv` and `CombinedItemHandlerWrapper` are kept since `IItemHandler` still
  exists for them to wrap. `ItemHandlerManager` keeps `ICapabilityProvider` on 1.20.1
  (matching `MjCapabilityHelper`'s precedent) and drops it entirely on 26.x, where
  capabilities are registered per block entity type instead.
- `buildcraft.lib.net.{PacketBufferBC,IPayloadWriter}` (both platforms) — the bit-packing
  `FriendlyByteBuf` subclass used by compact NBT/network encoding elsewhere in `lib`. The
  message dispatch it served in 1.12.2 (`IPayloadReceiver`, `MessageManager`) is not ported
  yet; both targets' networking layers differ enough from 1.12's single channel that design
  follows once there is a concrete message to register.
- `buildcraft.lib.misc.{LocaleUtil,PositionUtil,VolumeUtil,WorkerThreadUtil,ColourUtil,
  ProfilerUtil,JsonUtil,FluidUtilBC,ExpressionCompat}` (both platforms, all with the previous
  batch's `ColourUtil`/`StringUtilBC` gap now closed -- `ColourUtil` only needed `BCLibConfig`,
  `LocaleUtil` and `SpecialColourFontRenderer`, and only the last is still missing, worked
  around below). `WorkerThreadUtil`, `PositionUtil` and `VolumeUtil` are unchanged beyond the
  1.12.2 -> modern rename (see the new Method-rename-table rows for `BlockPos.relative`,
  `Direction.getClockWise` and `AxisDirection.getStep`). `LocaleUtil` swaps 1.12.2's `I18n`
  for `Language.getInstance()` (common code on both targets, unlike the client-only modern
  `I18n`) and `MjAPI.getRfConversion()` for `IMjToRfStatus.get().getConversion()`, the latter
  already ported as part of `buildcraft.api.mj`. `ColourUtil` hard-codes
  `useColouredLabels`/`useHighContrastLabelColours` to their 1.12.2 defaults rather than
  reading them from `BCLibConfig` (outside this batch's scope to extend) -- see its class
  javadoc -- and its `NAMES`/`DARK_HEX`/`LIGHT_HEX` arrays are reordered for `DyeColor`'s
  flipped ordinal order (see the new Method-renames-table note). On 26.x it also reproduces
  `ChatFormatting#isColor()` from the ordinal, since 26.x's `ChatFormatting` dropped that
  method entirely (new "differs between targets" table row). `ProfilerUtil` follows
  `Profiler`'s split into write-only `ProfilerFiller` and read-only `ProfileResults`/
  `ResultField` (new Method-renames-table rows), identically on both targets. `JsonUtil` keeps
  `FLUID_STACK_DESERIALIZER` on 1.20.1 only -- 26.x's `FluidResource` has no "resource +
  amount" carrier that stands in for a standalone `FluidStack` deserializer outside a
  `ResourceHandler` -- and both targets' NBT<->JSON adapters follow the `Tag`/`getAsX()` vs.
  `value()` split already in the "Primitive NBT tag payload" table row, plus
  `CompoundTag.keySet()` vs `getAllKeys()` (new table row). `FluidUtilBC` is a partial port on
  both: `pushFluidAround` needs `buildcraft.lib.fluid.Tank` and `CapUtil.CAP_FLUIDS` (neither
  ported, the latter already listed below as blocked), and `onTankActivated` needs
  `buildcraft.lib.misc.SoundUtil` (not ported by anyone yet); both are skipped with the reason
  in the class javadoc rather than guessed at. The kept methods (`mergeSameFluids`,
  `areFluidStackEqual`, `areFluidsEqual`, `move`) are a real per-platform fork on 26.x, rewired
  onto `ResourceHandler<FluidResource>` + `Transaction` (`move` walks slots directly instead
  of going through `IFluidHandlerAdv`, which has no role left once every handler is
  introspectable -- see `FluidFilters`). `ExpressionCompat` is a partial port: the
  `Controllable Mode` node type (`buildcraft.api.tiles.IControllable`, not ported) and the two
  GUI node types (`buildcraft.lib.gui.pos`, not ported) are omitted, along with the 1.12.2
  obfuscation-bug workaround (`BCLib.throwBadClass`) that guarded the former -- see its class
  javadoc.

Deliberately not ported, with reasons:

- `buildcraft.lib.registry.{RegistrationHelper,RegistryConfig,TagManager,CreativeTabManager}`,
  `buildcraft.lib.block.{BlockBCBase_Neptune,BlockBCTile_Neptune}`, and
  `buildcraft.lib.item.IItemBuildCraft` (and everything implementing it --
  `ItemBC_Neptune`/`ItemBlockBC_Neptune`/`ItemBlockBCMulti`) -- all of 1.12.2's per-module
  registration plumbing. This is not a porting gap: the port's `BCRegistry` (see "Both
  platforms, at parity" above) already replaced the whole `RegistrationHelper`/`TagManager`/
  `RegistryConfig` trio, and `TileBC`/`BlockBCTile` already replaced the Neptune base
  classes -- both say so in their own javadoc. `IItemBuildCraft`'s job (setting an
  unlocalised name, a registry name, a creative tab, and per-damage-value model variants) is
  covered by `BCRegistry` plus the lang/model JSON for everything except the model-variant
  half, which has nothing left to port: item damage no longer selects a model variant at
  all.
- `buildcraft.lib.misc.CapUtil`, `buildcraft.lib.inventory.ItemTransactorHelper`,
  `buildcraft.lib.tile.craft.WorkbenchCrafting` and `buildcraft.lib.misc.CraftingUtil` -- one
  blocked chain. `CapUtil`'s actual job (exposing `Capability<IItemHandler>`/
  `Capability<IFluidHandler>` tokens, plus a custom-registered `Capability<IItemTransactor>`)
  is entirely built on `@CapabilityInject`, removed well before either target; 1.20.1's two
  vanilla tokens already have a built-in replacement with no class of their own needed
  (`ForgeCapabilities.ITEM_HANDLER`, used directly by `ItemHandlerManager`), but the custom
  `IItemTransactor` capability still needs designing against `RegisterCapabilitiesEvent`
  (1.20.1) or `BlockCapability` (26.x) -- real work, not a rename, and nothing yet needs
  `IItemTransactor` to be a capability rather than just an interface. `ItemTransactorHelper`
  (capability lookup against an arbitrary neighbouring block entity) needs that token to
  exist. `WorkbenchCrafting` needs `InventoryUtil.addToBestAcceptor`, which needs
  `ItemTransactorHelper`; `CraftingUtil` (`GameRegistry.findRegistry(IRecipe.class)` ->
  `RecipeManager`/`RecipeType`) has no other consumer, so it waits for the same thing.
  `InventoryUtil`'s own capability-independent half (drop/spawn/`addAll`/`addToPlayer`) is
  ported already; see its entry above.
- `buildcraft.lib.cap.CapabilityHelper` -- a generic multi-capability-per-face
  `ICapabilityProvider` (the same "several capability instances behind one provider" idea
  `MjCapabilityHelper`/`ItemHandlerManager` each implement one-off for their own case, made
  reusable). Blocked entirely on 26.x for the same reason those two are restructured there:
  no `ICapabilityProvider` to implement. Mechanically portable on 1.20.1, but every consumer
  (`TileFiller`, `TileQuarry`, `TileMiner`, `TileLaser`, pipe behaviours, ...) lives in
  `buildcraft.core`/`builders`/`factory`/`silicon`/`transport`, all entirely unported; nothing
  to wire it into yet.
- `buildcraft.lib.prop.UnlistedNonNullProperty` -- implements Forge's old
  `IUnlistedProperty`, the extended-blockstate mechanism for carrying render-only data that
  isn't a real blockstate property. Confirmed absent from both targets' jars via `javap`;
  block entity renderers get their block entity directly now, so there's nothing left needing
  an unlisted property to smuggle data through the blockstate.
- `buildcraft.lib.list` (8 files) -- the item-list ("phantom item list") GUI's matching
  engine (`ListHandler`, `ListMatchHandler{Armor,Class,Fluid,OreDictionary,Tools}`,
  `ListOreDictionaryCache`, `VanillaListHandlers`). The `buildcraft.api.lists` interfaces it
  implements are already ported, but every real consumer (`ItemList_BC8`, the list GUI/
  container, `BCLib`'s registration) is in `buildcraft.core`, entirely unported, and
  `ListMatchHandlerOreDictionary` specifically needs redesigning around tags now that the ore
  dictionary is gone. Waits for a real consumer along with the rest of `core`.
- `buildcraft.lib.misc.BlockUtil` (555 lines) -- deferred whole rather than partially ported,
  because it bundles several genuinely separate redesigns rather than one mechanical port:
  - Its fluid-block cluster (`isFullFluidBlock`, `getFluid`/`getFluidWithFlowing`/
    `getFluidWithoutFlowing`, `drainBlock`) is built on `IFluidBlock`/`BlockFluidBase`/
    `BlockFluidClassic`/`BlockLiquid`/`FluidRegistry` -- a whole block-per-fluid-level
    architecture that no longer exists. Fluids are `FluidState` on any `BlockState` now
    (confirmed: `BlockBehaviour.BlockStateBase#getFluidState()`), a fundamentally different
    shape, not a rename.
  - `Block.getDrops` and `ForgeEventFactory.fireBlockHarvesting`, which
    `getItemStackFromBlock`/`harvestBlock` build on, are both gone. The static
    `Block.getDrops` replacement takes a `BlockState`/`ServerLevel`/`BlockPos`/
    `BlockEntity`, and optionally an `Entity` and a *new* `net.minecraft.world.item.
    ItemInstance` type (confirmed via `javap` against the 26.x jar) in place of the old
    `ItemStack` + fortune-int pair -- `ItemInstance` didn't exist in any version this port
    has touched so far and needs its own investigation before anything builds on it.
    `BlockEvent.BreakEvent` itself moved packages and names, to
    `net.neoforged.neoforge.event.level.block.BreakBlockEvent` (confirmed present in the
    26.x NeoForge jar; the harvesting-chance hook `fireBlockHarvesting` used to pair with
    doesn't appear to still exist under that name and needs its own search).
  - `computeBlockBreakPower` reads `buildcraft.core.BCCoreConfig`, which is in `buildcraft.
    core` -- entirely unported.
  - `getOtherDoubleChest` reads `TileEntityChest`'s `adjacentChestX/ZNeg/Pos` fields directly;
    modern double-chest merging goes through `DoubleBlockCombiner` and needs verifying from
    scratch, not assumed compatible.
  - `explodeBlock` hand-builds an `Explosion` and manually sends `SPacketExplosion` to nearby
    players; both the `Explosion` constructor shape and the packet class have almost
    certainly changed and need the same from-scratch verification.

  Everything else in the file (`breakBlock`/`harvestBlock`/`destroyBlock`/
  `getFakePlayerWithTool`, `canChangeBlock`, `getBlockHardnessMining`/`isUnbreakableBlock`/
  `isToughBlock`, the `getTileEntity`/`getBlockState` chunk-avoiding wrappers around the
  already-ported `CompatManager`, `useItemOnBlock`, `onComparatorUpdate`, and the
  blockstate-property comparison helpers) has no similar blocker and is a reasonable target
  for a focused follow-up pass once the fluid/drops/double-chest pieces above are actually
  designed, rather than split off today into a partial file that would need revisiting
  anyway.
- `buildcraft.lib.block.VanillaPaintHandlers` -- registered explicit paint handlers for
  vanilla glass, glass panes and terracotta, each a single block with a 16-value colour
  property in 1.12.2. All three are 16 separate blocks now, following the
  `<colour>_<suffix>` naming convention every vanilla colour family uses
  (`white_stained_glass`, `red_terracotta`, ...) -- which is exactly what
  `buildcraft.api.blocks.DyedBlockVariants` (see the api.blocks port) discovers generically.
  `CustomPaintHelper`'s default fallback already recolours all three without any
  block-specific registration; there is nothing left for this class to do.
- `buildcraft.lib.block.LocalBlockUpdateNotifier` and `buildcraft.lib.world.
  WorldEventListenerAdapter` -- both exist only to implement `IWorldEventListener`, vanilla's
  generic "notify me of every block change in this level" hook. It is not renamed, it is
  gone -- confirmed absent from the 26.x jars, with nothing that fires on every block change
  generically to replace it (NeoForge's `BlockEvent` variants are for specific things:
  drops, neighbour-notify, trample; none is "a block's state changed"). Nothing in the port
  calls either class yet, so this is deferred pending a real consumer to design the
  replacement against, rather than guessed at speculatively.
- `buildcraft.lib.chunkload.ChunkLoaderManager` -- 1.12.2's chunk-forcing API
  (`ForgeChunkManager.requestTicket`/`Ticket`) was redesigned into a
  `TicketHelper`/`LoadingValidationCallback` pair in Forge *before* 1.20.1 (confirmed: this
  target's jar already has the new shape), and redesigned again into a registered
  `TicketController` on 26.x. Needs rewriting on both targets, not porting on one and
  renaming on the other. `IChunkLoadingTile`, the pure-data interface machines implement to
  ask for chunkloading, is ported; the manager that reads it is not.
- `buildcraft.lib.registry.MigrationManager` -- block/item id migration across mod versions
  (`RegistryEvent.MissingMappings`). A maintenance feature with no bearing on anything else
  in the port; deferred as low priority rather than blocked.
- `buildcraft.lib.block.VanillaRotationHandlers` -- QoL wrench-rotation for ~25 vanilla block
  types (anvils, stairs, doors, hoppers, skulls, ...). Each needs individually verifying
  against the modern renamed block classes (`BlockDirectional`->`DirectionalBlock`,
  `BlockRedstoneDiode`->`DiodeBlock`, etc.) and the file also leans on
  `ObfuscationReflectionHelper` for private-field access, which needs rethinking under
  Mojang mappings. Real value, but an enhancement rather than something anything else
  depends on; deferred rather than rushed.
- `buildcraft.lib.block.BlockMarkerBase` and the rest of `buildcraft.lib.item`
  (`ItemDebugger`, `ItemGuide`, `ItemGuideNote`, `ItemPluggableSimple`) -- follow
  `buildcraft.lib.marker` and the guide-book/pluggable systems respectively, none ported yet.
- `buildcraft.lib.script.{ScriptableRegistry,SimpleScript,SimpleReloadableRegistry,
  ReloadableRegistryManager}` -- 1,629 lines together, and effectively one feature:
  BuildCraft's own JSON-based scripting system for adding, replacing or removing recipes
  without writing a mod, predating (and now largely superseded in spirit by) vanilla's own
  datapack recipe system. `SimpleScript` alone is 1043 lines and leans on `BCLibProxy` for
  resource-pack enumeration and `Loader.isModLoaded` for its `is_mod_loaded` script
  function -- both 1.12.2-era FML, needing real replacements
  (`ModList.get().isLoaded(id)` for the latter). Large, self-contained, and nothing calls
  it yet; deferred as a unit rather than half-ported.
- `buildcraft.lib.config.{DetailedConfigOption,OverridableConfigOption,EnumRestartRequirement,
  StreamConfigManager}` (and by extension `RoamingConfigManager`, which reads them) all
  touch `net.minecraftforge.common.config.Property` -- Forge's old `Configuration`/
  `Property` config file API, confirmed absent from both targets' jars. It was replaced by
  `ModConfigSpec` (`ForgeConfigSpec`), a structural change to how a config *value* is
  declared, not a rename. BuildCraft's actual config values live in `buildcraft.core` (not
  ported) rather than in `lib` itself, and `BCLibConfig` (ported) is already a plain
  settings holder rather than a `Property` wrapper, so none of this blocks anything already
  landed. `StreamConfigManager` was already marked `@Deprecated` in its own 1.12.2 source;
  only `FileConfigManager` is genuinely dependency-free, and is a candidate for a later,
  focused pass once there is a real `ModConfigSpec` to wire it to.
- `buildcraft.lib.command` (4 files) -- pre-Brigadier commands (`ICommand`,
  `event.registerServerCommand`). Modern commands are Brigadier
  (`CommandDispatcher<CommandSourceStack>` via `RegisterCommandsEvent`); this is a rewrite
  of each command's argument parsing and execution, not a port, and also depends on the
  dropped `BCLib` singleton for its mod-version/changelog commands specifically.

- `CapabilitiesHelper` — it existed only to supply the no-op storage and null factory
  1.12.2's capability system demanded but never used. Neither argument exists now.
  `TilesAPI` and `MjCapabilities` show the replacement shape per platform.
- `IFluidHandlerAdv` on 26.x — replaced by `FluidFilters`, because `ResourceHandler` can be
  introspected from outside. 1.20.1 keeps the interface. See structural change 6.
- `EnumColor`'s sprite registry and `getLocalizedName` — client-only statics behind
  `@SideOnly`, which has no equivalent; they belong to the rendering rewrite.
- `EnumRedstoneChipset`, `BCItems`, `BCBlocks` — all three are keyed off item damage or the
  eight old `@ObjectHolder` mod ids. They want `DeferredHolder` against real registry
  entries, so they follow the modules that define those entries rather than leading them.
- `IItemHandlerFiltered` — renamed `IFilteredItemHandler` on 26.x, because the interface it
  extends is now `ResourceHandler<ItemResource>`. Unchanged on 1.20.1.
- The eight rendering interfaces in `transport` — `IPipeBehaviourBaker`, `IPipeFlowRenderer`,
  `PluggableModelKey`, `PipeApiClient` and friends. They are all shaped around
  `BlockRenderLayer` and quad baking, so they come back with the rendering rewrite rather
  than being guessed at now.
- The `package-info.java` files. Each carried only
  `@API(apiVersion = ..., owner = ..., provides = ...)`, an FML annotation for the
  standalone-API-jar mechanism that no longer exists. Where a package needed real
  documentation it got a new one, such as `buildcraft.api.core`.

**Both targets are verified by booting a server**, not just by compiling. That matters: every
bug in the "Build and packaging gotchas" section below compiled cleanly and only showed up at
runtime. Re-run `./gradlew :neoforge-26x:runServer` (and the 1.20.1 equivalent) after any
registration change.

Not verified: client-side rendering. Checking that the models actually draw needs a display,
so treat the blockstate/model JSON as unconfirmed until someone runs `runClient`.

Remaining, in the order they should be tackled — each module needs the one above it:

| Module | Files | Notes |
| --- | --- | --- |
| `BuildCraftAPI/api` | **done** | 217/251; the remainder is blocked on the modules or on rendering, listed above. |
| `buildcraft.lib` | 541 | The foundation: tiles, GUI, networking, models, MJ power. |
| `buildcraft.core` | 85 | Gears (done), wrench, markers, engines, map location. |
| `buildcraft.transport` | 124 | Pipes. The largest single feature. |
| `buildcraft.builders` | 121 | Quarry, builder, architect, filler, schematics. |
| `buildcraft.silicon` | 79 | Laser, assembly table, gates/wires. |
| `buildcraft.factory` | 49 | Pump, mining well, tank, autoworkbench. |
| `buildcraft.energy` | 41 | Combustion/stirling engines, oil, fuel. |
| `buildcraft.robotics` | 24 | Robots, zone planner. |

Within `buildcraft.lib` the hard parts, roughly in dependency order, are: the registration
layer, `MjAPI`/power, the tile + networking stack (1.12's custom packet system has to become
NeoForge payloads), NBT (which becomes data components on 26.x), then GUI and models.

## API migration reference

Worked out against the real jars rather than from memory. `javap` on
`platforms/neoforge-26x/build/moddev/artifacts/minecraft-patched-*-merged.jar` settles any
question this table does not.

### Package and type renames (1.12.2 → modern)

| 1.12.2 | Modern |
| --- | --- |
| `net.minecraft.util.EnumFacing` | `net.minecraft.core.Direction` |
| `EnumFacing.Axis` / `.AxisDirection` | `Direction.Axis` / `Direction.AxisDirection` |
| `net.minecraft.util.math.BlockPos` | `net.minecraft.core.BlockPos` |
| `net.minecraft.util.math.Vec3d` | `net.minecraft.world.phys.Vec3` |
| `net.minecraft.util.math.Vec3i` | `net.minecraft.core.Vec3i` |
| `net.minecraft.util.math.AxisAlignedBB` | `net.minecraft.world.phys.AABB` |
| `net.minecraft.util.Rotation` | `net.minecraft.world.level.block.Rotation` |
| `net.minecraft.world.World` | `net.minecraft.world.level.Level` |
| `net.minecraft.block.state.IBlockState` | `net.minecraft.world.level.block.state.BlockState` |
| `net.minecraft.tileentity.TileEntity` | `net.minecraft.world.level.block.entity.BlockEntity` |
| `net.minecraft.item.ItemStack` | `net.minecraft.world.item.ItemStack` |
| `net.minecraft.util.ResourceLocation` | `net.minecraft.resources.ResourceLocation` on 1.20.1, **`net.minecraft.resources.Identifier` on 26.x** |
| `javax.vecmath.*` | `org.joml.*` |
| `gnu.trove.*` | `it.unimi.dsi.fastutil.*` |
| `net.minecraft.nbt.CompressedStreamTools` | `net.minecraft.nbt.NbtIo` |
| `net.minecraft.profiler.Profiler` (concrete, no-op by default) | `net.minecraft.util.profiling.ProfilerFiller` (interface); `InactiveProfiler.INSTANCE` for a standalone no-op |

`gnu.trove.*` is not a rename-and-done -- the collection types, method names and a couple of
behaviours all differ:

- `TIntIntHashMap` -> `Int2IntOpenHashMap`, `TIntHashSet` -> `IntOpenHashSet`, and the
  `T<Type>ArrayList` family -> `it.unimi.dsi.fastutil.<type>s.<Type>ArrayList` (note the
  lower-case type in the package).
- `list.toArray()` needs the typed name -- `toIntArray()`, `toByteArray()`, etc. -- fastutil has
  no bare no-arg overload.
- `list.sort()` (no args) -> `list.sort(null)`; fastutil's `sort(Comparator)` treats `null` as
  natural order, same as Trove's implicit sort.
- `TIntIntHashMap#increment(key)` (false if the key was absent, so callers needed a manual
  `put(key, 1)` fallback) collapses to a single `Int2IntOpenHashMap#addTo(key, 1)` -- `addTo`
  inserts an absent key starting from the map's default value, so it always does the right thing
  in one call.
- `TIntArrayList#remove(offset, length)` -> `IntArrayList#removeElements(from, to)`; the second
  argument's meaning changes from a length to an end index (equivalent at `offset == 0`, not
  otherwise).
- Trove's bulk-append `list.add(int[])` has no fastutil equivalent; use the array constructor
  (`new IntArrayList(data)`) instead of `new IntArrayList(); list.add(data);`.

### Method renames

| 1.12.2 | Modern |
| --- | --- |
| `Vec3d.addVector(x, y, z)` | `Vec3.add(x, y, z)` |
| `EnumFacing.getFrontOffsetX()` | `Direction.getStepX()` |
| `EnumFacing.getFacingFromAxis(dir, axis)` | `Direction.fromAxisAndDirection(axis, dir)` |
| `EnumFacing.getDirectionVec()` | `Direction.getUnitVec3i()` |
| `new BlockPos(double, double, double)` | `BlockPos.containing(double, double, double)` (floors) |
| `world.isRemote` / `level.isClientSide` (field) | `level.isClientSide()` — the field is private on 26.x |
| `TileEntity.readFromNBT` / `writeToNBT` | `BlockEntity.loadAdditional` / `saveAdditional` |
| `ITickable.update()` | a `BlockEntityTicker` returned from `EntityBlock.getTicker` |
| `Block.hasTileEntity` / `createTileEntity` | implement `EntityBlock.newBlockEntity` |
| `AxisAlignedBB.grow(amount)` | `AABB.inflate(amount)` |
| `Vec3i.add(Vec3i)` / `BlockPos.add(Vec3i)` | `Vec3i.offset(Vec3i)` / `BlockPos.offset(Vec3i)` |
| `EntityPlayerMP.getAdvancements().grantCriterion(advancement, name)` | `PlayerAdvancements.award(advancement, name)` |
| `FMLCommonHandler.instance().getMinecraftServerInstance()` | `ServerLifecycleHooks.getCurrentServer()` |
| `BlockPos.offset(EnumFacing[, int])` | `BlockPos.relative(Direction[, int])` |
| `EnumFacing.rotateAround(Axis)` | `Direction.getClockWise(Axis)` (no bare `rotateAround` any more) |
| `AxisDirection.getOffset()` | `AxisDirection.getStep()` |
| `I18n.translateToLocal`/`canTranslate` (`net.minecraft.util.text.translation`) | `Language.getInstance().getOrDefault(key)` / `.has(key)` — common code on both targets, unlike the client-only modern `I18n` |
| `Profiler.getProfilingData(name)` -> `List<Profiler.Result>` | `ProfileResults.getTimes(name)` -> `List<ResultField>` (obtained from a `ProfileCollector`'s `getResults()`, not from the write-side `ProfilerFiller` itself) |
| `Profiler.Result.profilerName`/`usePercentage`/`totalUsePercentage` | `ResultField.name`/`percentage`/`globalPercentage` |
| `IFluidHandler.fill`/`drain(..., boolean doFill/doDrain)` | `fill`/`drain(..., IFluidHandler.FluidAction)` — true on 1.20.1 too, not just the 26.x transfer API |
| `FluidStack.amount` (public field) | `FluidStack.getAmount()`/`setAmount(int)`/`grow(int)`/`shrink(int)` — both targets' `FluidStack` (`net.minecraftforge.fluids.FluidStack` on 1.20.1, `net.neoforged.neoforge.fluids.FluidStack` on 26.x) |

`EnumDyeColor`/`DyeColor`'s ordinal order flipped along the way: 1.12.2's `getDyeDamage()` ran
BLACK(0)..WHITE(15); modern `DyeColor.getId()` (== `ordinal()`) runs WHITE(0)..BLACK(15), the
reverse. Anything indexing a BuildCraft-owned array by the old damage value needs the array
reordered to match, not just the accessor renamed -- `ColourUtil`'s `NAMES`/`DARK_HEX`/
`LIGHT_HEX` are the worked example. Unchanged on both targets, since `DyeColor` is pure vanilla.

`BlockPos` no longer has a double constructor at all, so anything that rounded a different
way — `VecUtil.convertCeiling` for instance — has to cast explicitly.

### Structural changes, in rough order of pain

1. **Block metadata is gone** (1.13). Every `IBlockState`/metadata pair becomes a real
   blockstate property. This is the single biggest source of work in `transport` and
   `builders`.
2. **Registration.** BuildCraft's `RegistrationHelper` + FML `preInit` becomes
   `DeferredRegister` on the mod event bus. On 26.x the registry object carries its own id,
   so items no longer need an unlocalised-name string threaded through the constructor.
3. **`CompoundTag`'s getters return `Optional` on 26.x**, and this one is dangerous because the
   compiler only catches some of it. `getInt(name)` is `Optional<Integer>`; the value form is
   `getIntOr(name, default)`. 1.12.2's `getInteger` returned 0 for a missing key, so `getIntOr(name, 0)`
   is the faithful port — but it is now an explicit choice, and for a lot of BuildCraft's code the
   honest default is not zero. `getTagList(name, type)` became
   `getList(name).orElseGet(ListTag::new)`, and the write side is `setX` → `putX` throughout.
   1.20.1 still returns values, so this is a per-target divergence. `misc/port/rename.py` applies the
   faithful mapping and `misc/port/to1201.py` reverses it.
4. **NBT → data components** (1.20.5). Item NBT does not exist on 26.x; anything BuildCraft
   stored on a stack needs a `DataComponentType`. Block entities still use NBT, but on 26.x they
   read and write it through `ValueInput`/`ValueOutput` (`getLongOr(name, default)`,
   `putLong(name, value)`) rather than `CompoundTag`, so the hooks are `loadAdditional` and
   `saveAdditional`. 1.20.1 still uses `CompoundTag`.

   For the specific case of "BuildCraft wants to stash an arbitrary `CompoundTag` on a stack" --
   which is most of what item NBT was actually used for (guide books, filters, markers, list
   contents) -- vanilla already componentized exactly that as `DataComponents.CUSTOM_DATA`
   (`CustomData`, holding a `CompoundTag`). Use it rather than inventing a BuildCraft-specific
   component type. The one thing to get right: `CustomData` is copy-on-read.
   `stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()` hands back a
   *detached copy* -- mutating it does nothing to the stack. The 1.12.2 idiom of
   `getItemData(stack).setInteger(...)` and walking away relied on the returned tag being a live
   reference, which no longer holds. Either pair a read with an explicit write
   (`CustomData.set(DataComponents.CUSTOM_DATA, stack, nbt)`), or use
   `CustomData.update(DataComponents.CUSTOM_DATA, stack, nbtConsumer)`, which reads, hands your
   lambda a mutable tag, and writes the result back in one call -- the closest equivalent to the
   old idiom this target allows. `buildcraft.lib.misc.NBTUtilBC` on 26.x splits into
   `getItemData`/`setItemData`/`updateItemData` for exactly this reason; the 1.20.1 copy keeps
   the original single `getItemData` because 1.20.1's `stack.getOrCreateTag()` is still a live
   reference.
5. **Networking.** The 1.12 `IMessage`/`SimpleNetworkWrapper` stack becomes
   `CustomPacketPayload` with a `StreamCodec` and explicit registration.
6. **Capabilities**, and note this differs *twice*. 1.12.2's `@CapabilityInject` was removed in
   1.16, so on 1.20.1 each capability is fetched with a `CapabilityToken` and declared in
   `RegisterCapabilitiesEvent`. 26.x replaces the whole system with `BlockCapability`, keyed by
   an `Identifier` and registered per block entity type — there is no attach-by-event path at
   all, so anything that used `ICapabilityProvider` needs restructuring, not renaming.
7. **Item, fluid and energy transfer is a new API on 26.x**, and this one matters more to
   BuildCraft than to most mods, because moving items and fluids around *is* BuildCraft.
   `IItemHandler`, `IFluidHandler` and `IEnergyStorage` are all gone from NeoForge 26.x,
   replaced by one generic `net.neoforged.neoforge.transfer.ResourceHandler<T extends Resource>`
   with `ItemResource`, `FluidResource` and a separate `EnergyHandler`. Three things change:
   - **Resource and amount are separate.** A `FluidResource`/`ItemResource` says *what*
     something is; the amount is a `long` the handler holds per slot. There is no
     `FluidStack`-as-a-key any more, which is what `StackKey` was for.
   - **Handlers are slot-indexed and introspectable** — `size()`, `getResource(slot)`,
     `getAmountAsLong(slot)`. Anything 1.12.2 solved by making tanks implement an extra
     BuildCraft interface can usually now be done from outside, against any mod's handler.
     `IFluidHandlerAdv` → `FluidFilters` is the worked example.
   - **`boolean simulate` became transactions.** Operations take a `TransactionContext`;
     `Transaction.openRoot()` in try-with-resources, `commit()` to keep the effect, otherwise
     it rolls back on close, and `Transaction.open(parent)` nests. This replaces both
     `doDrain`/`simulate` booleans and BuildCraft's own manual rollback in the pipe and
     robot code.

   1.20.1 has none of this — it is still `IItemHandler`/`IFluidHandler` with `FluidAction`.
   This is the single largest source of per-platform divergence after registration, and it
   lands squarely on `transport`, `factory` and `robotics`.

   NeoForge ships its own base classes for the array-backed, transaction-safe case --
   `net.neoforged.neoforge.transfer.StacksResourceHandler<S, T>` (and its `ItemStack`
   specialisation, `ItemStacksResourceHandler`) already do the snapshot/rollback bookkeeping
   and `ValueIOSerializable` NBT persistence by themselves, with `isValid`/`getCapacity`/
   `onContentsChanged` as the extension points. `DelegatingResourceHandler<T>` and
   `CombinedResourceHandler<T>` are the generic equivalents of 1.12.2's hand-written
   delegate/combined wrappers. Before hand-rolling a `ResourceHandler` implementation, check
   whether one of these already covers it -- `buildcraft.lib.tile.item.ItemHandlerSimple` is
   the worked example: BuildCraft's `StackInsertionFunction` abstraction turned out to be
   fully replaceable by `ItemStacksResourceHandler`'s inherited `getCapacity` hook.
8. **Rendering**, and 26.x is a second rewrite on top of the first. `TESR` → `BlockEntityRenderer`,
   and the `PoseStack`/`RenderType` pipeline replaces raw GL — that much is the 1.20.1 story.
   26.x then replaces *that*: rendering no longer draws during the render pass, it **submits**
   work to a graph that is sorted and executed later. `MultiBufferSource` does not exist;
   the collector is `net.minecraft.client.renderer.SubmitNodeCollector`, with
   `submitModel`/`submitModelPart`/`submitCustomGeometry` in place of getting a
   `VertexConsumer` and writing to it. Anything taking a `MultiBufferSource` is therefore a
   third signature, not a shared one — `IItemCustomPipeRender` is the worked example.
   `GlUtil` and most of `buildcraft.lib.client` are rewrites, not ports, on both targets.
9. **Ore dictionary → tags.** `OreDictionary.registerOre` becomes a tag JSON. BuildCraft
   publishes its gears under `c:gears/<material>` on 26.x and `forge:gears/<material>` on
   1.20.1.
10. **`.lang` → `.json`**, and translation keys become `item.<namespace>.<path>`.
11. **Recipes.** `data/<ns>/recipe/` (singular) on 26.x with string ingredients and
   `result.id`; `data/<ns>/recipes/` on 1.20.1 with object ingredients and `result.item`.
12. **Item models** need a client item definition in `assets/<ns>/items/<id>.json` on 26.x
    (1.21.4+); 1.20.1 only needs `models/item/`.
13. **`IPlantable` is gone on 26.x** (confirmed absent from every 26.x/NeoForge jar; still
    present, unchanged, on 1.20.1's Forge fork). This is a real removal, not a rename, and it
    hits anything that touches crops, saplings or farmland.
    - Block-side: the `IPlantable` interface is gone. `net.minecraft.world.level.block.
      VegetationBlock` is the new common base for the same family -- `CropBlock`, `SaplingBlock`,
      `StemBlock`, `NetherWartBlock`, `FlowerBlock`, `TallGrassBlock`, `MushroomBlock` and
      `DoublePlantBlock` all extend it directly now, where 1.20.1 still has them implement
      `IPlantable` on top of `BushBlock`.
    - Soil-side: `Block#canSustainPlant(state, level, pos, dir, IPlantable)` (`boolean`) is
      replaced by the NeoForge extension method `BlockState#canSustainPlant(BlockGetter,
      BlockPos, Direction, BlockState)`, which takes the *plant's* `BlockState` rather than an
      `IPlantable` instance, and returns `net.minecraft.util.TriState`
      (`TRUE`/`FALSE`/`DEFAULT`) rather than `boolean`. `DEFAULT` means "no opinion, ask the
      plant", resolved with `TriState#toBoolean(fallback)` -- `plantState.canSurvive(level,
      pos)` is a reasonable fallback.
14. **`InteractionResult` changed shape, not just package, on 26.x.** It is a plain enum on
    1.20.1 (`SUCCESS`/`CONSUME`/`PASS`/`FAIL`), but a sealed interface with record subtypes on
    26.x -- `SUCCESS` and `SUCCESS_SERVER` are both instances of the nested
    `InteractionResult.Success`. A success check is `result == InteractionResult.SUCCESS` on
    1.20.1 but `result instanceof InteractionResult.Success` on 26.x.
15. **`LevelHeightAccessor.getMinBuildHeight()` was renamed `getMinY()`** at some point after
    1.20.1 -- 26.x has `getMinY()`, 1.20.1 still has `getMinBuildHeight()`. Easy to miss because
    both compile against completely different, unrelated things if you get it backwards on one
    target and the IDE doesn't catch it, since both classes have plenty of other methods.

### Things that differ *between* our two targets

These are the traps when porting a file to both at once.

| | 26.x | 1.20.1 |
| --- | --- | --- |
| Loader packages | `net.neoforged.*` | `net.minecraftforge.*` |
| Item/fluid transfer | `transfer.ResourceHandler<T>` + `Transaction` | `IItemHandler` / `IFluidHandler` |
| Render collector | `SubmitNodeCollector` | `MultiBufferSource` |
| Stack NBT | `ItemStack.CODEC` only | `ItemStack.save` / `.of` |
| Stack equality | `isSameItemSameComponents` | `isSameItemSameTags` |
| Primitive NBT tag payload | `ByteTag.value()`, `StringTag.value()`, ... (records) | `.getAsByte()`, `.getAsString()`, ... |
| Plant/soil interface | `VegetationBlock` base; `BlockState#canSustainPlant` -> `TriState` | `IPlantable`; `Block#canSustainPlant` -> `boolean` |
| `InteractionResult` | sealed interface (`instanceof .Success`) | plain enum (`== SUCCESS`) |
| Level height accessor | `getMinY()` | `getMinBuildHeight()` |
| Fluid equality | static `FluidStack.matches(a, b)` | instance `a.isFluidEqual(b)` |
| Particle detail setting | `net.minecraft.server.level.ParticleStatus` | `net.minecraft.client.ParticleStatus` |
| `Ingredient` stack accessor | `.items()` -> `Stream<Holder<Item>>` | `.getItems()` -> `ItemStack[]` |
| Mod metadata | `META-INF/neoforge.mods.toml` | `META-INF/mods.toml` |
| Dependency flag | `type = "required"` | `mandatory = true` |
| Registry handle | `DeferredHolder` / `DeferredItem` | `RegistryObject` |
| Item registry | `DeferredRegister.createItems(id)` | `DeferredRegister.create(ForgeRegistries.ITEMS, id)` |
| Mod constructor | `(IEventBus, ModContainer)` | no-arg, then `FMLJavaModLoadingContext.get()` |
| Dev detection | `FMLLoader.getCurrent().isProduction()` | `FMLLoader.isProduction()` (static) |
| Capabilities | `BlockCapability.createSided(Identifier, Class)` | `CapabilityManager.get(new CapabilityToken<>(){})` + `RegisterCapabilitiesEvent` |
| Resource ids | `Identifier` | `ResourceLocation` |
| Block entity NBT | `ValueInput` / `ValueOutput` | `CompoundTag` |
| `CompoundTag` getters | `getInt` → `Optional`; `getIntOr(k, 0)` | `getInt(k)` returns the value |
| Block entity type | `new BlockEntityType<>(supplier, blocks...)` | `BlockEntityType.Builder.of(...).build(null)` |
| Block `codec()` | not required (removed) | required (`simpleCodec`) |
| `AABB` corner constructor | no `(BlockPos, BlockPos)` ctor -- use the six-`double` ctor | `AABB(BlockPos, BlockPos)` still exists |
| `Vec3` from `BlockPos`/`Vec3i` | `new Vec3(Vec3i)` ctor exists | no such ctor -- spell out `new Vec3(pos.getX(), pos.getY(), pos.getZ())` |
| `ChunkPos` coordinates | `x()`/`z()` (fields are private) | public `x`/`z` fields, same as 1.12.2 |
| `AbstractArrow`/`SpectralArrow` package | `net.minecraft.world.entity.projectile.arrow` | `net.minecraft.world.entity.projectile` |
| Advancement lookup | `ServerAdvancementManager#get(Identifier)` -> `AdvancementHolder` | `ServerAdvancementManager#getAdvancement(ResourceLocation)` -> `Advancement` |
| `ServerPlayer`'s own level accessor | `level()` returns `ServerLevel` directly | `serverLevel()` (`level()` returns plain `Level`) |
| Mod banner | `bannerFile` / `iconFile` | `logoFile` |
| `pack_format` | 97 | 15 |
| Gradle plugin | `net.neoforged.moddev` | `net.neoforged.moddev.legacyforge` |
| `ChatFormatting` | gutted to the escape sequence and `stripFormatting` only -- no `isColor()`/`getColor()`/`getChar()`/`getName()` (confirmed against the real source; text styling moved to `Style`/`TextColor`) | keeps the full 1.12.2-shaped API, `isColor()` included |
| `CompoundTag` key set | `keySet()` | `getAllKeys()` |
| `FluidStack` existence | still exists (`net.neoforged.neoforge.fluids.FluidStack`), as a plain value type -- just not what a handler moves any more | `net.minecraftforge.fluids.FluidStack`, unchanged in shape from 1.12.2 |

For the 1.20.1 Gradle target, note that `legacyForge { version = ... }` selects
*MinecraftForge*. NeoForge's 1.20.1 fork needs
`legacyForge { enable { neoForgeVersion = "1.20.1-47.1.106" } }`.

## Build and packaging gotchas

Each of these cost a failed server boot, so they are worth knowing up front.

- **The shared modules must be declared as part of the mod**, not just as Gradle dependencies.
  A dev run loads `build/classes` directly rather than the built jar, so bundling `:expression`
  and `:shared` into the jar is not enough — without
  `mods { create("buildcraft") { sourceSet(project(":shared").sourceSets.main.get()) } }`
  the game starts and then dies with `NoClassDefFoundError` on the first shared class touched.
- **`pack.mcmeta` is optional for a mod, and on 26.x it is easier to leave out.** A mod's
  resources are loaded as both a resource pack and a data pack, and those have different format
  numbers (97 and 121 on 26.3), so one file cannot declare the right one for both. 26.x also
  rejects any `pack_format` above 81 unless `min_format` and `max_format` are present
  (`min_format` is an int, `max_format` is a `[major, minor]` pair). NeoForge infers correct
  metadata per pack type when the file is absent. 1.20.1 still wants a plain `pack_format = 15`.
- **`logoFile` is deprecated in `neoforge.mods.toml`** and fails mod loading validation on 26.x.
  Use `bannerFile` for a wide image or `iconFile` for a square one. 1.20.1's `mods.toml` still
  uses `logoFile`.
- **Blocks need a loot table or they drop nothing.** 1.12.2 dropped the block itself by default.
  The directory is `data/<ns>/loot_table/blocks/` — note `loot_table` is singular as of 1.21,
  and `tags/block/` likewise.
- **`requiresCorrectToolForDrops()` needs a mining tag**, or the block is unbreakable-for-drops.

## How much actually has to be duplicated

Worth knowing before adding a platform class, because the answer is not "all of it":

- `VecUtil` and `RotationUtil` are **byte-identical** on both targets. The vanilla geometry
  types did not move between 1.20.1 and 26.x, so the only work was the 1.12.2 -> modern rename.
- `MjEffects` and `IMjEffectManager` are likewise identical, and kept separate only so each
  platform's `buildcraft.api.mj` package is self-contained.
- What genuinely differs is registration (`BCRegistry`), block entity serialisation (`TileBC`),
  and capabilities (`MjCapabilities`, and how a machine exposes one). Those three are where the
  two targets really diverge, and they are worth reading side by side before porting a machine.

So the practical rule: port to 26.x first, try the same file unchanged on 1.20.1, and only fork
it when the compiler objects.

## Conventions

- Provided-by-Minecraft libraries (Guava, Gson, commons-lang3, fastutil, log4j) are declared
  `compileOnly`, so a second copy never ends up in the mod jar.
- The eight 1.12.2 mod ids (`buildcraftcore`, `buildcraftlib`, `buildcrafttransport`, ...)
  were held together by FML's `parent` mechanism, which no longer exists. They collapse into
  one `buildcraft` mod id; the old boundaries survive as packages and as separate `BC*`
  registration holders.
- Port by compiling, not by grepping imports. "No `net.minecraft` import" does not mean "no
  Minecraft dependency" — a same-package reference does not appear as an import.
  `buildcraft.lib.path.task` looks clean but `EnumTraversalExpense` beside it needs `Level`.
- When a class is *mostly* version-independent, split it rather than duplicating it whole.
  `MjBattery` is the pattern: all the arithmetic is shared, and the one method that needed a
  `Level` (shedding excess power as a particle effect) became `shedExcessPower()`, returning
  the amount lost for a thin per-platform caller to render.
- When a data structure is rewritten, add a round-trip test. `BitSetTester` exists because
  the Trove → fastutil swap changed how the backing array is read.
