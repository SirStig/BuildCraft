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
- **`buildcraft.core` — started.** `core.item.ItemWrench` (renamed from `ItemWrench_Neptune`,
  following the same "supersede `ItemBC_Neptune`/`IItemBuildCraft` with `BCRegistry` plus
  lang/model JSON" pattern already used for the five gears and the power tester -- see the
  "Deliberately not ported" note on `IItemBuildCraft`) is the first `core` item, registered in
  `BCCoreRegistries` alongside the gears. `onItemUseFirst`/`onItemUse` merge into `useOn`;
  `IBlockState#getActualState` and `Item#doesSneakBypassUse` are both simply gone (see the
  class's own javadoc for the full account, including 26.x's sealed-interface
  `InteractionResult` check and its three-argument `LivingEntity#swing`). Texture, item model
  and a modern datapack recipe (`c:gears/stone` + `c:ingots/iron` / `forge:` equivalents on
  1.20.1) are ported from `buildcraft_resources/assets/buildcraftcore/`; the advancement JSON
  is not -- `AdvancementUtil.unlockAdvancement` already treats an unregistered advancement id
  as a harmless one-time warning rather than an error (see its own class javadoc), and nothing
  else needs a BuildCraft advancement tree to exist yet, so building one prematurely for a
  single leaf advancement isn't worth it.
  `buildcraft.lib.misc.SoundUtil` (both platforms) also landed as part of this -- `ItemWrench`
  needed it for its slide-sound feedback, and it turned out to have no rendering/client-only
  dependency blocking it (unlike the `GlUtil`/`DrawingUtil`/... cluster it sits next to in
  `lib.misc`): `IBlockState#getSoundType(state, world, pos, entity)` lost its three context
  parameters, and per-fluid bucket sounds move from `Fluid#getEmptySound`/`getFillSound` to
  `Fluid#getFluidType()#getSound(FluidStack, SoundAction)`, Forge's generic fluid-property
  system already shared by both targets.
  `core.block.BlockSpringWater` (renamed from `BlockSpring`, water half only) is the first
  worked example of splitting a 1.12.2 metadata-subtyped block into one real `Block` per
  variant -- see its own class javadoc, and the `buildcraft.core` survey below for why the oil
  half and `ItemBlockSpring`'s original two-variant `BlockItem` don't follow the same way yet.
  Registered (`spring_water`, reusing vanilla's own `bedrock` model/texture, matching 1.12.2's
  own choice there) but not yet spawned anywhere -- `core.gen.SpringPopulate`, the world-gen
  hook that placed it, needs its own design; see the survey entry.
- **`buildcraft.lib.net`'s networking foundation is designed and landed, both platforms.**
  `BCNetwork` (a new root-package class, alongside `BCRegistries`) is this port's replacement
  for `MessageManager`: a thin, per-message static registration list rather than a dynamic
  per-mod dispatch table -- see the "Networking" structural-change entry for the full design
  and why the two targets need genuinely different underlying registration APIs.
  `IPayloadReceiver` and `MessageUpdateTile` (both platforms) are the first ported message:
  routes an opaque payload to whatever block entity implementing `IPayloadReceiver` sits at a
  given position. Verified with a real dev-server boot on each target, not just a compile
  check -- no crash, no registration error, mod construction and world load both completed
  cleanly on both. `MessageManager`, `MessageUtil`, and the rest of `lib.net`'s concrete
  messages (`MessageContainer`, `MessageDebugRequest`/`Response`, `MessageMarker`,
  `MessageObjectCacheRequest`/`Response`) are still not ported -- `MessageUpdateTile` only
  needed to prove the registration design works, not to bring the whole package along -- but
  each individual message is now an ordinary port against a settled design, not an open
  question.
- **`buildcraft.lib.gui.pos` (9 files) and `buildcraft.lib.gui.ISimpleDrawable`, in `modules/shared`.**
  A self-contained "screen coordinate algebra" package (points, rectangles, offsets, all as
  composable `IGuiPosition`/`IGuiArea` values) discovered while porting `buildcraft.lib.
  statement` below, which needed it for `StatementContext`. Confirmed via a full import audit
  of all 9 files that none of it touches Minecraft or rendering at all -- pure interfaces and
  math, built only on `java.util.function.DoubleSupplier` and the already-ported `modules/
  expression` -- so, like the earlier `buildcraft.lib.misc`/`misc.data` pure-Java files, it
  lives once in `modules/shared` rather than duplicated per platform.
- **`buildcraft.lib.statement` (7 files, both platforms).** The generic trigger/action
  wrapper layer gates and other statement-driven blocks will eventually sit on top of
  (`ActionWrapper`, `TriggerWrapper`, `StatementWrapper`, `FullStatement`, `StatementType`,
  `StatementTypeParam`, `StatementContext`). `buildcraft.api.statements` (already fully
  ported) turned out to have modernised its own shape along the way, discovered while
  porting this: `IGuiSlot#getDescription()`/`getTooltip()` return `Component` now, not
  `String` (both were built from a client-only `I18n` lookup in 1.12.2, which doesn't work on
  a server; a `Component` carries its translation key and resolves at draw time instead,
  which is also why the `@SideOnly(Side.CLIENT)` annotations on them are gone -- see
  `IGuiSlot`'s own javadoc), and `IStatementParameter`'s NBT/buffer read/write methods all
  gained a `HolderLookup.Provider` parameter, threaded through `StatementType`/
  `StatementTypeParam`/`FullStatement` here as a result -- reading or writing a parameter can
  mean reading or writing an `ItemStack`, whose data components need registry access on
  26.x. 1.20.1's copy of the same API takes the identical parameter, ignoring it, specifically
  so both platforms' `lib.statement` files could stay this close to identical; they are.
  `StatementManager`'s lookup also changed shape: no `getParameterReader(kind)` method exists
  any more, just the public `parameters`/`paramsBuf` maps directly.
- **`BuildCraftAPI/api` — 217 of 251 files.** Everything except the list below, on both targets.
  Of the 34 not ported: 20 are `package-info.java` whose only content was FML's `@API`
  annotation, which no longer exists; the rest are blocked or deliberate, and listed below.
- `buildcraft.lib.nbt` (5), `.mj` (2), `.crops` (2), `.compat` (3), `.migrate` (2), `.fake` (1),
  `.json` (1), `buildcraft.lib.misc.{NBTUtilBC,StackUtil,InventoryUtil}`, and `BCLibConfig`,
  `IChunkLoadingTile`, `IBlockWithFacing`, `ILocalBlockUpdateSubscriber`,
  `buildcraft.lib.registry.PluggableRegistry` — the parts of `buildcraft.lib`'s foundation
  layer that turned out not to need the tile/net/block/item cluster below them.
  `InventoryUtil` is a partial port (the drop/spawn/`addAll`/`addToPlayer` helpers only) --
  see the `CapUtil`/`ItemTransactorHelper` entry further down for the rest.
  `buildcraft.lib.misc.data.AverageDouble` (both platforms, most of `misc.data` already lives
  in `modules/shared` -- see that entry) drops the `INBTSerializable` interface entirely on
  26.x, the same way `ItemHandlerSimple` does and for the same reason (the interface doesn't
  exist there) -- see its own class javadoc.
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
- `buildcraft.lib.cache` (7, both platforms) — the chunk/neighbour-tile lookup caches every
  machine's chunk-loading-avoidance code goes through. `Chunk#isLoaded()`/`TileEntity#isInvalid()`
  are gone (`Level#hasChunk`/`BlockEntity#isRemoved()` respectively), `Block#hasTileEntity(state)`
  is `BlockState#hasBlockEntity()`, and `TileBC_Neptune`'s own `getChunk` shortcut (which
  `NeighbourTileCache` special-cased) has no equivalent on `TileBC`, so that lookup now always
  goes through `ChunkUtil#getChunk` uniformly rather than special-casing BuildCraft's own base
  class.
- **`buildcraft.lib.marker` (6 files, both platforms) — the abstract marker-connection framework**:
  `MarkerCache`, `MarkerSubCache`, `MarkerConnection`, `MarkerSavedData`, `buildcraft.lib.tile.TileMarker`
  and `buildcraft.lib.net.MessageMarker` (registered `playToClient` on 26.x, `NetworkDirection.PLAY_TO_CLIENT`
  on 1.20.1 -- this message is server-to-client only, unlike `MessageUpdateTile`). Compiles cleanly on both
  and both dev servers still boot clean after the registration change, but nothing instantiates a concrete
  `MarkerCache`/`MarkerSubCache`/`MarkerConnection` yet -- `buildcraft.core.marker`'s concrete
  `VolumeCache`/`PathCache`/etc. are still out of scope, deliberately deferred to a later pass (see the
  `buildcraft.core` survey entry above, which flagged this package as the blocker).
  - `MarkerSavedData<S, C>` cannot supply a complete `SavedDataType`/`DimensionDataStorage` factory by
    itself -- both need a concrete, constructible type, which a generic class parameterised over `S`/`C`
    doesn't have. It still `extends SavedData` (rather than becoming a detached data holder), so a future
    concrete subtype satisfies that bound through ordinary inheritance; what it can't supply is the
    `Codec<T>` (26.x) / `Function<CompoundTag, T>` loader (1.20.1), so `createCodec`/`createLoader` are
    generic static factories a concrete subtype calls with nothing but its own no-arg constructor
    reference. No connection-specific codec is needed either way -- a connection is saved purely as its
    grouped `BlockPos` list, exactly 1.12.2's own on-disk shape.
  - `TileMarker`'s `onLoad`/`onChunkUnload` become `clearRemoved`/`setRemoved`, as expected, but the
    genuine-removal-vs-chunk-unload split turned out to differ *between* the two targets, not just from
    1.12.2, contradicting this file's own earlier assumption below (in the `buildcraft.core` survey entry)
    that both targets would need a cooperating `Block#onRemove`: decompiling the real 26.x `LevelChunk`
    shows `Block#onRemove` is gone there entirely (not renamed -- confirmed via `javap` against
    `BlockBehaviour`), replaced by `affectNeighborsAfterRemoval`, which drops the `newState` parameter the
    old `state.getBlock() != newState.getBlock()` trick needed. In its place, 26.x calls a brand new hook
    directly on the block entity, `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)`, fired only on a
    genuine block change, never a chunk unload -- so `TileMarker` handles the whole distinction itself there,
    no cooperating `Block` needed at all. 1.20.1 still has the old `onRemove(state, level, pos, newState,
    movedByPiston)` shape, so that target's `TileMarker` still exposes a public `removeFromMarkerCache()`
    for a future `BlockMarkerBase` to call from its own `onRemove` override, matching the original
    assumption. Both platforms guard against `setRemoved()` (which still fires afterwards either way, for
    both genuine removal and chunk unload) double-handling an already-genuinely-removed marker via the same
    `genuinelyRemoved` flag.
  - `MarkerSubCache`'s abstract `getPossibleLaserType()` is dropped outright (no `LaserData_BC8`, no
    renderer to call it for yet -- see that class's own javadoc), and `MarkerCache.registerCache`'s 1.12.2
    FML-lifecycle guard is dropped with no replacement (no modern equivalent check, and nothing calls this
    before mod construction finishes anyway).
  - **A real, previously-unknown runtime pitfall, found by actually booting the 1.20.1 dedicated server
    (not just compiling) after wiring `MessageMarker` into `BCNetwork`**: reading
    `Minecraft.getInstance().player` (declared type `LocalPlayer`, client-only) directly inside
    `MessageMarker.handle` crashed dedicated-server mod construction with a `BootstrapMethodError` --
    `Attempted to load class net/minecraft/client/player/LocalPlayer for invalid dist DEDICATED_SERVER` --
    even though `handle` itself is never *invoked* server-side (the message is server-to-client only).
    `BCNetwork.register()` still has to pass `MessageMarker::handle` as a method reference on both sides to
    register the codec, and that alone loads and bytecode-verifies the whole class, including `handle`'s
    body; verifying the implicit `LocalPlayer -> Player` widening assignment needs the verifier to resolve
    `LocalPlayer`'s hierarchy, which Forge's runtime dist-cleaner refuses on a dedicated server. Fixed by
    isolating that one touch into its own nested class (`MessageMarker.ClientPlayerLookup`), which only
    loads lazily when actually invoked -- never, server-side. This is a general trap for *any* future
    client-bound message handler on 1.20.1 that reads a client-only type inline, not specific to markers;
    26.x's merged/joined jar has no equivalent restriction (confirmed: the 26.x dev server boots fine with
    `MessageMarker.handle` reading `IPayloadContext#player()`, whose static type is the common `Player`
    already, never `LocalPlayer`, so this never came up there). Worth remembering for whoever writes the
    next client-bound message on 1.20.1.

- **`buildcraft.lib.misc.data.Box` and `buildcraft.core.marker`'s concrete marker types (9 files, both
  platforms) — the volume-box and path connections `buildcraft.lib.marker`'s framework was built for.**
  `Box`, `VolumeCache`/`VolumeConnection`/`VolumeSubCache`/`VolumeSavedData`,
  `PathCache`/`PathConnection`/`PathSubCache`/`PathSavedData`. This turns out to make the deferred-`Box`
  entry below stale for `Box` specifically (left as-is rather than rewritten -- a future pass can clean it
  up): `Box`'s only unported dependency was three `@SideOnly(Side.CLIENT)` fields (`laserData`,
  `lastMin`/`lastMax`, `lastType`) that nothing in its actual geometry ever read, dropped outright with a
  one-line class-javadoc note; `MessageUtil.readBlockPos`/`writeBlockPos` (blocked) turned out to be
  unnecessary too, since `FriendlyByteBuf` already has `readBlockPos()`/`writeBlockPos(BlockPos)` built in
  on both targets. `BoxIterable`/`BoxIterator` and `ProfilerBC` remain deferred exactly as that entry
  describes -- this pass didn't touch them.
  - `IZone#getRandomBlockPos` (inherited through `IBox`) takes a `RandomSource` now, not
    `java.util.Random`, but `PositionUtil#randomBlockPos` still takes the old `java.util.Random` type, so
    `Box#getRandomBlockPos(RandomSource)` reimplements that method's arithmetic directly rather than
    delegating to it.
  - `Vec3(Vec3i)` -- the constructor 1.12.2's `new Vec3d(BlockPos)` idiom relied on -- doesn't exist on
    1.20.1 (confirmed via `javap`; only `Vec3.atLowerCornerOf(Vec3i)` does there), even though 26.x does
    have it. Using `Vec3.atLowerCornerOf` uniformly, and building `getBoundingBox()`'s `AABB` through the
    `AABB(Vec3, Vec3)` constructor rather than `AABB(BlockPos, BlockPos)` (only present on 1.20.1, not
    26.x), keeps `Box.java` textually identical on both platforms -- the one genuine divergence left is
    `CompoundTag`'s int-reading method in the legacy `initialize(CompoundTag)` NBT branch (26.x:
    `getIntOr(key, default)`; 1.20.1: `getInt(key)`, already 0-defaulting).
  - `BlockPos.betweenClosed(min, max)` (the modern rename of `BlockPos.getAllInBox`) reuses a single
    mutable `BlockPos` across the whole iteration; `Box#getBlocksInArea()` calls `.immutable()` on each
    before adding it to the returned `List`, which the original 1.12.2 method never did -- a latent bug in
    the class this ports from, fixed rather than carried over, since nothing in either version has a real
    caller yet to have depended on the broken behaviour.
  - `BCCoreConfig.markerMaxDistance` isn't ported (see this file's deferred-config entries) -- both
    `VolumeConnection`/`VolumeSubCache` and `PathSubCache` use a local
    `private static final int MARKER_MAX_DISTANCE = 64;` (package-visible where a connection type and its
    sub-cache both need it) standing in for it, `64` being that config's own 1.12.2 default
    (`config.get(general, "markerMaxDistance", 64)`), not a new value.
  - `VolumeConnection#renderInWorld`/`PathConnection#renderInWorld` are empty overrides, not the
    laser-drawing 1.12.2 had -- `buildcraft.lib.client.render.laser` and
    `buildcraft.core.client.BuildCraftLaserManager` are both unported rendering code, same situation
    `MarkerConnection#renderInWorld` itself already documents. `PathConnection`'s private `renderLaser`/
    `offset` rendering helpers, and `VolumeConnection`/`PathConnection`'s dropped `@SideOnly(Side.CLIENT)`
    annotations, follow the same reasoning already established for `MarkerConnection`/`MarkerSubCache`.
  - `VolumeSubCache`/`PathSubCache`'s `getPossibleLaserType()` override is deleted outright, not just
    emptied -- the abstract method it overrode no longer exists on `MarkerSubCache` at all (dropped there
    in the previous pass), so an `@Override` here would fail to compile.
  - **`World#getPerWorldStorage().getOrLoadData`/`.setData` needed real per-platform research, and the
    obvious assumption going in (that 1.20.1's `Level#getDataStorage()` is available on a generic `Level`,
    unlike 26.x's `ServerLevel`-only `SavedDataStorage`) turned out to be wrong when checked directly.**
    Confirmed via `javap` against both real merged jars: on 26.x, `Level#getDataStorage` doesn't exist at
    all -- only `ServerLevel#getDataStorage()` does, returning `SavedDataStorage`. On 1.20.1, the same is
    true: `javap` against `net.minecraft.world.level.Level` (and, checked separately, against
    `net.minecraft.client.multiplayer.ClientLevel`) shows neither declares `getDataStorage()` either --
    only `ServerLevel#getDataStorage()` does there too, returning `DimensionDataStorage`. So both targets
    behave identically here: a client `Level` has no on-disk storage reachable at all, and
    `VolumeSubCache`/`PathSubCache`'s constructors branch on `level instanceof ServerLevel serverLevel`,
    skipping the disk load entirely otherwise and relying on `MessageMarker` network sync for the client --
    which is what actually kept 1.12.2's client in sync too, the disk load there always having been a
    server-side concern. On 26.x, the load goes through `serverLevel.getDataStorage().computeIfAbsent(
    VolumeSavedData.TYPE)`, a `SavedDataType<VolumeSavedData>` built from `MarkerSavedData.createCodec` and
    `BuildCraftAPI.nameToResourceId(VolumeSavedData.NAME)` via the 3-argument `SavedDataType(Identifier,
    Supplier<T>, Codec<T>)` constructor (no `DataFixTypes` needed, confirmed present via `javap`). On
    1.20.1, it's `serverLevel.getDataStorage().computeIfAbsent(VolumeSavedData.LOADER, VolumeSavedData::new,
    VolumeSavedData.NAME)`, `LOADER` being `MarkerSavedData.createLoader(VolumeSavedData::new)`.
    `PathSavedData` mirrors this exactly. Both dev servers boot clean with these classes on the classpath
    (nothing yet constructs a `VolumeCache`/`PathCache` to actually exercise the load/save path -- see the
    `buildcraft.lib.marker` entry above for why `registerCache` has no caller yet).

- **The marker blocks, tiles, and connector item are placeable and connectable in-game (6 files, both
  platforms): `buildcraft.lib.block.BlockMarkerBase`, `core.tile.{TileMarkerVolume,TileMarkerPath}`,
  `core.block.{BlockMarkerVolume,BlockMarkerPath}`, `core.item.ItemMarkerConnector`.** This is what the
  `buildcraft.lib.marker`/`buildcraft.core.marker` framework above was actually built for -- a real block a
  player can place, wrench-rotate, and connect. Registered in `BCCoreRegistries` (`marker_volume`,
  `marker_path`, `marker_connector`) with textures/models/blockstates/loot tables/recipes/lang pulled from
  `buildcraft_resources/assets/buildcraftcore/`, following the same pattern `BlockSpringWater`/
  `BlockPowerConsumerTester`/`ItemWrench` already established. Verified with forced rebuilds, the full test
  suite, and real dedicated-server boots on both targets (registration/asset bugs like a bad blockstate JSON
  or missing loot table only surface there, not at compile time).
  - **`MarkerCache.registerCache(VolumeCache.INSTANCE)`/`registerCache(PathCache.INSTANCE)` finally has a
    caller.** Both were left unregistered when `buildcraft.lib.marker`/`buildcraft.core.marker` landed, since
    nothing constructed one yet. Without registering, `VolumeSubCache`/`PathSubCache`'s own
    `MarkerCache.CACHES.indexOf(...)` lookup (their `cacheId`) returns `-1`, silently breaking every
    `MessageMarker` these tiles send -- this is now wired into `BCCoreRegistries`' `register(modBus)`.
  - **The old id-tagged network-payload system (`writePayload`/`readPayload`/`sendNetworkUpdate(id)`/
    `IdAllocator`) is gone, and doesn't need replacing -- it needs deleting.** `TileMarkerVolume`'s
    `showSignals` boolean used to need its own `NET_SIGNALS_ON`/`NET_SIGNALS_OFF` payload pair; `TileBC`'s
    `markDirtyAndSync()` (already landed, see its own javadoc) syncs the block entity's entire saved state to
    tracking clients, so `showSignals` is now just an ordinary persisted field
    (`saveAdditional`/`loadAdditional` on 1.20.1's `CompoundTag`; `ValueInput`/`ValueOutput` on 26.x), and
    `switchSignals()` just calls `markDirtyAndSync()`. A genuine simplification, not a compromise.
  - **`IBlockState#getActualState`'s removal (already known from `ItemWrench`) forces a real design change
    here, not just a rename.** `BlockMarkerBase.getActualState` used to synthesise
    `BuildCraftProperties.ACTIVE` from the tile's `isActiveForRender()` at render time; with no render-time
    state override left at all, `ACTIVE` has to be a persisted blockstate value the tile pushes explicitly.
    `TileMarkerVolume`/`TileMarkerPath` each added a `refreshActiveState()` helper, called from every method
    that can change whether a connection exists (`switchSignals`, `onPlacedBy`, `onManualConnectionAttempt`),
    diffing against the current blockstate before writing (`Block.UPDATE_CLIENTS`, sync-only, deliberately not
    a neighbour-notifying flag -- nothing here reacts to `ACTIVE`, so there's no loop to cause). Connections
    formed through `ItemMarkerConnector` (which calls `cache.tryConnect` directly, bypassing the tiles' own
    methods -- the *only* way path markers ever connect at all) get an equivalent helper inside that class
    instead. One documented, currently-harmless gap: a marker whose connection is invalidated by a *different*
    marker's removal never gets its own `ACTIVE` refreshed, since that path runs entirely inside
    `MarkerSubCache`/`MarkerConnection` -- inert today since nothing renders `ACTIVE` yet (no renderer, same
    deferral `MarkerConnection#renderInWorld` already documents).
  - **Wrench rotation reuses `buildcraft.lib.block.IBlockWithFacing`** (already ported, built around
    `RotationUtil.rotateAll`) rather than a hand-rolled `ICustomRotationHandler#attemptRotation` override --
    `BlockMarkerBase` just implements it and returns `true` from `canFaceVertically()`. `RotationUtil.rotateAll`
    cycles faces in a different order (north-east-south-west-up-down) than 1.12.2's
    `VanillaRotationHandlers.ROTATE_FACING` (east-south-down-west-north-up) -- same "cycle through all six
    faces" behaviour, different order, and reusing an established mechanism beat reimplementing one that just
    happens to order its cycle differently.
  - **`BlockMarkerVolume`'s periodic redstone re-check (1.12.2's randomly-ticked `updateTick`) is dropped, not
    reproduced with a scheduled tick.** Verified against real decompiled vanilla redstone-component source
    (`DiodeBlock` and friends) that a power change always fires `neighborChanged` on every adjacent block, and
    vanilla's own components rely on exactly that rather than random ticking to catch signal changes --
    `checkSignalState` being wired only into `neighborChanged` is already sufficient in practice.
    `World#isBlockPowered(pos)` is `Level#hasNeighborSignal(BlockPos)` now (a default method inherited through
    `SignalGetter`, identical on both targets, confirmed via `javap`).
  - **`BlockMarkerVolume#neighborChanged` deliberately doesn't call `BlockMarkerBase`'s self-destruct-if-
    unsupported check** -- not a new decision, a preserved 1.12.2 quirk: the original fully overrode its base
    class' `neighborChanged` the same way, so volume markers never actually self-destroyed when their support
    block was removed, unlike path markers (which don't override `neighborChanged` at all, keeping the base
    behaviour). Carried over unchanged rather than "fixed".
  - `World#isSideSolid(pos, side)` (`BlockMarkerBase.canPlaceBlockOnSide`) is
    `BlockState#isFaceSturdy(BlockGetter, BlockPos, Direction)` on the *neighbouring* block's state now,
    identical signature on both targets (confirmed via `javap`), wired into a real `canSurvive` override (a
    placement-validity hook 1.12.2 didn't have here) as well as `neighborChanged`.
  - `AxisAlignedBB`-returning `getBoundingBox`/`getCollisionBoundingBox` are `VoxelShape`-returning
    `getShape`/`getCollisionShape` now. `Block.box(...)`'s pixel-scale (0-16) arguments just divide by 16
    before reaching `Shapes.box(...)`, which already takes 0-1-scaled coordinates (confirmed via decompiled
    `Block#box` source) -- so the original six `AxisAlignedBB` literals carry over completely unchanged, just
    re-wrapped. `getCollisionShape` returns `Shapes.empty()` for the same "no collision, walk straight
    through" behaviour 1.12.2's `getCollisionBoundingBox() -> null` had.
  - `getRenderBoundingBox()`/`getMaxRenderDistanceSquared()` (1.12.2 client render-distance hints on
    `TileEntity`) don't exist in any form on `BlockEntity` any more (confirmed via `javap` on both targets) --
    dropped rather than forced into a non-existent equivalent.
  - **`ItemMarkerConnector` is a partial port.** 1.12.2's single `onItemRightClick` bundled two unrelated
    features: the marker-line connector (`interactCache`/`MarkerLineInteraction`, looking along the player's
    view line for two nearby markers of the same cache type to connect -- ported in full, onto the modern
    `Item#use(Level, Player, InteractionHand)`) and a second, entirely separate volume-box "addon" region
    editor (`onItemRightClickVolumeBoxes`, depending on `buildcraft.core.marker.volume.*` -- `Addon`,
    `AddonsRegistry`, `VolumeBox`, `WorldSavedDataVolumeBoxes`, `Lock`, `EnumAddonSlot`). That whole
    sub-feature was already explicitly deferred when this package's other marker types were ported (see this
    file's own entry above) and remains entirely unported; porting the addon system is its own project,
    independent of the marker-connector feature this class otherwise provides in full.
  - New divergence-table rows worth knowing: `BlockBehaviour.Properties#noCollision()` (26.x, correctly
    spelled) vs. `#noCollission()` (1.20.1, keeps the old double-s typo); and on 26.x, `BlockBehaviour` hooks
    like `getShape`/`getCollisionShape`/`canSurvive`/`neighborChanged`/`useWithoutItem` are `protected`, while
    the same hooks (`getShape`/`getCollisionShape`/`canSurvive`/`neighborChanged`/`use`) are `public` on
    1.20.1 -- both confirmed via `javap` against the real merged jars.
  - Not yet done: no in-game interaction test beyond a clean dedicated-server boot (give/place/connect via a
    real client) -- the registration and asset pipeline is verified, but nobody has watched a marker connect
    in a running game yet. Worth doing before relying on this for anything downstream (a `builders` machine
    that reads a `VolumeConnection`'s box, for instance).

- **`core.block.BlockDecoration` and `core.item.ItemGoggles`.** Both graduate out of the
  `buildcraft.core.{item,block,tile,gen}` survey above (their entries there are left as written, since they
  correctly describe why each needed a real design pass rather than a mechanical port).
  - **`BlockDecoration`** is six real blocks (`decorated_destroy`, `decorated_blueprint`, `decorated_template`,
    `decorated_paper`, `decorated_leather`, `decorated_laser_back`) in place of 1.12.2's single block with a
    six-value `EnumDecoratedBlock` blockstate property -- the same metadata-subtype split
    `BlockSpringWater`/`DyedBlockVariants` already established, but with *no* surviving per-variant behaviour
    at all this time: `getSubBlocks`/`damageDropped` only ever enumerated/reported metadata, and
    `getLightValue(state, world, pos)` -- the one real behaviour `EnumDecoratedBlock.lightValue` carried --
    moves to `BlockBehaviour.Properties#lightLevel(ToIntFunction<BlockState>)`, confirmed via `javap` to exist
    identically on both targets and supplied per instance from `BCCoreRegistries` rather than read back off
    blockstate. That leaves `BlockDecoration` itself an empty `Block` subclass, kept as a real named type (not
    six bare `Block` instances) purely so other code has something to `instanceof` against, the same reasoning
    `BlockSpringWater` already used. Textures are the real 1.12.2 assets pulled from
    `buildcraft_resources/assets/buildcraftcore/textures/blocks/` (`blueprint/blue`, `blueprint/black`,
    `misc/texture_red_dark`, `misc/paper`, `misc/leather`) except `laser_back`, whose 1.12.2 model pointed at
    `buildcraftsilicon:blocks/laser/bottom` -- `buildcraft.silicon` isn't ported yet, so that one texture is
    copied over from the still-unported module's own resource tree rather than invented. Properties
    (`MapColor.METAL`, `strength(5.0F, 10.0F)`, `SoundType.METAL`) match what `BlockBCBase_Neptune`'s
    constructor actually gave every 1.12.2 BuildCraft block by default (`Material.IRON`, hardness 5, resistance
    10, `SoundType.METAL`) -- `BlockDecoration` never overrode any of them -- rather than reusing
    `BlockPowerConsumerTester`'s already-ported `(5.0F, 6.0F)`, which turns out to not match that same default
    either, a preexisting minor inconsistency left alone rather than "fixed" here.
  - **`ItemGoggles` is the first place the two targets have needed genuinely different code for the same
    feature**, not just renamed API calls -- see the new divergence-table row below and each platform's own
    class javadoc for the full account. In short: 1.12.2 wrapped `ItemArmor` in Forge's `ISpecialArmor` to force
    zero defense and skip durability damage. On 26.x, `javap` confirms `ArmorItem` doesn't exist as a class at
    all any more -- equipping is purely the `Equippable` data component (real decompiled source read from the
    merged jar), which carries no defense value of its own, so `ItemGoggles` is just a plain `Item` with an
    `Equippable.builder(EquipmentSlot.HEAD)` component and no `ItemAttributeModifiers` component added on top
    (the same shape vanilla's own `Items.CARVED_PUMPKIN` uses for a zero-defense head-slot item -- read
    straight from `Items.java` in the bundled decompiled source, not invented); `damageOnHurt` is set `false`
    on the component to mirror the original's explicit no-op, though it was already moot, since the item never
    gets a `DataComponents.MAX_DAMAGE` component and `LivingEntity#hurtArmor` only calls `hurtAndBreak` when
    `isDamageableItem()` is true. On 1.20.1, `javap` confirms the opposite: `ArmorItem`/`ArmorMaterial` are
    still real (`ISpecialArmor` is the one now confirmed gone -- "class not found"), with `ArmorMaterial`
    demoted from an enum to a plain interface `ArmorItem`'s constructor reads directly to build its
    `Attributes.ARMOR`/`ARMOR_TOUGHNESS`/`KNOCKBACK_RESISTANCE` modifiers -- so `ItemGoggles` implements that
    interface itself with every numeric value zeroed, in place of reusing `ArmorMaterials.CHAIN`. Durability 0
    leaves `Item#isDamageableItem()` (`maxDamage > 0`) false, which is what `ItemStack#hurtAndBreak` actually
    checks before doing anything -- the same "never damageable" end state as 26.x, reached by a different
    mechanism. The enchantment value (12, matching `ArmorMaterials.CHAIN`) is kept on 1.20.1 even though every
    defense number is zeroed, since 1.12.2 never overrode `getItemEnchantability` and so inherited chainmail's
    default -- the one incidental property worth preserving alongside the intentionally-zeroed ones.
  - Both registered in `BCCoreRegistries` (`decorated_destroy` through `decorated_laser_back`, and `goggles`)
    with textures/models/blockstates/loot tables/lang pulled or written following `BlockSpringWater`/
    `BlockPowerConsumerTester`'s established pattern; `goggles` needs only an inventory-icon item model, since
    no rendering pipeline exists yet to draw a worn item either way (consistent with every other rendering
    deferral elsewhere in this file). Verified with forced rebuilds, the full test suite, and real
    dedicated-server boots on both targets -- no registration or component-wiring error surfaced on either.

- `buildcraft.lib.misc.data.{Box,BoxIterable,BoxIterator}` and `.ProfilerBC` -- deferred as a
  group. `Box` (328 lines) needs `buildcraft.lib.client.render.laser.LaserData_BC8` (rendering,
  not ported) and `MessageUtil` (blocked, needs the old `IMessage` networking stack); it also
  has no consumer yet (every reader is in the unported `builders`/`core`/`energy`/`silicon`
  modules). `BoxIterable` exists only to construct a `BoxIterator`, so the two travel together.
  `BoxIterator` (256 lines) itself has no blocked dependency -- everything it needs
  (`NBTUtilBC`, `StringUtilBC`, `VecUtil`, `AxisOrder`) is already ported -- but it is dense,
  order/invert/repeat-aware 3-axis iteration logic (`advance`/`moveTo`/`compare`/`willVisit`/
  `hasVisited`) with no ported consumer to verify correctness against yet; a wrong axis-order
  edge case here would be easy to miss without a real caller exercising it, so it waits for one
  rather than shipping unverified. `ProfilerBC` is a client-only wrapper around
  `Minecraft.getMinecraft()` and the old `Profiler` (see `ProfilerUtil`'s already-ported
  `Profiler`->`ProfilerFiller` split) with no consumer either; low value to port ahead of the
  rendering pass it belongs with.

- **Survey of the rest of `buildcraft.core.{item,block,tile,gen}`** (20 of 24 files, `ItemWrench`,
  `BlockSpringWater` and `TilePowerConsumerTester`/`BlockPowerConsumerTester` aside). Unlike
  `buildcraft.lib`'s utility layer, almost none of this is mechanical -- each file needs either a
  real architectural decision this port hasn't made yet, or an unported subsystem. Recorded here
  so the next pass doesn't have to re-derive it:
  - `BlockDecoration`/`ItemBlockDecorated` is single-`Block`-with-metadata-subtypes
    (`EnumDecoratedBlock`, 6 values) -- item #1 on PORTING.md's own structural-changes list,
    "block metadata is gone". Needs splitting into one real `Block`/`BlockItem` per enum value,
    the same shape `BlockSpringWater` now demonstrates for `EnumSpring`'s water half (see the
    entry above) and `DyedBlockVariants` already uses for colour families -- not a per-file
    port. Has no reader anywhere in the currently-ported tree to design the split against yet
    (unlike spring water, nothing pulls it in even indirectly).
  - `core.gen.SpringPopulate` (water/oil spring world generation) is built on
    `PopulateChunkEvent`/`TerrainGen`, Forge's old chunk-populate hook. There is no equivalent
    event on either target -- world generation is entirely datapack/`Feature`-driven now
    (`Feature<NoneFeatureConfiguration>` registered through `BiomeModifications` or a
    `ConfiguredFeature`/`PlacedFeature` JSON pair). A real feature to write, not a rename. Until
    this lands, `BlockSpringWater` (ported) has no way to spawn naturally -- same "foundation
    ported, not yet wired to its trigger" situation as `DeltaManager`/`lib.cache` and `TileBC`.

    Update: the water half is done -- see the `core.gen.SpringGenerator` progress entry below. This
    guess at the modern shape was also only half right: 26.x turned out to have collapsed
    `Feature`/`ConfiguredFeature` into one tier, not kept the classic three-tier system this note
    assumed applies uniformly -- see that entry for the real, `javap`-verified shape on each target.
  - `core.tile.ITileOilSpring` is a two-method marker interface with nothing wrong with it, but
    its only implementor is `buildcraft.energy.tile.TileSpringOil`, entirely unported; nothing
    to port it *for* yet. `BlockSpringOil` (the other `EnumSpring` half) waits alongside it: its
    block entity is only sometimes present, decided by whether `buildcraft.energy` has
    registered one at all -- a runtime, cross-module decision that 1.12.2 expressed by mutating
    a shared `EnumSpring.OIL` instance, but doesn't map onto the modern `EntityBlock`/
    `BlockEntityType` model's *static*, registration-time-only block entity typing. Needs a real
    design once `buildcraft.energy` exists to design it against, not a mechanical copy of the
    water half.
  - `ItemGoggles` implements `ISpecialArmor` (confirmed absent from both targets' jars) to make
    a zero-defense, damage-immune helmet. Modern armor is a bigger redesign than a rename can
    cover: there is no `ArmorItem` class to extend any more (verified via `javap` -- armor
    material and rendering moved to a `net.minecraft.world.item.equipment.ArmorMaterial`
    record that requires a `ResourceKey<EquipmentAsset>`, a new equipment-rendering asset
    registry this port hasn't touched yet) -- needs its own design pass, not a quick port.
  - `ItemPaintbrush_BC8` is a 17-metadata-subtype item (1 "clean" + 16 dye colours) storing
    remaining uses in NBT keyed by damage-as-metadata -- the same subtype-removal redesign
    `StackUtil`'s class javadoc already describes in the abstract, here in concrete form. It
    also needs `ParticleUtil` (rendering, not ported) and `SpecialColourFontRenderer`
    (rendering). A real redesign (one item, uses + colour as data components) rather than a
    port.
  - `ItemMapLocation` needs `buildcraft.lib.misc.data.Box`, deferred above for its own reasons.
    `ItemVolumeBox`, `ItemMarkerConnector`, `BlockMarkerPath`/`BlockMarkerVolume` and
    `TileMarkerPath`/`TileMarkerVolume` all need `buildcraft.core.marker`/
    `buildcraft.lib.marker`, neither ported (see the next entry). `ItemFragileFluidContainer`
    needs `buildcraft.lib.fluid` (unported) and a `Capability<IFluidHandler>` (the `CapUtil`
    chain, already documented as blocked). `ItemList_BC8` needs `buildcraft.lib.list`,
    deferred above for having no ported consumer. `ItemEngine_BC8`/`BlockEngine_BC8`/
    `TileEngineCreative`/`TileEngineRedstone_BC8` all need `buildcraft.lib.engine`, unported.
  - `buildcraft.lib.marker` (4 files) was checked directly as part of this survey: blocked on
    `buildcraft.lib.tile.TileMarker` (unported -- and its own `onLoad`/`onChunkUnload` hooks
    are themselves gone from `BlockEntity`, folded into `setLevel`/`clearRemoved`/`setRemoved`;
    distinguishing "chunk unloaded" from "genuinely destroyed", which 1.12.2 could tell apart
    at the tile level, now needs the owning `Block`'s `onRemove(state, level, pos, newState,
    movedByPiston)` comparing `state.getBlock() != newState.getBlock()` instead -- a real
    design point for whoever ports `TileMarker`, not a one-line rename), and
    `buildcraft.lib.client.render.laser.LaserData_BC8` (rendering, unported; `MarkerSubCache`'s
    one use of it, `getPossibleLaserType()`, can simply be dropped when that file is ported --
    nothing calls it without a renderer to call it for). The message-dispatch blocker this
    entry used to flag is resolved -- see the "Networking" structural-change entry and the new
    `buildcraft.lib.net`/`BCNetwork` progress entry below -- so `MessageMarker` itself is now
    just an ordinary message to write once `MarkerCache` exists to route it through, not a
    design problem in its own right.

    Update: this whole entry is superseded -- `buildcraft.lib.marker` and `TileMarker` are both ported now
    (see the new `buildcraft.lib.marker` progress entry above), and the `onRemove` assumption two sentences
    up turned out to only hold for 1.20.1; 26.x found a better dedicated hook instead. See that entry for
    the full account. Only `buildcraft.core.marker`'s concrete subtypes remain unported.

- **The engine subsystem: `buildcraft.lib.engine.{TileEngineBase,EngineConnector,IEngineLikeForLedger}` plus two
  real machines, `core.block.{BlockEngineWood,BlockEngineCreative}`/`core.tile.{TileEngineWood,TileEngineCreative}`
  (both platforms).** The largest single feature this port has taken from `buildcraft.core`/`buildcraft.lib` so
  far -- MJ storage, a heat/power-stage state machine driving an overheat condition, chain-of-engines power
  routing, and a redstone-pulsed-vs-constant power split, all ported faithfully from `TileEngineBase_BC8`
  (660 lines). What changed is purely the plumbing, following patterns already established elsewhere in this
  port; see `TileEngineBase`'s own (long) class javadoc for the full account on each target. Registered
  (`engine_wood`, `engine_creative`) in `BCCoreRegistries` with textures/models/blockstates/loot
  tables/lang pulled from `buildcraft_resources/assets/buildcraftcore/`, a datapack recipe for `engine_wood`
  (a redstone-torch-free reproduction of the original's shaped recipe, using vanilla planks/glass/piston plus
  `gear_wood` rather than 1.12.2's ore-dictionary keys), and no recipe for `engine_creative` (creative-tab-only in
  1.12.2 too). Verified with forced rebuilds, the full 25-test suite, and clean dedicated-server boots on both
  targets (no registration/asset/capability error surfaced on either) -- but **not** with a full in-game
  placed-and-ticking check: this session could not get an interactive server console attached in its sandboxed
  environment (tried a named-pipe-fed stdin and writing directly to the server process's `/proc/<pid>/fd/0`;
  neither delivered a typed command to the running dedicated server), so `/setblock`-and-observe was not
  completed. Worth doing properly before relying on this for anything downstream.
  - **Renaming.** `TileEngineBase_BC8` -> `TileEngineBase` (dropping the `_BC8` suffix, matching this port's
    established convention of dropping 1.12.2 version-tag suffixes -- `ItemBC_Neptune`/`BlockBCTile_Neptune`/
    `TileBC_Neptune` and friends are all gone the same way already). `TileEngineRedstone_BC8` -> `TileEngineWood`:
    despite its class name this was never a combustion engine, it just outputs a small constant MJ draw while
    redstone-powered (hence the "free power" advancement) and was registered in 1.12.2 under the `WOOD`
    `EnumEngineType`/`tile.engine.wood` tag -- the new name follows what it actually is and how it was
    registered, not the misleading class name. `TileEngineCreative` keeps its name. 1.12.2's single, multi-variant
    `BlockEngine_BC8` (metadata-subtyped via `EnumEngineType`, extended by `BlockEngineBase_BC8`'s
    `registerEngine(type, constructor)` machinery so `buildcraft.core` could wire up WOOD/CREATIVE while
    `buildcraft.energy`, unported, plugged STONE/IRON/RF into the *same block instance* later) splits into two
    real, independent blocks, `BlockEngineWood`/`BlockEngineCreative` -- the same one-real-`Block`-per-variant
    split `BlockSpringWater`/`BlockDecoration` already established, with the added twist that this cross-module
    "other modules register more variants into my block later" design has no ported equivalent at all needed:
    `BlockEngineBase_BC8`'s variant/`registerEngine` machinery itself is not ported, only the two concrete engine
    tiles it used to host. `ItemEngine_BC8`, which existed purely to pick a model variant per metadata damage
    value, has nothing left to do and is not ported either -- `BCRegistry.addBlockAndItem`'s default `BlockItem`
    is sufficient, same as every other block in this port. `buildcraft.core.tile.ITileOilSpring` (a two-method
    marker interface whose only implementor, the unported `buildcraft.energy.tile.TileSpringOil`, doesn't exist in
    this port) and the `STONE`/`IRON`/`RF` engine types (`buildcraft.energy`, unported) are out of scope, matching
    the existing `buildcraft.core` survey entry's own reasoning for both.
  - **Capabilities.** An engine tile's `mjConnector` field (typed `IMjConnector`, always a plain `EngineConnector`
    for both concrete engines) turns out to be the *only* MJ capability either engine ever exposes: `EngineConnector`
    implements nothing beyond `IMjConnector`, so 1.12.2's `MjCapabilityHelper` `instanceof`-probing it for
    `IMjReceiver`/`IMjRedstoneReceiver`/`IMjReadable`/`IMjPassiveProvider` never matched anything there either --
    an engine pushes power outward by calling a neighbour's `receivePower`, it is never itself received from, read,
    or pulled from. So on 26.x, `BCCoreRegistries#registerCapabilities` registers only `MjCapabilities.CONNECTOR`
    for each engine block entity type, directly (not through `MjCapabilityHelper.registerAll`, which is built to
    expose every MJ capability unconditionally on every side -- not what an engine, which only ever answers on its
    `currentDirection` face, wants), guarded by `side == tile.getCurrentFacing()`. On 1.20.1, the tile itself holds
    a `LazyOptional<IMjConnector>` and answers `getCapability` with the same guard, matching
    `TilePowerConsumerTester`'s already-established per-instance pattern. **RF auto-conversion did not make it
    in.** 1.12.2's `getReceiverToPower(TileEntity, EnumFacing)` had an RF-fallback branch
    (`MjToRfAutoConvertor.createReceiver(rf)`, wrapping a neighbour's foreign `IEnergyStorage` to *look like* an
    `IMjReceiver`), but the `MjToRfAutoConvertor` class already built on both platforms goes the *opposite*
    direction -- it wraps an `IMjConnector` to *look like* Forge/NeoForge energy to outside callers, which is what
    lets an external mod pull RF out of a BuildCraft machine, not what lets an engine push MJ into a foreign RF
    machine. Writing the reverse adapter this call site would need is new work outside this pass's scope, not a
    rename of something already built, so `getReceiverToPower(Direction)` here is MJ-to-MJ only -- deliberately,
    documented in `TileEngineBase`'s own javadoc, not silently dropped.
  - **Rotation.** `attemptRotation()` skips any face without a valid power receiver behind it
    (`isFacingReceiver`), which rules out reusing `IBlockWithFacing`/`RotationUtil.rotateAll` the way
    `BlockMarkerBase` does (that cycles blindly through all six faces with no such check). 1.12.2 drove the cycle
    order from `VanillaRotationHandlers.ROTATE_FACING` (unported -- see this file's own "deliberately not ported"
    entry for that class, which also covers ~25 unrelated vanilla-block rotation handlers with no bearing here),
    so the same six-direction cycle (east-south-down-west-north-up) is reproduced inline in `TileEngineBase` using
    the already-ported, pure-Java `buildcraft.lib.misc.collect.OrderedEnumMap` rather than porting the whole
    unrelated class. Both concrete engine blocks implement `ICustomRotationHandler` directly (matching 1.12.2's
    own per-block-class implementation) and delegate to the tile's `attemptRotation()`, which
    `CustomRotationHelper.INSTANCE.attemptRotateBlock` (already ported, unchanged) dispatches to automatically via
    an `instanceof ICustomRotationHandler` check -- no extra registration needed.
  - **Block shape/face-solidity: real, decompiled-source-verified research, not a guess.** `javap` against
    `BlockBehaviour` on both targets' merged jars shows `getBlockFaceShape`/`isSideSolid` have no surviving
    override point at all any more -- `isFaceSturdy` (the modern rename `BlockMarkerBase` already uses, for a
    *different* purpose: checking a *neighbour's* face) is declared on `BlockBehaviour$BlockStateBase`, not on
    `BlockBehaviour`/`Block` itself, and is computed purely from the block's own `VoxelShape` geometry via
    `SupportType.FULL.isSupporting`. There is nothing left for a block to override to declare "only this one face
    is solid" independent of its collision shape. Since there is also no rendering pipeline in this port yet to
    feed 1.12.2's `EnumBlockRenderType.ENTITYBLOCK_ANIMATED` custom model (matching every other machine ported so
    far), both engine blocks use an ordinary static block model with no shape override at all -- `Block`'s own
    default (a full cube) is exactly what's wanted with no renderer to justify anything else, and a full cube is
    therefore sturdy on every side rather than just the one opposite `currentDirection`. This is a real,
    documented behaviour change from 1.12.2, not an oversight; reproducing the old behaviour would need a custom
    per-tile `VoxelShape`, undesirable without a renderer to justify the geometry.
  - **Ticking.** `ITickable#update()` becomes `TileEngineBase#serverTick()`, wired through each concrete engine
    block's `EntityBlock#getTicker` exactly like `BlockPowerConsumerTester`/`TilePowerConsumerTester` already do.
    The client-side half of the original `update()` (the piston `progress` animation interpolating every client
    tick while `isPumping`, plus `getProgressClient`/`lastProgress`/`clientModelData`/`ModelVariableData`) is
    dropped rather than kept inert: nothing in this port can register a `BlockEntityRenderer` yet to consume it,
    matching every other rendering deferral already documented elsewhere in this file. `progress` and
    `progressPart` are still tracked and persisted server-side, so a future renderer has real data to read.
  - **Owner tracking for the "free power" advancement.** `getOwner().getId()` was part of the old, much larger
    `TileBC_Neptune`, with no equivalent on the current, slimmer `TileBC`. `TileEngineWood` (not the shared base,
    since this is wood-engine-specific) adds its own small `@Nullable UUID owner` field, set from `setPlacedBy`'s
    `placer` argument the same "the block calls the tile's own placement hook" pattern `TileMarkerVolume
    #onPlacedBy`/`BlockMarkerVolume#setPlacedBy` already established, persisted, and passed to the already-ported
    `AdvancementUtil.unlockAdvancement(UUID, Identifier/ResourceLocation)` overload in place of the original
    direct call. The advancement JSON itself (`buildcraftcore:free_power`) is not ported, matching the exact
    precedent already set for `ItemWrench`'s `buildcraftcore:wrenched` -- `AdvancementUtil.unlockAdvancement`
    already tolerates an unregistered advancement id as a harmless one-time warning.
  - **A genuine, worth-flagging finding, not a guess: `TileEngineCreative`'s wrench-driven power-output cycling
    (`onActivated`) is very likely unreachable through an actual wrench, on both targets, and was probably
    already unreachable in 1.12.2 too.** `ItemWrench#useOn` intercepts every wrench right-click through
    `CustomRotationHelper.INSTANCE.attemptRotateBlock` and returns a definite result before a block's own
    interaction hook (`useItemOn`/`use`) is ever reached, and since `BlockEngineCreative` also implements
    `ICustomRotationHandler`, wrenching it always rotates it rather than falling through. 1.12.2's own
    architecture is the same shape (`ItemWrench_Neptune` intercepted rotation the same way, ahead of
    `BlockBCTile_Neptune#onBlockActivated`'s delegation to `TileEngineCreative#onActivated`), so this is a
    faithfully-ported pre-existing quirk, not a new bug -- but it is still ported and wired in (via `useItemOn` on
    26.x, `use` on 1.20.1), documented in `BlockEngineCreative`'s own javadoc, in case a future wrench redesign
    changes the short-circuiting.
  - New divergence-table rows worth knowing: `Player#sendSystemMessage(Component)` (26.x, no action-bar flag;
    the action-bar equivalent is the separate `Player#sendOverlayMessage(Component)`) vs.
    `Player#displayClientMessage(Component, boolean)` (1.20.1, action-bar flag still inline) -- found porting
    `TileEngineCreative#onActivated`'s status message. `World#isBlockIndirectlyGettingPowered(pos) -> int` is
    `Level#getBestNeighborSignal(pos) -> int` on both targets (confirmed via `javap` against `SignalGetter`,
    identical signature on both) -- the direct modern successor, alongside the already-known
    `isBlockPowered`/`hasNeighborSignal` (boolean) rename `BlockMarkerVolume` already uses.

- **`core.gen.SpringGenerator` (both platforms), replacing 1.12.2's `core.gen.SpringPopulate` -- the last
  piece of `core.block.BlockSpringWater` (registered since the `ItemWrench`/`BlockSpringWater` progress entry
  above, but never spawned anywhere until now).** 1.12.2's version was a `@SubscribeEvent`-driven
  `PopulateChunkEvent.Post` handler calling `TerrainGen.populate` for permission, then placing blocks directly
  with `World#setBlockState`. That whole imperative, cancellable-event world-gen style is gone on both
  targets, replaced by a declarative `Feature`/datapack system -- but the earlier guess in the
  `buildcraft.core` survey above (that both targets share one `Feature<FC>`/`ConfiguredFeature`/`PlacedFeature`
  three-tier shape) turned out to be wrong for 26.x specifically, confirmed by real `javap`/decompiled-source
  research against both merged jars rather than assumed:
  - **1.20.1 keeps the classic shape.** `Feature<FC extends FeatureConfiguration>` (abstract class,
    `place(FeaturePlaceContext<FC>)`) is wrapped in a `ConfiguredFeature<FC, Feature<FC>>` (datapack JSON,
    `Registries.CONFIGURED_FEATURE`) which is wrapped in a `PlacedFeature` (also datapack JSON,
    `Registries.PLACED_FEATURE`). Only the bare `Feature<FC>` type itself is a static code registry
    (`Registries.FEATURE`, confirmed via `javap`: `ResourceKey<Registry<Feature<?>>>`) -- registered here via
    a new `BCCoreFeatures` (`DeferredRegister<Feature<?>>`, mirroring the pattern `BCCoreRegistries` already
    uses for every other registry). `SpringGenerator extends Feature<NoneFeatureConfiguration>` (vanilla's own
    "no config" marker type, since this feature has no real configuration beyond which block to place) and the
    `ConfiguredFeature`/`PlacedFeature` pair are plain JSON under
    `data/buildcraft/worldgen/{configured_feature,placed_feature}/spring_water.json` -- the former still needs
    an explicit empty `"config": {}` even with `NoneFeatureConfiguration`, confirmed against vanilla's own
    bundled `void_start_platform.json`.
  - **26.x collapsed this into two tiers, not three.** `javap` against the real 26.x merged jar returns "class
    not found" for `net.minecraft.world.level.levelgen.feature.ConfiguredFeature` -- it does not exist at all.
    `Feature` itself is now a plain **interface** (`place(WorldGenLevel, ChunkGenerator, RandomSource,
    BlockPos)`, no `FeaturePlaceContext`), and a concrete feature is a **record implementing `Feature`
    directly**, carrying its own configuration as record fields and its own `codec()` -- confirmed against
    vanilla's own decompiled `SpringFeature` (`record SpringFeature(FluidState, boolean, int, int,
    HolderSet<Block>) implements Feature`), which this port's `SpringGenerator` (a zero-field record, since
    there's nothing to configure) follows exactly. The "configured feature" concept folded into the feature
    instance itself, and the two old registries traded roles: `Registries.FEATURE_TYPE` is now the *static*,
    code registry (holds `MapCodec<? extends Feature>` -- the codec a feature deserializes through, confirmed
    via `javap` and vanilla's own `FeatureTypes.bootstrap`, which registers vanilla's `SpringFeature.CODEC`
    under id `spring_feature` this way), while `Registries.FEATURE` is now the *dynamic*, datapack registry
    (holds actual, fully-configured `Feature` instances -- confirmed via `javap`:
    `ResourceKey<Registry<Feature>>`, not `Feature<?>`). `PlacedFeature` is unchanged in role on both targets
    (a `Holder<Feature>` + placement modifiers, still `Registries.PLACED_FEATURE`, still pure JSON). So on
    26.x, `BCCoreFeatures` registers a `DeferredRegister<MapCodec<? extends Feature>>` against
    `Registries.FEATURE_TYPE` (a `DeferredHolder<MapCodec<? extends Feature>, MapCodec<SpringGenerator>>`,
    `SpringGenerator.CODEC` built with `MapCodec.unit(SpringGenerator::new)` since there are no fields to
    serialize), and the actual feature/placed-feature entries are JSON under
    `data/buildcraft/worldgen/{feature,placed_feature}/spring_water.json` -- the former just
    `{"type": "buildcraft:spring_water"}`, no config block needed at all.
  - **Generation-rarity and column placement moved into declarative `PlacementModifier`s, not hand-rolled
    Java.** 1.12.2's "every 40th chunk" (`random.nextFloat() > 0.025f`) and `random.nextInt(16)` column pick
    are now a `minecraft:rarity_filter` (`"chance": 40`) and `minecraft:in_square` in this feature's
    `placed_feature` JSON (identical field names/ids on both targets, confirmed via `javap` and vanilla's own
    bundled `spring_water.json`/`lake_lava.json`) -- decompiled `RarityFilter#shouldPlace` computes exactly
    `random.nextFloat() < 1.0F / chance`, the same 1-in-40 odds. A closing `minecraft:height_range` (pinned to
    `above_bottom: 0` via a `minecraft:constant` height provider) anchors the scan to the world floor in place
    of the original's implicit `y=0`, and a trailing `minecraft:biome` filter matches the sanity check every
    real vanilla placed feature ends its chain with. The bedrock scan and water-fill loop themselves stayed in
    Java (in `SpringGenerator#place`) rather than being expressed declaratively too, since they need to inspect
    real block state column-by-column -- not something a `PlacementModifier` can do; the divide drawn here is
    "pure probability/position decisions go in JSON, world-state inspection stays in Java."
  - **Nether/End exclusion moved from a runtime dimension check to biome targeting, per the task's own
    suggested (and confirmed cleaner) approach.** 1.12.2 checked `dimId == -1 || dimId == 1` in Java; this
    feature instead is simply never attached to a Nether/End biome, via the `#minecraft:is_overworld` biome
    tag (confirmed present and correctly excluding Nether/End in both real merged jars' bundled data) on a
    biome-modifier JSON targeting the `fluid_springs` `GenerationStep.Decoration` step -- the same step
    vanilla's own spring features use (confirmed via `javap` against `GenerationStep$Decoration`). The biome
    modifier's own registered type id and JSON shape needed real, per-target verification, not an assumption
    that NeoForge kept Forge's `forge:add_features` unchanged: decompiled source confirms 26.x's
    `net.neoforged.neoforge.common.world.BiomeModifiers$AddFeaturesBiomeModifier` registers under
    `neoforge:add_features`, and the registry itself (`NeoForgeRegistries.Keys.BIOME_MODIFIERS`) lives under
    the `neoforge` namespace -- so the JSON is `data/buildcraft/neoforge/biome_modifier/spring_water.json`.
    1.20.1 keeps Forge's own `net.minecraftforge.common.world.ForgeBiomeModifiers$AddFeaturesBiomeModifier`
    under `forge:add_features` (`ForgeRegistries.Keys.BIOME_MODIFIERS` confirmed keyed `forge:biome_modifier`
    via decompiled source), so `data/buildcraft/forge/biome_modifier/spring_water.json` -- matching this
    project's own existing `data/forge/tags/items` convention for that namespace.
  - **`EnumSpring.WATER.canGen`** is checked at the top of `SpringGenerator#place`, matching 1.12.2's own guard
    in its event handler -- it's a plain mutable field, not config-driven yet (see its own javadoc), so there's
    no declarative way to gate a `PlacementModifier` chain on it from JSON.
  - **A faithfully-preserved quirk, not a new decision: the original's "handle flat bedrock maps" special
    case.** On finding bedrock at the very first scanned layer (the world floor, always solid bedrock on every
    world type), 1.12.2 shifted the target position one layer *below* the floor, which its Y&gt;=0 chunk
    storage silently discarded -- a no-op, not a crash, for that specific roll. A modern level's chunk storage
    isn't safely assumed to tolerate the same out-of-range write (26.x in particular gives every dimension a
    real, configurable minimum Y rather than a hard-coded 0), so this port reproduces the same *outcome* (that
    attempt places nothing) as an explicit early return instead of reproducing the out-of-bounds write itself.
    `World#getHeight()` as the water-fill loop's upper bound is the trap PORTING.md's structural-changes list
    already documents as item 15 -- replaced with `Level#getMaxY()` (26.x) / `Level#getMaxBuildHeight()`
    (1.20.1), paired with `getMinY()`/`getMinBuildHeight()` in place of the original's implicit `y=0` floor.
  - Verified with forced rebuilds, the full 25-test suite, and real dedicated-server boots on both targets
    against a **freshly deleted world save** (not a stale one from an earlier session's testing) -- a bad
    feature codec, a malformed placement/biome-modifier JSON, or a missing registration would surface as a
    datapack/registry error exactly here, at world load and spawn-chunk generation, not at compile time. Both
    booted clean with no exceptions and genuinely regenerated region files (26.x: 5 `.mca` files in ~2s;
    1.20.1: 10 `.mca` files in ~12s, logging real "Preparing spawn area: N%" progress throughout) -- not
    verified beyond that clean-boot-plus-generation ceiling: nothing in this sandboxed environment could
    confirm a spring block actually rolled a successful 1-in-40 placement and is sitting in one of those
    regenerated chunks (interactive server-console commands aren't reliably reachable here either, matching
    this file's other "not verified" notes on in-game interaction).

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
- `buildcraft.core.statements` (19 files) -- checked directly once `buildcraft.lib.statement`
  landed, since that was the blocker PORTING.md's `buildcraft.core` survey originally flagged
  for it. It turns out `lib.statement` was necessary but not sufficient: an import audit of
  all 19 files found most also need one or more of -- the old `IFluidHandler`/`IItemHandler`
  capability-based transfer API (`TriggerFluidContainer`, `TriggerFluidContainerLevel`,
  `TriggerInventory`, `TriggerInventoryLevel`, `CoreTriggerProvider`), gone entirely on 26.x
  and needing the same kind of per-file `ResourceHandler` redesign `lib.tile.item` and
  `FluidUtilBC` already went through, not a rename; the blocked `CapUtil` chain (same files);
  `buildcraft.core.{BCCoreSprites,BCCoreStatements}`, themselves unported (every concrete
  trigger/action's `getSprite()` reads a constant off `BCCoreSprites`, and each self-registers
  into `BCCoreStatements` at construction); `buildcraft.lib.engine.TileEngineBase_BC8`
  (`TriggerEnginePowerStage`), unported; and, for `StatementParameterDirection` specifically,
  direct `TextureAtlasSprite`/`TextureMap` rendering. `lib.statement` itself is done and
  verified (see its own Progress entry) -- this package just turned out to need several more
  things besides.
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

- **`buildcraft.factory` — started: `BlockChute`/`TileChute` (both platforms), the first file this port has
  touched in this module, and the first machine anywhere in the port with a real, working item inventory other
  blocks can insert into and extract from.** Landing it closes a loop several sessions old:
  `buildcraft.lib.tile.item.ItemHandlerManager#getHandlerForFace(Direction)` had no caller until now, and its own
  class javadoc named "a tile's `RegisterCapabilitiesEvent` lookup function" as exactly the thing that would
  eventually call it -- `BCFactoryRegistries`' new `registerCapabilities` listener is that caller. Verified with
  forced rebuilds, the full test suite, real dedicated-server boots on both targets, **and real, observed
  gameplay via RCON on both**: a dropped item summoned above a placed chute was pulled in and pushed into a
  neighbouring hopper -- confirmed by reading the hopper's `Items` NBT afterward (`minecraft:diamond` on 26.x,
  `minecraft:netherite_ingot` on 1.20.1) and confirming the dropped item entity was gone and never reappeared
  anywhere nearby. `data get block` on the placed chute also confirmed `ItemHandlerManager`/`ItemHandlerSimple`'s
  NBT round-trip is correct on both targets (`inv: {stacks: [...]}` on 26.x's `ValueInput`/`ValueOutput` shape,
  `inv_manager: {inv: {items: [...]}}` on 1.20.1's `CompoundTag` shape), and that the battery-driven progress
  timer ticks and resets correctly in real time.
  - **New capability: `buildcraft.api.inventory.ItemTransactorCapabilities`, one per target, declaring only the
    BuildCraft-native `IItemTransactor` half of 1.12.2's `CapUtil.CAP_ITEM_TRANSACTOR`/`CAP_ITEMS` pair.** The
    vanilla-interop half does not need declaring on either target: checked via `javap` against the real
    NeoForge universal jar, 26.x already ships `Capabilities.Item.BLOCK`
    (`BlockCapability<ResourceHandler<ItemResource>, Direction>`) and `Capabilities.Item.ENTITY`/
    `ENTITY_AUTOMATION` (the entity-capability equivalents) as its own built-in tokens -- confirmed by reading
    `CapabilityHooks` in the NeoForge sources jar, which registers `Capabilities.Item.BLOCK` for every vanilla
    container block entity (chests, hoppers, furnaces, ...) and `Capabilities.Item.ENTITY_AUTOMATION` for
    minecart-type entities already. `ItemHandlerManager#getHandlerForFace` already returns exactly that
    `ResourceHandler<ItemResource>` shape (confirmed by its own return type), so 26.x never needed an
    `IItemHandler`-shaped bridge at all -- the port's earlier engine-subsystem finding that NeoForge dropped
    `IItemHandler` in favour of `ResourceHandler<T>`/`Transaction` turned out to apply to items exactly as it did
    to energy, with NeoForge itself supplying the "vanilla items" half of the pair this time, not just BuildCraft's
    own MJ-to-RF bridge. On 1.20.1 the vanilla-interop half is `ForgeCapabilities.ITEM_HANDLER`, already used by
    that target's `ItemHandlerManager`. `ItemTransactorCapabilities` (1.20.1) still needs an
    `event.register(IItemTransactor.class)` call wired into `BCFactoryRegistries.register`, matching
    `MjCapabilities`' own precedent for why 1.20.1 needs an explicit declaration where 26.x does not
    (`@CapabilityInject` is gone).
  - **`ItemTransactorHelper` (both platforms) is a trimmed port**: only `getTransactor`/`move`, all
    `TileChute` calls. `getInjectable`/`wrapInjectable`/`insertAllBypass` are dropped -- they exist only for
    `buildcraft.api.transport.IInjectable`/`PipeApi`, both belonging to the entirely unported `transport` module,
    the same "not needed for this task's real caller" scope note `ItemMarkerConnector` already established for
    its own deferred sub-feature. `createDroppingTransactor` is dropped for the same reason (no caller anywhere
    in this port). On 26.x, 1.12.2's single `getTransactor(ICapabilityProvider, EnumFacing)` had to split into two
    overloads -- `getTransactor(Level, BlockPos, Direction)` for a neighbouring block entity (the same
    `level.getCapability(BlockCapability, BlockPos, Direction)` idiom `TileEngineBase#getReceiverToPower` already
    established) and `getTransactor(Entity, Direction)` for a neighbouring entity (`Entity#getCapability
    (EntityCapability, Direction)`, confirmed via `javap`) -- because there is no common `ICapabilityProvider`
    supertype left for both to share. 1.20.1 keeps the original single signature unchanged, since both
    `BlockEntity` and `Entity` still extend `CapabilityProvider` there (confirmed via `javap`). `move`'s
    `boolean simulate` parameter is rebuilt on nested `Transaction`s on 26.x, the same "peek in a throwaway
    transaction, then commit only what really moved" trick `AbstractInvItemTransactor` already uses for its own
    all-or-nothing insert; 1.20.1's copy keeps 1.12.2's shape unchanged, `boolean simulate` included.
  - **`TileChute`'s `hasInventoryAtPosition` and `BlockChute`'s per-side `CONNECTED_MAP` blockstate are dropped
    outright, not ported.** 1.12.2's `CONNECTED_MAP` was a purely cosmetic "this face visually touches an
    inventory" indicator synthesised in `getActualState`, which no longer exists at all (see the structural-
    changes list) -- and, unlike `TileMarkerVolume`'s `ACTIVE` property (which mattered for connection *logic*),
    nothing reads `CONNECTED_MAP` for gameplay purposes and there is no renderer to show the visual distinction
    either way, so it is dropped rather than reproduced as an always-pushed real blockstate. `hasInventoryAtPosition`
    was `CONNECTED_MAP`'s only caller and goes with it.
  - **`onBlockActivated`'s GUI is dropped -- the first genuinely new kind of GUI deferral in this port.** Nothing
    ported so far has a GUI/container framework at all (`buildcraft.lib.gui` is entirely unported), so every
    earlier GUI-adjacent deferral (`ItemMarkerConnector`'s volume-box editor, `TileEngineCreative`'s wrench-cycle
    feature) never actually needed one either. A chute is the first block that would have. Right-clicking one is
    simply a no-op for now -- no override at all, rather than a `InteractionResult.PASS` stand-in that implies a
    GUI is coming back soon.
  - **No custom `VoxelShape`, despite the real 1.12.2 model being a genuine stepped funnel, not a cube.** The
    model (`buildcraft_resources/assets/buildcraftfactory/models/block/chute.json`, seven stacked boxes) is
    ported faithfully as a real block model. But nothing in this pass needs collision fidelity to match: item
    pickup scans an `AABB` sitting *above* the block (`BoundingBoxUtil.extrudeFace`), never the block's own
    volume, so a full-cube collision/light-occlusion shape costs nothing functionally, and a faithful rotated
    composite shape across all six facings would be real extra engineering for a purely cosmetic gap with no
    Java renderer to show it off either way -- the same call `BlockEngineWood` already made for its own non-cube
    1.12.2 render type. `BlockBehaviour.Properties#noOcclusion()`, set where the block is registered, keeps the
    one non-cosmetic half of 1.12.2's `isOpaqueCube() -> false` (light does not treat the block as a full
    occluder) without needing the shape override.
  - Registered in a new `BCFactoryRegistries` (mirroring `BCCoreRegistries`'s structure exactly) on both
    platforms, wired into each platform's `BuildCraft.java` mod constructor alongside the existing
    `BCRegistries.register(modBus)` call (a new top-level registration entry point, since `buildcraft.factory`
    has never needed one before). Textures/models/blockstate/loot table/lang/recipe pulled or written from
    `buildcraft_resources/assets/buildcraftfactory/`, following `BlockPowerConsumerTester`/`BlockEngineWood`'s
    established pattern -- same `buildcraft:chute` single-mod-id convention every other block in this port
    already uses, even though the source assets still live under the old per-module `buildcraftfactory`
    resource tree.

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
| `buildcraft.factory` | 49 | Chute (done). Pump, mining well, tank, autoworkbench. |
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
5. **Networking**, and this is the one place besides capabilities where the two targets need
   genuinely different designs, not a renamed one -- confirmed via `javap`: 1.20.1 has no
   `CustomPacketPayload` at all (that class is a 1.20.2+ vanilla addition), so it keeps
   1.12.2's own `SimpleChannel`/`NetworkRegistry.ChannelBuilder`, registering each message
   against a numeric id the same way 1.12.2 did, just once and statically rather than
   dynamically at FML postInit. 26.x replaces the whole stack with `CustomPacketPayload` +
   `StreamCodec`, registered through `RegisterPayloadHandlersEvent`'s `PayloadRegistrar`.

   Both targets drop `MessageManager` itself rather than port it: 1.12.2's dynamic per-mod
   message-class registry (which assigned each message an id lazily, resolved at FML
   postInit) has no equivalent left to build now that both platforms want every message
   statically registered up front, individually -- there is no dispatch table to build, so
   `BCNetwork` (this port's `MessageManager` equivalent, alongside `BCRegistries`) is a thin
   per-message registration list instead, one line added per message as it lands.
   `IMessageHandler`'s return-a-reply contract is also gone: both `IPayloadContext` (26.x) and
   `NetworkEvent.Context` (1.20.1) already expose `reply(...)` and `enqueueWork(...)` directly
   on the object a handler receives, so `IPayloadReceiver` (BuildCraft's own generic "does this
   tile want this payload" interface) has nothing left to hand back either.

   `buildcraft.lib.net.MessageUpdateTile` -- the one truly generic message, routing an opaque
   payload to whatever `IPayloadReceiver` block entity sits at a given position -- is the first
   ported message on both targets, verified with a real dev-server boot on each (no crash, no
   registration error) rather than just a compile check, since networking bugs are exactly the
   kind that pass `javac` and fail at runtime. `FriendlyByteBuf` already has `readBlockPos`/
   `writeBlockPos` built in on both targets, so `MessageUtil`'s equivalent helpers (still
   blocked; see its own entry) were never needed for this file.
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
16. **World generation's `Feature`/`ConfiguredFeature`/`PlacedFeature` three-tier system collapsed to two
    tiers on 26.x, between 1.20.1 and 26.x specifically -- not a 1.12.2-era change at all.** `ConfiguredFeature`
    does not exist as a class any more on 26.x (confirmed via `javap`: "class not found"); `Feature` became a
    plain interface, implemented directly by a record carrying its own configuration as fields plus its own
    `codec()` (vanilla's own `SpringFeature` is exactly this shape). `Registries.FEATURE_TYPE` and
    `Registries.FEATURE` effectively swapped what they hold between the two targets: on 1.20.1,
    `Registries.FEATURE` is the static registry (holding the bare `Feature<FC>` type) and
    `Registries.CONFIGURED_FEATURE` is the datapack registry (type+config pair); on 26.x,
    `Registries.FEATURE_TYPE` is the static registry (holding just the codec) and `Registries.FEATURE` is now
    the datapack one (holding the fully-configured instance). `PlacedFeature` is unchanged in role on both.
    See `core.gen.SpringGenerator`'s progress entry (and its own class javadoc, on each platform) for the full
    worked example, including the real registration code and datapack JSON shape on each target.

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
| Block-entity genuine-removal hook | `Block#onRemove` is gone (confirmed via `javap` against `BlockBehaviour`); `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)` fires directly on the tile, only for a genuine block change, never a chunk unload | `BlockBehaviour#onRemove(state, level, pos, newState, movedByPiston)`, unchanged in shape from 1.12.2 |
| Wearable/armor items | `ArmorItem` doesn't exist (confirmed via `javap`); a helmet is a plain `Item` carrying a `DataComponents.EQUIPPABLE` (`Equippable`) component, which has no defense field at all -- defense needs a separate, opt-in `ItemAttributeModifiers` component that `ItemGoggles` simply never adds | `ArmorItem`/`ArmorMaterial` still exist; `ArmorMaterial` is a plain interface (`getDefenseForType`/`getDurabilityForType`/...) `ArmorItem`'s constructor reads to build its `Attributes.ARMOR` modifiers -- Forge's old `ISpecialArmor` side interface is gone (confirmed via `javap`: class not found), so a zero-defense item implements `ArmorMaterial` itself with every value zeroed instead of overriding a side hook |
| World-gen `Feature` shape | interface, implemented directly by a record carrying its own config + `codec()`; `ConfiguredFeature` class is gone (confirmed via `javap`: class not found). `Registries.FEATURE_TYPE` (static) holds the codec, `Registries.FEATURE` (datapack) holds the configured instance | classic `Feature<FC>` abstract class + separate `ConfiguredFeature<FC, Feature<FC>>` datapack wrapper. `Registries.FEATURE` (static) holds the bare `Feature<FC>`, `Registries.CONFIGURED_FEATURE` (datapack) holds the type+config pair |
| Biome-modifier registry/type namespace | `net.neoforged.neoforge.common.world.BiomeModifiers`; registry key `neoforge:biome_modifier`; stock "add features" type id `neoforge:add_features` | `net.minecraftforge.common.world.ForgeBiomeModifiers`; registry key `forge:biome_modifier`; stock "add features" type id `forge:add_features` |
| Vanilla-interop item capability | `Capabilities.Item.BLOCK` (block entity) / `Capabilities.Item.ENTITY_AUTOMATION` (entity) -- both `ResourceHandler<ItemResource>`-shaped, NeoForge's own tokens, auto-registered for every vanilla container and minecart-type entity (confirmed via `CapabilityHooks` in the NeoForge sources jar) | `ForgeCapabilities.ITEM_HANDLER` -- one token, `IItemHandler`-shaped, works identically for a `BlockEntity` or an `Entity` since both still implement `ICapabilityProvider` |
| Neighbour capability lookup | `Level#getCapability(BlockCapability<T,C>, BlockPos, C)` for a block position; `Entity#getCapability(EntityCapability<T,C>, C)` for an entity -- two different call shapes, no common supertype | `provider.getCapability(Capability<T>, Direction)` (returns `LazyOptional<T>`) -- one shape, works on both `BlockEntity` and `Entity` |

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
- **On 1.20.1, a client-bound message handler cannot read a client-only type (e.g.
  `Minecraft.getInstance().player`, declared type `LocalPlayer`) inline in its own method body**, even if
  that method only ever runs client-side at runtime. Registering the message (`SimpleChannel.registerMessage`
  via a `MessageClass::handle` method reference) has to happen on both sides, which loads and
  bytecode-verifies the whole class regardless of which side actually calls the method -- and verifying an
  implicit widening assignment from a client-only type needs the verifier to resolve that type's hierarchy,
  which Forge's runtime dist-cleaner refuses on a dedicated server, crashing mod construction with a
  `BootstrapMethodError` ("invalid dist DEDICATED_SERVER"). Isolate the touch into its own class file (nested
  is fine) that only loads when actually invoked. Found porting `MessageMarker` -- see the
  `buildcraft.lib.marker` progress entry above for the full account. 26.x has no equivalent restriction (its
  merged/joined jar and `IPayloadContext#player()`, whose static type is already the common `Player`, never
  `LocalPlayer`, sidestep this entirely).

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
