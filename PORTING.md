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

- **`buildcraft.factory` — `BlockMiningWell`/`TileMiner`/`TileMiningWell` and the cosmetic `BlockTube` shaft it
  digs through (both platforms), plus a trimmed `buildcraft.lib.misc.BlockUtil` and `InventoryUtil#addToBestAcceptor`
  built specifically to support them.** The mining well digs straight down from its own position, one block at a
  time, powered by MJ, laying `tube` blocks behind it and inserting whatever it digs up into the best nearby
  inventory. Verified with forced rebuilds, the full test suite, real dedicated-server boots on both targets, and
  a real, observed, full dig-and-deposit cycle via RCON on **both**: a well placed above a solid stone column with
  its battery hand-filled via `data merge block` dug six full blocks down in real time, laying six `tube` blocks
  behind it, and deposited six `minecraft:cobblestone` into a neighbouring hopper (confirmed by reading the
  hopper's `Items` NBT afterward) -- and, on both targets, breaking the mining well itself afterward
  (`setblock ... minecraft:air destroy`) correctly turned every one of those six `tube` blocks back to air,
  confirming the shaft-retraction hook fires (and reaches the right tile) on each platform's own real removal
  path. Real-time RCON testing against an idle dedicated server (no player connected) is workable but has a hard
  practical limit worth recording for next time: the world ticks close to real-time for a few seconds after any
  RCON command lands, then the server goes fully idle and `time query gametime` freezes solid -- `/tick query`
  still reports "running normally" throughout, so it is not a freeze the game itself reports; the fix that worked
  here was re-sending `data merge block ... {battery:...}` in a loop from the test script rather than trying to
  wait out a single long sleep.
  - **`BlockUtil` is a ~550-line-to-4-method trim, same discipline as `ItemTransactorHelper`'s own trim**:
    `computeBlockBreakPower`, `isUnbreakableBlock`, `getFluidWithFlowing` and `breakBlockAndGetDrops` are the only
    four `TileMiner`/`TileMiningWell` actually call; nothing else came along for the ride.
    `breakBlockAndGetDrops` loses its `GameProfile owner`/`boolean grabAll` parameters and the entire
    `FakePlayer`/`BreakEvent`/`getFakePlayerWithTool` apparatus 1.12.2 built around them: confirmed by decompiling
    the real `Level#destroyBlock`/`Block#getDrops` out of the merged jar (via Vineflower, extracted to scratch
    space, never into the repo), modern `Block.getDrops(state, serverLevel, pos, blockEntity, breakingEntity,
    tool)` computes a block's loot straight from a `LootParams` built out of the position and tool -- no player
    object, fake or otherwise, required at all -- so `breakBlockAndGetDrops` is now just "compute drops, then
    `level.destroyBlock(pos, false)`" (`false` so vanilla does not *also* spawn the drops as item entities; the
    caller already has the `List<ItemStack>` and inserts it directly via `addToBestAcceptor`). The one real
    behavioural loss: no `BreakEvent` is posted any more, so a protection mod that only listens for that event
    cannot see or cancel a mining well digging through a claim -- accepted for this pass rather than building
    mod-compat event plumbing nothing in this port needs yet. `getFluidWithFlowing` returns a real
    `net.minecraft.world.level.material.FluidState` instead of reconstructing 1.12.2's Forge `Fluid`: a position's
    fluid (source or flowing, either one) is just `BlockState#getFluidState()` now, no `IFluidBlock`/
    `BlockFluidBase` abstraction to rebuild; the viscosity number `TileMiningWell#canBreak` gates flowing-lava on
    is `fluidState.getType().getFluidType().getViscosity(fluidState, level, pos)`, confirmed via `javap` on both
    targets (`net.neoforged.neoforge.fluids.FluidType`/`net.minecraftforge.fluids.FluidType`, both carrying a
    position-aware `getViscosity` overload). `isUnbreakableBlock` drops its owner parameter along with the
    player-relative-hardness detour it existed for, collapsing to `state.getDestroySpeed(level, pos) < 0` --
    1.12.2's own bottom-line check for bedrock and friends, reached directly instead of through a fake player.
  - **`InventoryUtil#addToBestAcceptor` lands, correcting that class's own stale "not ported yet" javadoc note**
    (it named the missing `ItemTransactorHelper`/`CapUtil` this session's chute pass already built). Only does
    the `IItemHandler`-equivalent half of 1.12.2's version -- the `IInjectable` (pipe) half is not ported, since
    `buildcraft.transport` itself is not ported at all yet, the same gap `ItemTransactorHelper`'s own javadoc
    already noted for its dropped `getInjectable`/`wrapInjectable`.
  - **`TileMiner`/`TileMiningWell` drop every render-only field from 1.12.2's `TileMiner`** (`currentLength`,
    `lastLength`, `getLength`, `getPercentFilledForRender`, `hasFastRenderer`, both render-distance overrides,
    and the whole `world.isRemote` client-interpolation branch that opened `update()`) -- there is no renderer in
    this port yet to consume any of them, the same "no renderer to serve it" call already made for
    `TileEngineBase`'s dropped progress animation. `wantedLength` survives as a plain server-side field, since
    `updateLength()` still needs it to notice when the dig target moved. `IdAllocator`/`TileBC_Neptune.IDS`/
    `NET_LED_STATUS`/`NET_WANTED_Y` and the `onLoad` random stagger they needed are dropped with the id-tagged
    payload system, matching `TileEngineWood`'s own precedent. `migrateOldNBT` is not ported -- no old saves to
    migrate from.
  - **A real, verified bug fix, not just a straight port: `TilesAPI.HAS_WORK`'s capability instance now reads
    `!isComplete()` (the method) instead of reproducing 1.12.2's `() -> !isComplete` (the *field*).** On the
    server, 1.12.2's `isComplete` field was only ever written by a client-only network handler
    (`readPayload`) -- nothing server-side ever assigned it, so it stayed permanently `false`, and the capability
    lambda was therefore permanently `true` regardless of whether the miner had actually finished digging. Almost
    certainly an artifact of the field/method name collision with `isComplete()` (the method), which computed the
    real, server-authoritative answer (`currentPos == null`) but was never what the capability actually read.
    Dropping the now-pointless client-mirror field and wiring the capability to the method instead is a
    correction, not a divergence -- worth a human double-check given the reasoning is inferred from reading the
    code rather than from an upstream changelog admitting the bug.
  - **`IWorldEventListener`/`WorldEventListenerAdapter` (`TileMiningWell`'s `worldEventListener`, 1.12.2's
    "wake up instantly on any block change anywhere" optimization on top of its periodic `SafeTimeTracker` poll)
    has no cheap modern replacement and is dropped outright, not reproduced.** Confirmed via `javap`: `Level`
    carries no `addListener`/`EventListener` method of any kind on either target any more, and neither
    NeoForge/Forge nor vanilla exposes a global "any block changed" bus event to hook once in its place (only
    per-position hooks like `neighborChanged`, which would need registering on every block type in the game to
    reproduce the old behaviour). The periodic `SafeTimeTracker(256)` poll already there as the real fallback
    either way covers the same ground, just with up to ~12.8 more seconds of latency noticing a block manually
    placed or removed in the miner's own dig column -- not a functional regression.
  - **`BlockTube#removedByPlayer` doesn't exist on either target any more at all** -- confirmed via `javap`
    against both `Block` and `BlockBehaviour` on both the 26.x and 1.20.1 merged jars: zero matches, the hook is
    gone outright, not renamed. 1.12.2 layered two mechanisms: `setBlockUnbreakable()` (hardness -1, blocking
    survival breaking) and `removedByPlayer` additionally refusing creative-mode insta-mine specifically while a
    `TileMiner` still stood above the shaft (insta-mine bypasses hardness). With the conditional half's hook
    gone, this port collapses to the unconditional half alone (`strength(-1.0F, ...)`, matching
    `BlockSpringWater`'s "always unbreakable" precedent) -- accepted because `TileMiner`'s own shaft-retraction
    logic (next bullet) already guarantees a tube block is never left orphaned (with no `TileMiner` above it) in
    normal play, the only case the dropped conditional half ever actually mattered for. `tube` also carries no
    `BlockItem` at all -- the first block in this port registered that way, via a new `BCRegistry#addBlock`
    (additive alongside `addBlockAndItem`) -- and an empty loot table via `Properties#noLootTable()` rather than
    a JSON file, since a player is never meant to obtain it directly.
  - **1.12.2's `TileMiner#onRemove()`/`BlockBCTile_Neptune#breakBlock` (the shaft-retraction hook) reaches two
    genuinely different modern hooks per platform, not the same one with a different name.** Confirmed by
    decompiling the real `LevelChunk#setBlockState` out of each merged jar (Vineflower, scratch space only): on
    26.x, `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)` is a hook that did not exist at all on 1.20.1
    -- it fires directly on the tile, while it is still valid, and is called *before* the block entity is removed
    from the level, which is exactly where 1.12.2's `TileBC_Neptune#onRemove()` used to fire from. On 1.20.1,
    there is no such `BlockEntity` hook; `Block#onRemove(state, level, pos, newState, movedByPiston)` still
    exists (confirmed unchanged via `javap`) and still runs while the old block entity is still valid, so
    `BlockMiningWell#onRemove` calls the tile's cleanup method directly instead -- the same `Block`-drives-
    `BlockEntity` shape 1.12.2's own `BlockBCTile_Neptune#breakBlock`/`TileBC_Neptune#onRemove()` pair used, just
    now needed only on the one platform that lost the tile-level hook. This divergence was already flagged in
    the "Things that differ between our two targets" table below the previous chute session added it for a
    different reason -- confirmed still accurate and now has a second, independent real caller.
  - No `owner` field on `TileMiningWell`, unlike `TileChute`/`TileEngineWood`: 1.12.2's `getOwner()` fed a
    `GameProfile` into `breakBlockAndGetDrops` purely to build a `FakePlayer`, and this port's version needs no
    such thing (see above) -- there is no advancement to unlock and no fake player to attribute the break to, so
    there is nothing left for an owner field to feed. A deliberate divergence from the pattern the task briefing
    suggested, made after confirming the technical need it existed for is gone, not a shortcut.
  - Registered in the existing `BCFactoryRegistries` (additive, mirroring `CHUTE`'s registration exactly for
    `mining_well`; `tube` has no item and no ticker). Textures/models/blockstate/loot table/lang/recipe pulled
    from `buildcraft_resources/assets/buildcraftfactory/`. One real asset finding worth flagging: the shipped
    1.12.2 `tube.json` block model is a degenerate zero-size element (`"from": [8,8,8], "to": [8,8,8]`) textured
    with plain `minecraft:blocks/stone` -- i.e. the tube block was never actually visible in 1.12.2 either
    (underground, in a hole its own removal destroys almost immediately, so nobody noticed). Ported faithfully
    rather than "fixed" with the unused `mining_well/tube.png`/`pump/tube.png` art assets sitting in
    `buildcraft_resources` but referenced by no model anywhere in the shipped resource pack.

- **`buildcraft.factory`'s fluid foundation lands, and `TilePump`/`BlockPump` is the third factory machine.**
  This batch is `buildcraft.lib.fluid.Tank` (both platforms), `buildcraft.lib.misc.FluidUtilBC#pushFluidAround`
  (both platforms, the two methods that class's own javadoc had flagged as blocked pending exactly this), and
  `TilePump`/`BlockPump` built on top of them. No new capability class was needed for either platform -- see
  below, a real correction to the assumption this task started from.
  - **The fluid capability turned out to need no BuildCraft-native token at all, unlike the item side.**
    1.12.2's `CapUtil.CAP_FLUIDS` was `IFluidHandlerAdv extends IFluidHandler` -- a single capability that was
    *already* vanilla-interop-shaped, unlike `IItemTransactor` (which needed `ItemTransactorCapabilities` plus a
    separate vanilla-interop token because it does *not* implement `IItemHandler`). Confirmed via `javap` against
    the real universal jars: 26.x already ships `net.neoforged.neoforge.capabilities.Capabilities$Fluid.BLOCK`,
    typed `BlockCapability<ResourceHandler<FluidResource>, Direction>` -- exactly `Tank`'s own shape, once `Tank`
    is itself a `ResourceHandler<FluidResource>` (see below) -- and 1.20.1 already ships
    `ForgeCapabilities.FLUID_HANDLER`, typed `Capability<IFluidHandler>` -- exactly what `Tank` already is by
    extending Forge's own `FluidTank`. Both were registered directly (`BCFactoryRegistries` on 26.x,
    `TilePump#getCapability` on 1.20.1) with no wrapper class in between; `FluidUtilBC#pushFluidAround`'s
    neighbour lookup queries them directly too, the same way `pushFluidAround` needed no `FluidTransactorHelper`
    the way `addToBestAcceptor` needed `ItemTransactorHelper`. `FluidUtilBC`'s own javadoc (both platforms) is
    corrected in place to say so, the same "stale note fixed once its blocker landed" precedent
    `InventoryUtil#addToBestAcceptor`'s own javadoc already set.
  - **26.x's `Tank` is a one-slot `FluidStacksResourceHandler`, not a from-scratch `ResourceHandler`
    implementation.** Confirmed via `javap`: `net.neoforged.neoforge.fluids.FluidTank` (the direct rename target
    1.12.2's own superclass would suggest) genuinely does not exist on this target -- "class not found", as this
    task's own briefing already expected. What *does* exist, and was not anticipated going in, is
    `net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler`: the fluid counterpart of
    `ItemHandlerSimple`'s own `ItemStacksResourceHandler` base, in the same `StacksResourceHandler<S, T extends
    Resource>` family, with the same transaction-safe `insert`/`extract` and `ValueIOSerializable` NBT
    persistence already built in. `Tank` (26.x) is therefore exactly as thin a wrapper over its base as
    `ItemHandlerSimple` is over its own -- `isValid`/`onContentsChanged` overrides for the filter/callback, plus
    `fillInternal` (new: `StacksResourceHandler#set(int, T, int)`, already public, is the direct modern
    equivalent of 1.12.2 `FluidTank#fillInternal`'s validator-bypassing write) -- not the ground-up
    transaction-aware handler the task briefing budgeted real design time for. Worth a human double-check purely
    because it means less new code than expected, not because the design is shaky.
  - **1.20.1's `Tank` keeps a real base-class relationship, just under a moved package.** Confirmed via `javap`
    against the Forge 1.20.1 universal jar (not the merged jar -- the merged jar doesn't carry it):
    `net.minecraftforge.fluids.capability.templates.FluidTank` is 1.12.2's own `net.minecraftforge.fluids.
    FluidTank` moved under `capability.templates`, with the same `fill`/`drain`/`isFluidValid`/`getCapacity`/
    `onContentsChanged` surface (`boolean doFill/doDrain` -> `FluidAction`, matching every other fill/drain
    rename already in this port's fluid code). `Tank` still implements `IFluidHandlerAdv` here (unlike 26.x,
    which drops it -- see above): 1.20.1's `IFluidHandler` has no transaction-scoped way to introspect a
    handler's slots from outside, so filtered draining still needs its own interface method, same as 1.12.2.
  - **The vanilla "infinite water source" rule is unchanged in shape, confirmed against the real modern
    mechanic, not assumed.** 1.12.2's own comment pointed at `BlockDynamicLiquid.updateTick`; the direct modern
    descendant is `FlowingFluid#getNewLiquid` (decompiled out of the 26.x merged jar, scratch space only): a
    flowing block becomes a new source once it has `neighbourSources >= 2` horizontally-adjacent source
    neighbours of the same fluid *and* the block directly below is either solid or another source of that fluid
    -- the identical two-neighbour threshold and shape 1.12.2's own check already used, just re-expressed against
    `FluidState#isSource()`/`BlockState#isSolid()` instead of Forge's old `Material#isSolid()`. Ported verbatim
    into `TilePump#buildQueue0` rather than guessed at.
  - **The oil-spring branch (`isOil`/`oilSpringPos`/`ADVANCEMENT_DRAIN_OIL`/`BCEnergyFluids.crudeOil`/
    `ITileOilSpring#onPumpOil`) is dropped entirely, not ported.** All five depend on `buildcraft.energy`, not
    ported at all in this port. Not a functional cut: 1.12.2's own `isOil` already opened with
    `if (BCModules.ENERGY.isLoaded()) { ... } return false;`, and since `buildcraft.energy` never registers here
    either, that condition is permanently `false` regardless of whether the branch exists in source -- dropping
    it outright reproduces the exact real-world behaviour this port is already in, matching the precedent already
    set for `BlockSpringWater`'s dropped oil half and the un-ported `ITileOilSpring` itself.
  - `fluidConnection` is not ported -- a dead field even in 1.12.2's own source (assigned in `buildQueue`, never
    read anywhere in the whole 1.12.2 tree).
  - The debug-profiler instrumentation (`Profiler debugProf`/`Stopwatch watch`/`ProfilerEntry`, gated behind
    `DEBUG_PUMP`, a system property nobody sets) is dropped, not reproduced -- zero behavioural effect, and it
    would have needed merging with a live per-tick world profiler this target has no simple handle on.
    `DEBUG_PUMP`'s real diagnostic value, the `BCLog.logger.info` calls explaining *why* a drain attempt failed,
    is kept in full.
  - `Fluid#isGaseous()` has no modern equivalent -- confirmed via `javap` against `FluidType` on both targets: no
    such method exists, getter or builder flag. The established Forge/NeoForge convention (a negative
    `FluidType#getDensity()` marks a gas) stands in for it; nothing this port registers is actually gaseous yet
    (only vanilla water/lava), so the branch it gates is currently dead in practice, kept faithfully anyway since
    the original explicitly special-cased it.
  - `BlockUtil`'s own 1.12.2 `getFluid`/`getFluidWithoutFlowing`/`drainBlock` (`TilePump`'s other fluid-block-
    inspection dependencies, beyond the already-ported `getFluidWithFlowing`) become `FluidUtilBC#getFluidSource`/
    `#drainBlock` instead, on both platforms -- relocated there rather than added to `BlockUtil`, which was out of
    scope for this pass. Real per-platform research, not a rename: NeoForge's own generic
    `net.neoforged.neoforge.transfer.fluid.FluidUtil#tryPickupFluid` was considered for 26.x and rejected after
    reading its source -- its own doc comment warns it can mutate the world (via `BucketPickup#pickupBlock`) even
    when the `Transaction` it was given is never committed, unsuitable for `mine()`'s simulate-then-commit two
    step. On 1.20.1, `net.minecraftforge.fluids.FluidUtil#getFluidHandler(Level, BlockPos, Direction)` was also
    considered and rejected after decompiling it: confirmed it only resolves a handler through a neighbouring
    `BlockEntity`, and a plain vanilla water/lava lake has none, unlike 1.12.2's own version of that method (which
    wrapped `IFluidBlock`/`BucketPickup` blocks directly). Both platforms' `drainBlock` instead go straight to
    `BucketPickup` (the same interface Forge's own `BucketPickupHandlerWrapper` builds on for this exact case,
    confirmed by reading its decompiled source): a `FluidState` read for the side-effect-free simulate case, and
    only the real case calls `pickupBlock`, which vanilla's `LiquidBlock` already refuses unless the state is a
    source (confirmed by reading `LiquidBlock#pickupBlock` on both targets) -- no redundant source check needed.
  - `createMjReceiver()` no longer exists as an overridable hook on `TileMiner` (dropped when that class landed,
    see its own javadoc) -- its `mjReceiver` field is fixed to a plain, non-redstone `MjBatteryReceiver`. Since a
    pump genuinely still wants `MjRedstoneBatteryReceiver` (unlike the mining well, which was already happy with
    the plain receiver) and `TileMiner` was out of scope to modify for this pass, `TilePump` adds a second
    receiver field, `mjRedstoneReceiver`, wrapping the same `battery` -- registered in its place
    (`BCFactoryRegistries` on 26.x, an intercepting `getCapability` override on 1.20.1); the inherited
    `mjReceiver` field is simply never registered for this tile. `IMjRedstoneReceiver` is presently a pure marker
    (nothing in this port reads it yet to actually allow cheap redstone-direct power), so this has no observable
    behavioural effect today -- it is there for whenever a future redstone-to-MJ feature wants to find pumps by
    it, matching what the field existed for in 1.12.2 too.
  - `getOwner().getId()` (used only to grant the "draining the world" advancement) follows the same owner-UUID-
    field pattern `TileChute`/`TileEngineWood` already established -- unlike `TileMiningWell` (which needed no
    owner at all, see that class's own javadoc), a pump's advancement grant is the one place this tile still
    cares who placed it.
  - `BlockPump` needed no facing property or GUI-opening hook to drop: unlike `BlockMiningWell` (which kept a
    facing property purely for parity), 1.12.2's real `BlockPump` had neither to begin with -- it extended
    `BlockBCTile_Neptune` directly, overriding only `createTileEntity`.
  - Registered in the existing `BCFactoryRegistries` (additive, same shape as `MINING_WELL`'s own registration).
    Textures/blockstate/block+item model/loot table/lang pulled from `buildcraft_resources/assets/
    buildcraftfactory/` following the established pattern; the LED-status textures (`led_green`/`led_red`) are
    not pulled in, matching every other render-only asset already skipped elsewhere in this port (no renderer to
    show them). The 1.12.2 recipe is **not** ported: its `t` key requires `buildcraftfactory:tank`, an entirely
    separate `factory` block/item this port hasn't reached yet (the "Tank" *block*, not this batch's
    `lib.fluid.Tank` *class* -- same name, different thing) -- inventing a substitute ingredient would misrepresent
    the real recipe, so it waits for that block the same way `BlockTube` waits for a reason to have a `BlockItem`.
  - **Verified with a real dedicated-server boot and RCON on both targets, not just a compile check -- a pump
    genuinely fills its tank from world water and correctly tells a finite pool apart from an infinite one.**
    Two scenarios, both observed directly via `/data get block` and `/execute store result score ... if block`
    (no `mcrcon`/`mcstatus` dependency -- a small stdlib `socket`+`struct` script speaks the RCON binary protocol
    directly): (1) a flat 5x5 source-block pond sitting on solid ground -- every position the pump drains from is
    the "two-or-more-same-fluid-neighbours-over-solid-ground" infinite-source pattern by construction, and
    indeed, after ten separate MJ-gated drain cycles across two full battery charges, both drained positions were
    still confirmed real `minecraft:water` blocks, never `air`; the tank correctly accumulated `5000`/`1000` mB
    (26.x/1.20.1) at exactly `10 * MjAPI.MJ` per drain, and the battery correctly hit `0` after exactly five
    charges' worth. (2) a single isolated water source block on a stone platform, with no same-fluid neighbours
    anywhere -- drained exactly once (`1000` mB into the tank, matching a single battery charge), and the source
    block genuinely became `air` afterward, confirmed the same way. Both scenarios reproduced on 26.x; scenario
    (1) also reproduced independently on 1.20.1 (tank correctly held `1000` mB after one charge, and the drained
    position was still confirmed water afterward) to confirm the shared algorithm and the platform-specific
    `Tank`/NBT shape both actually work there too. `pushFluidAround` itself was not observed moving fluid between
    two real in-world blocks -- there is no second fluid-accepting block anywhere in this port yet for it to push
    into (the same gap the task briefing itself flagged as a real possibility) -- so it is verified by code
    review and the already-proven `move` primitive it is built on, not by a live cross-block transfer. Both dev
    servers booted and shut down cleanly with zero exceptions in the log either time.

- **`buildcraft.factory` — `TileTank`/`BlockTank` (both platforms), the storage tank.** Stacked vertically with
  others of its own kind, a tank behaves as one combined multi-block fluid reservoir: filling the bottom tank
  spills upward once it is full, draining pulls from the top down for a liquid (bottom up for a gas), and a
  capability query landing on *any* tile in the column sees the whole column's combined contents. This is the
  first machine in this port whose defining behaviour genuinely spans multiple physical block entities at once,
  not just one tile talking to its immediate neighbours.
  - **The column walk itself ports unchanged in shape**: starting from `this`, walk upward one block at a time
    while the neighbour above is a `TileTank` and `canTanksConnect` agrees, then the same downward, returning the
    run bottom to top. It only ever looks straight up/down (a stack is 1-wide by definition), so it is not a
    flood-fill and needs no visited-set. Every fluid method (`fill`/`drain`/`insert`/`extract`/
    `balanceTankFluids`) calls this first, then spreads its work across the resulting list, reversing the list's
    iteration order for a gas versus a liquid (liquid settles toward the bottom -> fill packs bottom-first, drain
    empties top-first; gas rises -> both flip). **Renamed from 1.12.2's private `getTanks()` to
    `getConnectedTanks()` on both platforms** -- not optional on 1.20.1, where `IFluidHandler` itself declares an
    unrelated same-signature `int getTanks()` ("how many tank slots does this handler have") that this class also
    has to implement; Java does not allow two same-parameter-list methods differing only in return type. 26.x has
    no such collision but uses the same name for consistency between the two files.
  - **The multi-tank capability aggregation needed real, different per-platform designs, confirmed via `javap`
    against both merged jars -- not a mechanical trim of `TilePump`'s own single-tank capability.**
    - On 26.x, `TileTank` implements `net.neoforged.neoforge.transfer.ResourceHandler<FluidResource>` *directly*
      (confirmed via `javap`: `size()`, `getResource(int)`, `getAmountAsLong(int)`, `getCapacityAsLong(int, T)`,
      `isValid(int, T)`, `insert(int, T, int, TransactionContext)`, `extract(int, T, int, TransactionContext)`),
      as a single logical slot (`size() -> 1`) whose every method calls `getConnectedTanks()` and fans out across
      the column -- the direct modern equivalent of 1.12.2's own `TileTank implements IFluidHandlerAdv`. Each
      physical tile's own `tank` field (a plain one-slot `Tank`) still holds and serialises that block's own share
      of the fluid; the aggregate view is assembled fresh from every tile's `tank` on each call, never cached.
      Registered in `BCFactoryRegistries` exactly like `TilePump`'s own `Capabilities.Fluid.BLOCK` registration,
      just handing back `tile` itself (now a `ResourceHandler<FluidResource>`) instead of a `tank` field.
    - On 1.20.1, `IFluidTankProperties`/`FluidTankProperties` (what 1.12.2's `getTankProperties()` returned) do
      not exist on this target at all -- confirmed via `javap` against the Forge 1.20.1 universal jar, neither
      type is present. The modern `IFluidHandler` surface replaces that single properties array with
      `getTanks()`/`getFluidInTank(int)`/`getTankCapacity(int)`/`isFluidValid(int, FluidStack)` directly, so
      `TileTank` implements those four (plus `fill`/`drain`/`IFluidHandlerAdv#drain`) instead, still presenting
      the whole column as slot `0`. Exposed through the tile's own `getCapability`/`invalidateCaps` override for
      `ForgeCapabilities.FLUID_HANDLER`, the same per-instance pattern `TilePump`/`TileChute` already established,
      returning `this` rather than a wrapped field.
  - **The comparator hook's modern names, confirmed via `javap` against `BlockBehaviour` on both merged jars, with
    a real signature divergence between the two targets.** 26.x: `protected boolean hasAnalogOutputSignal
    (BlockState)` / `protected int getAnalogOutputSignal(BlockState, Level, BlockPos, Direction)` -- the extra
    trailing `Direction` parameter (unused here, matching how 1.12.2's own `getComparatorInputOverride` never used
    `world`/`pos` for anything beyond the tile lookup either) is genuinely not present on 1.20.1's copy of the
    same two hooks, which are `public boolean hasAnalogOutputSignal(BlockState)` / `public int
    getAnalogOutputSignal(BlockState, Level, BlockPos)`. `getComparatorLevel()`'s own math is unchanged 1.12.2
    logic, unmoved, still reading this tile's own physical `tank` (not the whole column) -- a stacked column's
    comparator output is per physical block, matching 1.12.2's own behaviour exactly.
  - **No ticker.** 1.12.2's `update()` had two jobs: tick the dropped `FluidSmoother` (no renderer to serve it,
    same reasoning as everywhere else in this port), and re-check the comparator level once a tick, calling
    `markDirty()` again if it had changed -- but `Tank`'s own `onContentsChanged` already calls
    `markChunkDirty()`/`markChunkDirty` on *every* content change regardless of whether the comparator level
    actually moved, so that tick-polled recheck was already redundant in 1.12.2 itself the moment any fill/drain
    had happened at all. Wiring `Tank`'s `onChange` callback straight to `markDirtyAndSync()` reproduces the one
    behaviour that callback ever actually caused, with no ticker and no `getTicker` override on `BlockTank` at
    all -- a deliberate simplification, not an oversight, documented here so a future reader does not assume a
    ticker was simply forgotten.
  - **`ITankBlockConnector` needed no port at all and is dropped, not just left unported.** It was a marker
    interface 1.12.2's `BlockTank` implemented purely so `getActualState`/`shouldSideBeRendered` (the cosmetic
    "block below is also a tank" face-culling blockstate) could check for it on a neighbour -- 1.12.2's own real
    fluid-column logic (`TileTank#getTanks()`) already used a direct `instanceof TileTank` check, never the
    marker. With `getActualState` gone entirely (no such hook exists any more -- see PORTING.md's structural-
    changes list) and `shouldSideBeRendered` dropped with it (no renderer), nothing is left to read the marker.
  - **Everything render/old-network/GUI-only is dropped**, matching every precedent already set by `TilePump`/
    `TileChute`: `FluidSmoother`/`smoothedTank`/`getFluidForRender`, the id-tagged network-cache payload system,
    and `onActivated`'s two behaviours (`FluidUtilBC.onTankActivated`, still not ported anywhere -- see that
    class's own javadoc -- and `BCFactoryGuis.TANK.openGUI`, no GUI/container framework exists yet). Right-
    clicking a tank is a no-op for now, matching `BlockChute`.
  - **A full-cube default block shape was chosen deliberately, not by default neglect**, over reproducing
    1.12.2's real non-cube bounding box (`2/16 .. 14/16` horizontally) and its matching `JOINED_BELOW`-aware
    model: nothing in this pass exercises collision or occlusion fidelity for a tank, and there is no renderer to
    show a faithful shape off either way -- the same call already made for `BlockPump`/`BlockEngineWood`'s own
    non-cube 1.12.2 render types. The block model uses the real tank textures (`tank/end.png`, `tank/side.png`
    from `buildcraft_resources`) on a plain cube parent; `tank/side_joined_below.png` is not pulled in, since
    `JOINED_BELOW` itself is dropped (see above). `ICustomPipeConnection`/`getExtension` (pipe-connection-shape
    hints) are dropped too -- `buildcraft.transport` is not ported at all, so there is no reader.
  - The 1.12.2 recipe (`buildcraftfactory:tank`, a hollow ring of `#blockGlassColorless`) is **not** ported: same
    "wait for a real ingredient rather than invent a substitute" reasoning `TilePump`'s own skipped recipe already
    used for its own missing `buildcraftfactory:tank` ingredient (a coincidence of naming -- that pump recipe note
    was about *this* block, now landed, but the glass-colour ore-tag ingredient this tank's own recipe needs is a
    separate, still-unaddressed gap).
  - Registered in the existing `BCFactoryRegistries` (additive, same shape as `PUMP`'s own registration, inserted
    directly after it and before the `TUBE` block so as not to disturb the javadoc comment already anchored to
    `TUBE`). Textures/blockstate/block+item model/loot table/lang pulled from `buildcraft_resources/assets/
    buildcraftfactory/` following the established pattern.
  - **Verified with forced rebuilds, the full 25-test suite, real dedicated-server boots on both targets with
    zero exceptions in either log, and a real, observed, multi-tank fluid-balancing column via RCON on both --
    not just single-tank fill/drain the way `TilePump`'s own verification was.** A `TilePump` was placed with a
    three-tall `TileTank` column directly above it, over a 3x3 water pond, with its battery hand-filled via
    `data merge block` (the same technique `TileMiningWell`/`TilePump` verification already established, looping
    short RCON round trips rather than one long sleep to keep the idle dedicated server's world actually
    ticking). On 26.x: after the battery fully drained, `data get block` on each of the three tank tiles showed
    `16000`/`16000`/`3000` mB of `minecraft:water` bottom to top -- the bottom two tanks completely full and the
    third correctly catching the overflow, entirely through the real `TilePump -> FluidUtilBC.pushFluidAround ->
    TileTank.insert()` capability path (the first time `pushFluidAround` has ever been observed moving fluid
    into a real second block in this port -- `TilePump`'s own verification pass had no fluid-accepting neighbour
    to test against yet and said so explicitly). The apparent shortfall against a naive battery/10,000,000 x
    1,000 mB estimate (35,000 mB delivered against a 500,000,000-microjoule battery that would suggest 50,000)
    is fully accounted for, not a bug: that battery value was more than double `TilePump`'s real 50,000,000-
    microjoule capacity, so `MjEffects.tick`'s `shedExcessPower()` (unit-tested, unchanged 1.12.2 arithmetic --
    see `MjBatteryTester`) was faithfully burning off the excess above 2x capacity every tick throughout the
    test, exactly as designed. A second run on 26.x and a fresh run on 1.20.1, both kept deliberately under that
    2x-capacity shed threshold, confirmed exact accounting instead: 1.20.1's bottom tank read `9000`/`16000` mB
    after 9 completed drain cycles (9,000 mB expected, 9,000 mB observed), and after a second battery top-up,
    `16000`/`2000`/`0` mB bottom to top after 18 total cycles (18,000 mB expected, 18,000 mB observed) -- the
    overflow into the second tank confirmed on this platform too, with zero loss once the shed mechanic was
    avoided. Draining (top-down for a liquid) was **not** independently observed live -- there is still no real
    in-game path to trigger `TileTank`'s own `extract`/`drain` at all in this build (`FluidUtilBC.onTankActivated`
    is still unported, so right-clicking a tank with a bucket does nothing, and nothing else in this port drains
    *from* a `TileTank`), so that half of the column logic is verified by code review and the same
    already-proven `getConnectedTanks()`/direction-reversal machinery the fill path just demonstrated live, not
    by an observed live drain -- worth a human double-check once a real fluid consumer exists to test it against.
    Both dev servers booted and shut down cleanly with zero exceptions in the log either time.

- **`buildcraft.factory` — `TileFloodGate`/`BlockFloodGate` (both platforms), the flood gate.** Given a
  fluid piped into its own single-slot `tank` (registered the same direct way `TilePump`'s own `tank` is, not
  `TileTank`'s aggregating-column pattern -- a flood gate is a single, non-stacking tile), it breadth-first
  searches outward through open space along up to 4 of its 5 non-top sides (`openSides`) and spreads that fluid
  into the world, one source block every 16 ticks, on a rebuild cadence that backs off exponentially
  (`{16, 32, 64, 128, 256}` ticks) whenever the search queue empties out with nothing left to place, and resets to
  the fastest delay the moment a placement actually succeeds.
  - **Fluid placement needs no `FakePlayer`, confirmed rather than assumed.** `TileFloodGate#canFill` only ever
    accepts two kinds of target -- plain air, or an existing *flowing* (non-source) block of the tank's own fluid
    -- and neither is ever a `LiquidBlockContainer` (a cauldron and friends), so the modern placement call is a
    plain, unconditional `Level#setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL)`
    with no player object of any kind. Independently confirmed moot either way: `BuildCraftAPI.fakePlayerProvider`
    is still never assigned anywhere in this port (`grep` across both platforms turns up only its declaration), so
    routing through it would have been a guaranteed `NullPointerException` regardless -- the same "no renderer/no
    consumer yet" situation as several other deferred fields in this port, just for a field that would crash on
    use rather than silently do nothing.
  - **A genuine, faithfully-preserved bug in 1.12.2's own `TileFloodGate#update()`, found while verifying this
    method line-by-line against the original, not introduced by porting it.** The path-revalidation loop iterates
    over every intermediate position `p` on the route back to the flood gate, but the actual fillability check
    inside that loop calls `canFillThrough(currentPos)` -- the loop variable `p` is read for nothing but the
    `p.equals(currentPos)` skip-self test, never passed to `canFillThrough` itself. The result is that 1.12.2's
    real binary never independently re-validates the intermediate steps of a path at all, only the destination,
    repeated once per path element. Ported byte-for-byte as written (bug included), per this port's established
    precedent for preserved-not-silently-fixed upstream quirks (`BlockMarkerVolume#neighborChanged`) -- flagged
    in both platforms' class javadoc and here for a human to weigh in on whether it is worth deviating from
    upstream to fix.
  - **`openSides` is a real, persisted `EnumSet<Direction>`, encoded as a plain bitmask `int`** (one bit per
    `Direction#ordinal()`), not 1.12.2's `NBTTagByteArray`/`NBTPrimitive` dual-format reader -- there is no old
    save data for a fresh port to stay backward-compatible with, so the "7.99.7 and before" legacy-array fallback
    is dropped outright, matching how `TileMiner`'s own `migrateOldNBT` was already dropped for the same reason.
    It needs no client sync of its own beyond the ordinary `markDirtyAndSync()` full-state push `toggleOpenSide`
    already triggers -- there is still no renderer in this port to consume a faster sync of this one field, the
    same reasoning that already dropped `TileMarkerVolume#showSignals`'s own id-tagged payload pair.
  - **The wrench interaction is real and was confirmed reachable, unlike a same-shaped precedent that turned out
    not to be.** `BlockEngineCreative`'s own javadoc already documented that `ItemWrench#useOn` intercepts a
    wrench click through `CustomRotationHelper.INSTANCE.attemptRotateBlock` *before* a block's own
    `useItemOn`/`use` ever runs, and speculated its own output-cycling wrench feature was consequently dead code
    since that block implements `ICustomRotationHandler`. `BlockFloodGate` does not implement
    `ICustomRotationHandler` and has no handler registered against it, so `attemptRotateBlock` falls through to
    its own `InteractionResult.PASS` default (confirmed by reading `CustomRotationHelper#attemptRotateBlock`
    directly, not assumed) -- and a `PASS` does not consume the interaction, so it genuinely reaches
    `BlockFloodGate#useItemOn`/`#use` on both targets.
  - **In-game verification, both platforms, via RCON.** `data merge block` on a placed flood gate's own NBT
    (`{tank:{stacks:[{id:"minecraft:water",amount:...}]}}` on 26.x -- `FluidStacksResourceHandler`'s real codec
    shape, `id`/`amount`, decompiled from the NeoForge sources jar rather than guessed after a first wrong guess
    silently produced a zero-length stack list and crashed the dedicated server with an `IndexOutOfBoundsException`
    in `StacksResourceHandler#getAmountAsLong` -- worth knowing for the next person who merges NBT into a
    `Tank`-backed tile on this target: a malformed `stacks` list decodes to *empty*, not *rejected*, and every
    `Tank` read assumes exactly one slot always exists; `{tank:{FluidName:"minecraft:water",Amount:...}}` on
    1.20.1, unchanged Forge `FluidTank` NBT shape) filled the tank directly. With a flood gate placed in the
    middle of five otherwise-sealed 1-block pockets (one per non-top side, each connected to the flood gate's own
    position only, no path around), and `openSides` set to exclude `WEST` via `data merge block ...
    {openSides:45}`, all four open pockets (`DOWN`/`NORTH`/`SOUTH`/`EAST`) filled with real placed water source
    blocks (`execute if block ... minecraft:water`, matching the pattern already established for
    `TileMiningWell`/`TilePump`/`TileTank`) while the closed `WEST` pocket never did, on both platforms, with the
    tank draining exactly 4,000 mB (four 1,000 mB placements) each time -- a clean, unambiguous confirmation that
    `openSides` genuinely gates the search. **Not independently observed live: an actual client wrench right-click
    flipping `openSides` end to end.** This environment has no graphical client to drive a real interaction
    through; what *is* verified live is everything `toggleOpenSide` actually does once reached (the NBT-level
    `openSides` gating above) and, via direct source reading rather than assumption, that the interaction pipeline
    genuinely reaches `BlockFloodGate#useItemOn`/`#use` for a wrench click on this block (see above) -- worth a
    human double-check with a real client before relying on this specific gesture.
  - **A real, dedicated-server-crashing robustness gap was found (and left as-is, not fixed) in shared
    `Tank`/`StacksResourceHandler` plumbing this pass does not own.** Feeding `data merge block` a `stacks` list
    that fails to decode (a wrong field name, in this case) does not reject the merge or fall back to the
    previous contents -- it silently produces a zero-*length* list instead of the expected always-one-slot list,
    and the very next `tank.getAmountAsInt(0)` (called unconditionally at the top of every `serverTick()`) throws
    `IndexOutOfBoundsException` and crashes the dedicated server. This is shared `net.neoforged.neoforge.transfer
    .StacksResourceHandler` behaviour, not something `TileFloodGate` does differently from `TilePump`/`TileTank`
    (both of which read tank slot 0 just as unconditionally, and neither is in scope for this pass to modify) --
    flagged here since it was only actually triggered by this task's own testing, not because it is specific to
    the flood gate. A real player can never produce this through ordinary play (nothing in-game ever hands a tank
    a malformed NBT blob), but it is worth a human's attention as a shared fragility the next `Tank`-owning tile's
    own verification pass should be aware can happen from a bad `/data merge`.
  - Registered in the existing `BCFactoryRegistries` (additive, inserted directly after `TANK`/`TANK_TYPE` and
    before the `TUBE` block, same care taken as `TANK`'s own insertion not to disturb `TUBE`'s anchored javadoc).
    A plain full cube (matching `PUMP`/`TANK`'s own "no renderer to justify a non-cube model yet" call) using the
    real `flood_gate/open.png`/`top.png` textures from `buildcraft_resources/assets/buildcraftfactory/` (the
    per-side `open`/`closed` texture swap and the `connected_*` blockstate properties that drove it are dropped
    along with `getActualState`, matching precedent -- see the class's own javadoc); blockstate/block+item
    model/loot table/lang written following the established pattern. The 1.12.2 recipe is not ported, same
    "no invented ingredient substitute" reasoning already applied to `TilePump`'s and `TileTank`'s own skipped
    recipes (this one needs `buildcraftfactory:tank`, itself unrecipe'd yet -- see `TileTank`'s own entry).
  - Verified with forced rebuilds, the full test suite, real dedicated-server boots with zero exceptions on both
    targets, and the live RCON fluid-placement/`openSides`-gating test described above on both platforms.
    **One environment-specific gotcha hit during this pass's own verification, unrelated to the port itself**:
    26.x's dedicated server pauses ticking entirely after `pause-when-empty-seconds` (default 60) with no players
    connected, which silently stalled the very first `openSides`-gating attempt (nothing placed for over a
    minute) until noticed in the server log and raised locally in `run/server/server.properties` for this test
    session -- worth remembering for whoever next needs a long-idle RCON-only verification run on 26.x; 1.20.1
    has no equivalent setting and was unaffected.

- **A real, player-facing bug was found and fixed after real jars were deployed to Prism Launcher and actually
  played: only `buildcraft.core`'s own blocks/items ever showed up in BuildCraft's creative tab, on both
  platforms.** `BCCoreRegistries#TAB_MAIN`'s `displayItems` read `REGISTRY.creativeTabEntries()` -- that class's
  own private `BCRegistry` instance -- but every module (`buildcraft.core`, `buildcraft.factory`, and every one
  still to come) constructs its **own separate** `BCRegistry`, each with its own private `creativeOrder` list.
  `buildcraft.factory`'s chute, mining well, pump, tank, and flood gate were all correctly registered (obtainable
  via `/give`, fully functional in-world) but simply invisible in the tab -- explaining a real player's "I don't
  seem to be able to do much in the creative menu" experience after trying the mod, even though five working
  machines existed by that point. Missed by every prior verification pass this port has run because all of them
  `/give` items directly over RCON and never open the creative-tab UI itself.
  - Fixed by giving `BCRegistry` a static list of every instance constructed (`ALL`) and a new
    `allCreativeTabEntries()` that concatenates all of them, in construction order; `TAB_MAIN` now calls that
    instead of its own module's `REGISTRY`. Safe specifically because `CreativeModeTab#displayItems` is only
    ever invoked lazily (when something actually needs the tab's contents), by which point every module's
    `register(modBus)` -- and therefore every module's `BCRegistry` construction -- has already run during mod
    construction; `BCCoreRegistries` does not need a compile-time reference to `BCFactoryRegistries` or any
    later module for this to work.
  - Verified with forced rebuilds on both platforms, the full 25-test suite, and real dedicated-server boots
    with zero exceptions on both targets (confirming the fix does not disturb ordinary registration/serverside
    behaviour). **Not independently confirmed with a live, on-screen creative-tab check**: this environment has
    no input automation to actually open a creative inventory screen and read what is drawn (the same
    limitation already noted for `BlockFloodGate`'s wrench gesture and for client-side rendering generally,
    below), and repeated attempts to trigger `displayItems` indirectly by watching `:neoforge-26x:runClient`'s
    own logs for evidence it had run were inconclusive -- the dev client's automatic world auto-join is not
    reliably reproducible run to run, so no log signal was ever confirmed either way. The fix itself is a small,
    mechanical list-concatenation change with a clear, verified-by-reading root cause, but a human should still
    open a real creative inventory once before trusting this fully.

- **`buildcraft.factory` — `TileAutoWorkbenchItems`/`BlockAutoWorkbenchItems` (both platforms), the auto
  workbench, and with it this port's first real GUI/container foundation.** Every machine ported so far needed
  zero player-facing UI (place it, pipe items/fluids through it, maybe wrench it) -- `BlockChute`'s own javadoc
  already flagged this as "the first block that *would* have wanted one." A player drags items into a phantom
  3x3 blueprint grid; the tile derives a matching material filter, pulls piped-in materials that exactly match
  (by item + data components, not by recipe ingredient/tag -- see below), and crafts one item at a time, charged
  by MJ (`POWER_GEN_PASSIVE = MjAPI.MJ / 5` per tick, `POWER_REQUIRED` = 10 seconds' worth, `POWER_LOST` = half
  that per tick once materials run out) exactly as 1.12.2's `TileAutoWorkbenchBase` did.
  - **New files, both platforms**: `buildcraft.lib.gui.ContainerBCTile` (tile-bound menu base),
    `buildcraft.lib.gui.slot.{IPhantomSlot,SlotBase,SlotPhantom,SlotOutput,SlotDisplay}`,
    `buildcraft.lib.tile.craft.WorkbenchCrafting`, `buildcraft.factory.tile.{TileAutoWorkbenchBase,
    TileAutoWorkbenchItems}`, `buildcraft.factory.block.BlockAutoWorkbenchItems`,
    `buildcraft.factory.container.ContainerAutoCraftItems`, `buildcraft.factory.gui.GuiAutoCraftItems`, and a
    genuinely new (no 1.12.2 counterpart) `buildcraft.factory.client.BCFactoryClientRegistries`. `BCRegistry`
    (both platforms) grew a new `addMenu(name, IContainerFactory<M>)` helper alongside its existing
    `addBlockAndItem`/`addBlockEntity`, so the next GUI-needing machine (quarry, filler, assembly table, ...)
    registers a `MenuType` the same one-line way.
  - **Deliberately dropped, matching this task's own scope, not discovered mid-port**: 1.12.2's
    `GuiRecipeBookPhantom`/`IRecipeShownListener` recipe-book integration (click a recipe in the book to
    auto-fill the blueprint) -- pure client UI convenience with no bearing on the tile's own logic; a player can
    still fill the blueprint by hand. The `ICON_FILTER_OVERLAY_SAME/DIFFERENT/SIMILAR` filter-overlay icons on
    the material slots -- a material slot's *behaviour* never depended on the overlay, only its look. Both are
    noted in each new class's own javadoc. `TileAutoWorkbenchFluids`/`BlockAutoWorkbenchFluids` are out of scope
    for this pass entirely (a separate follow-up once this foundation is proven) and were not touched.
    `buildcraft.lib.misc.CraftingUtil` (1.12.2's `GameRegistry.findRegistry(IRecipe.class)` scan) has no port at
    all -- both targets' own `RecipeManager#getRecipeFor` already does the lookup directly, collapsing the whole
    class into one call inside `WorkbenchCrafting#tick()`. The block's own crafting recipe (gear + crafting
    table, `gwg`) *is* ported, as a clean modern shaped-recipe JSON -- unlike `TilePump`/`TileTank`/
    `TileFloodGate`'s skipped recipes, both its ingredients (`buildcraft:gear_stone`, vanilla's crafting table)
    already exist in this port.
  - **Genuine platform divergence #1, confirmed via `javap` against both real merged jars: the recipe-matching
    API shape, not just a rename.** 1.12.2's `WorkbenchCrafting extends InventoryCrafting` was simultaneously
    the temporary crafting-grid storage *and* the object handed straight to `IRecipe#matches`/
    `#getCraftingResult`. On 1.20.1, `Recipe<C extends Container>` is still generic over a live container type,
    and `CraftingContainer` (extending `Container`) is exactly that same shape -- so the 1.20.1
    `WorkbenchCrafting` *does* implement `CraftingContainer` itself, backed by a small internal `ItemStack[]`
    standing in for 1.12.2's inherited storage, staying structurally close to the original. On 26.x,
    `Recipe<T extends RecipeInput>` is generic over a plain **immutable data holder**, not a live container --
    `CraftingInput`, built only through the static factory `CraftingInput.of(int width, int height,
    List<ItemStack> items)` (no mutable container interface exists to implement at all), with `Recipe#matches`/
    `#assemble` (no `RegistryAccess` parameter on `assemble` here, confirmed via `javap` — 1.20.1's takes one)
    both reading it directly. So the 26.x `WorkbenchCrafting` is a plain object that snapshots either the live
    blueprint (while matching) or its own temporary grid (while executing a craft) into a fresh `CraftingInput`
    each time one is needed. `RecipeManager#getRecipeFor` also returns one layer deeper on 26.x
    (`Optional<RecipeHolder<T>>`, the recipe itself `holder.value()`) than 1.20.1's plain `Optional<T>`.
  - **A real bug, found only by actually crafting through RCON, not by reading the API surface alone:
    `CraftingInput.of` silently trims its input to the bounding box of non-empty stacks.** Confirmed by reading
    this target's own decompiled source (`CraftingInput.ofPositioned`) after a real dedicated-server crash: a
    3x3 blueprint holding a 1x2 sticks pattern (planks in slot 0 and slot 3) produces a **trimmed 1x2**
    `CraftingInput`, not a 3x3 one -- matching for recipe *lookup* still works fine either way (`RecipeManager`
    handles a trimmed input correctly), but `CraftingRecipe#getRemainingItems(CraftingInput)` is sized to the
    *trimmed* grid, not `width * height`. The first version of `WorkbenchCrafting#craftExact` built a 3x3
    `CraftingInput` and then looped `remainingStacks.get(s)` for `s` in `0..9`, and crashed the live dedicated
    server outright with `ArrayIndexOutOfBoundsException: Index 2 out of bounds for length 2` the moment a real
    craft was attempted (`net.minecraft.ReportedException: Ticking block entity`, caught and fixed in this same
    pass -- see the RCON verification note below for the exact scenario that triggered it). Fixed by using
    `CraftingInput.ofPositioned(width, height, grid)` instead and mapping the trimmed grid's own
    `(tx, ty)` coordinates back to the original blueprint's `(positioned.left() + tx, positioned.top() + ty)`
    when returning a genuine remaining item (an empty bucket, say) to materials -- `matches()`/`assemble()`
    themselves needed no change, since both only ever read through `input.getItem(...)`, which is already
    trim-relative and safe. 1.20.1's `WorkbenchCrafting`, which never trims (its `CraftingContainer` is always
    reported as the tile's own fixed `width * height`, exactly like 1.12.2's `InventoryCrafting`), has no
    equivalent bug -- confirmed by the same RCON scenario succeeding there without incident, both before and
    after the 26.x fix. Worth remembering for any future 26.x code that builds a `CraftingInput` from a grid
    that can be smaller than the crafting recipe search area: `of`/`ofPositioned` trims, and anything reading
    `getRemainingItems`/similar per-slot recipe output has to size itself off the (possibly trimmed) input, not
    off its own caller-side grid dimensions.
  - **Genuine platform divergence #2, confirmed via `javap`: the GUI slot base class.** On 1.20.1, classic
    `IItemHandler`/`net.minecraftforge.items.SlotItemHandler` both still exist, and `ItemHandlerSimple` still
    implements `IItemHandlerModifiable` directly, so `SlotBase` keeps 1.12.2's shape almost unchanged --
    `extends SlotItemHandler`, wrapping the handler directly. On 26.x, neither `IItemHandler` nor
    `SlotItemHandler` exist at all (see `ItemHandlerSimple`'s own javadoc); NeoForge's replacement,
    `net.neoforged.neoforge.transfer.item.ResourceHandlerSlot`, wraps a `ResourceHandler<ItemResource>` through
    an `IndexModifier`, but since every inventory this GUI layer touches is concretely an `ItemHandlerSimple`
    (never just any `ResourceHandler`), the 26.x `SlotBase` instead extends NeoForge's own
    `net.neoforged.neoforge.world.inventory.StackCopySlot` directly and reads/writes the handler's own
    `getStackInSlot`/`setStackInSlot` pair through the abstract `getStackCopy`/`setStackCopy` pair -- one fewer
    layer of indirection than wiring up a `ResourceHandlerSlot` + `IndexModifier` would need, and consistent
    with this port's established preference for talking to its own concrete handler types directly (see
    `ItemHandlerSimple`'s own javadoc, and `TileTank` implementing `ResourceHandler` itself rather than being
    wrapped). `SlotDisplay` follows the same split: a hand-rolled `InventoryBasic`-backed classic `Slot` on
    1.20.1 (1.12.2's own shape, unchanged), `StackCopySlot` directly on 26.x (no fake backing `Container`
    needed at all there).
  - **A third, smaller divergence, also confirmed via `javap`, in the container-menu click/merge plumbing**:
    `AbstractContainerMenu#clicked(int, int, ?, Player)` takes 1.20.1's classic `ClickType` but 26.x's own
    `ContainerInput` enum instead -- a genuine, target-specific rename (re-verified via `javap`, not assumed
    from the task brief that flagged it), and on both targets `clicked` now returns `void` rather than 1.12.2's
    `ItemStack` (the cursor stack lives in `getCarried()`/`setCarried` instead). `ContainerBCTile#clicked`
    (both platforms) intercepts an `IPhantomSlot` click before delegating to `super.clicked`, setting/growing
    the phantom slot's content to match the carried stack without ever consuming it -- ported from
    `ContainerBC_Neptune#slotClick`'s same behaviour. Shift-click merging (`#quickMoveStack`) is a plain two-
    region merge (machine slots, then player inventory, or the reverse) using `moveItemStackTo` (the modern
    rename of `mergeItemStack`), which already respects `Slot#mayPlace` -- so phantom/output/display slots are
    automatically skipped without `ContainerBCTile` needing to filter them out itself.
  - **No custom network payload survives, and none was rebuilt.** 1.12.2's `writePayload`/`readPayload` pushed
    the MJ progress bar's value and the assumed-result display by hand, id-tagged. The progress value is now a
    single container `DataSlot` (`TileAutoWorkbenchBase#getPowerStoredForSync`/`#setPowerStoredForSync`, the
    same "block entity doubles as the `ContainerData` source" idiom vanilla's own furnace uses -- the server's
    `get()` always reads the live field, the client's `set()` writes into its own local tile instance when a
    change packet arrives). The assumed-result display slot needs nothing at all: `SlotDisplay#getItem()` reads
    `TileAutoWorkbenchBase#getCurrentRecipeOutput()` live, and `AbstractContainerMenu#broadcastChanges()`
    already diffs and pushes every slot's `getItem()` to the client every tick on its own. 1.12.2's
    `powerStoredLast`/partial-tick progress-bar interpolation has no replacement -- the progress bar just reads
    the last-synced `DataSlot` value directly; a minor, deliberate simplification given no `DeltaManager`-style
    interpolation is wired into the GUI layer yet.
  - **Opening the menu diverges in the expected, already-documented-elsewhere way.** `BlockAutoWorkbenchItems`
    always opens the GUI on right-click, with no wrench check at all -- matching 1.12.2's own
    `onBlockActivated`, unlike `BlockFloodGate`'s wrench-gated interaction. On 26.x this overrides
    `useWithoutItem` (confirmed `protected` via `javap`, the split-hook shape) and opens through
    `Player#openMenu(MenuProvider)`, with `TileAutoWorkbenchBase` itself implementing `MenuProvider` and
    overriding `IMenuProviderExtension#writeClientSideData` to write its own `BlockPos` by hand (read back by
    `ContainerAutoCraftItems`'s `RegistryFriendlyByteBuf` factory constructor to look the tile up again
    client-side). On 1.20.1 this overrides the still-unified `use` and opens through
    `NetworkHooks.openScreen(ServerPlayer, MenuProvider, BlockPos)`, which writes that same `BlockPos`
    automatically -- no `writeClientSideData` override needed there at all.
  - **Dist-isolation for client screen registration, mirroring but not identical to the `MessageMarker`
    precedent already on file.** `MessageMarker.ClientPlayerLookup` had to isolate one client-only field read
    into a lazily-loaded nested class because that message's registration itself had to run, and be verified,
    unconditionally on both sides. Menu-screen registration is different: it is *inherently* client-only, so the
    coarser, standard idiom applies instead -- gate the *listener registration call itself*, never the method
    body. `BuildCraft`'s constructor (both platforms) only calls
    `modBus.addListener(BCFactoryClientRegistries::registerScreens)` behind a dist check
    (`FMLEnvironment.getDist().isClient()` on 26.x -- confirmed via `javap` that `FMLEnvironment.getDist()` is a
    **method**, not the field the task brief's own starting guess assumed; `FMLEnvironment.dist.isClient()` on
    1.20.1, where it genuinely *is* a public field, confirmed separately via `javap` against that target's own
    `net.minecraftforge.fml.loading.FMLEnvironment`) -- so a dedicated server never has a reason to load or
    bytecode-verify `BCFactoryClientRegistries`, and transitively `GuiAutoCraftItems` (a client-only type on
    26.x, since `Screen`/`GuiGraphicsExtractor` don't exist on a dedicated server there), at all. The actual
    `MenuScreens.register`/`RegisterMenuScreensEvent#register` call and every import of the `Screen` subclass
    live only inside that gated class.
  - **A second, unrelated real bug was found and fixed in the block's own crafting recipe JSON, specific to
    26.3's data format.** The first version of `auto_workbench_item.json`'s shaped-recipe `key` used the classic
    `{"item": "minecraft:crafting_table"}` object form for the non-tag ingredient -- valid on 1.20.1 (and
    accepted there without incident) but rejected outright on 26.3, which crashed *dedicated server world
    creation itself* (`RegistryDataLoader` failing to parse `buildcraft:auto_workbench_item` out of the
    `minecraft:recipe` registry, `IllegalStateException`, before "Done" ever printed) because 26.3's ingredient
    codec no longer accepts that bare object form for a plain item reference -- only a plain string (an item id,
    or a `"#namespace:tag"` string) or its own `neoforge:ingredient_type` object shape. Fixed by using the bare
    string form (`"w": "minecraft:crafting_table"`) instead, matching the already-working `"g": "#c:gears/stone"`
    tag reference beside it. Found on the very first dedicated-server boot attempt after adding the recipe, not
    by inspection -- worth remembering for any future 26.x recipe JSON that still writes ingredients the classic
    `{"item": ...}` way.
  - Registered in `BCFactoryRegistries` (`auto_workbench_item`, same default block properties as every other
    `factory` machine, plus the new `AUTO_WORKBENCH_ITEMS_MENU` field) and `BCRegistry`'s new `addMenu` helper,
    inserted directly after `FLOOD_GATE`/`FLOOD_GATE_TYPE` and before `TUBE`, with the same care taken not to
    disturb `TUBE`'s own anchored javadoc that every prior insertion into this file has needed (flagged as a
    real, previously-hit bug class in this task's own brief). Textures/blockstate/block+item model/loot table/
    lang pulled or written following the established `BlockFloodGate`/`BlockPump` pattern -- `up`/`down` both
    use the original's single `top.png` (matching 1.12.2's own model, which never referenced its own bundled
    `bottom.png` at all -- dropped as a genuinely unreferenced asset, not merely unported), sides use `side.png`
    (`side_alt.png`, also unreferenced by the original model, dropped the same way).
  - **In-game verification, both platforms, via RCON, two independent scenarios each (different position, different
    recipe every time, not a replay of the same test).** `/data merge block` on a freshly placed auto-workbench's
    own NBT (`{blueprint:{stacks:[...]},materials:{stacks:[...]},powerStored:...}` on 26.x -- the
    `ValueIOSerializable`-driven `ItemHandlerSimple` shape, `stacks: [{count, id}, ...]`, empty slots encoding to
    a bare `{}`; `{inv_manager:{blueprint:{items:[...]},materials:{items:[...]}}}` on 1.20.1 -- classic
    `{id, Count}` item NBT nested one level deeper under `inv_manager`, both shapes read directly off a real
    `/data get block` rather than guessed) simulated a player having already configured the blueprint and piped
    in materials, with `powerStored` set directly to `POWER_REQUIRED` to skip the idle-server tick-throttling
    gap already documented in this project's own RCON-verification memory. First scenario (both platforms): a
    1x2 sticks pattern (2 oak planks) with 10 planks piped in -- after ticking, the result slot held 8 sticks
    (4 automatic crafts, 2 sticks each), materials correctly dropped from 10 to 6 planks, and
    `material_filter` balanced to all 9 slots holding oak planks (the only unique blueprint ingredient), exactly
    matching `createFilters()`'s expected single-ingredient case. Second, independent scenario at a different
    position (both platforms): a real wooden-pickaxe pattern (3 planks across the top row, a stick down the
    middle column, 6 planks + 4 sticks piped in) produced one real `minecraft:wooden_pickaxe` in the result slot,
    consumed exactly 3 planks + 2 sticks, and balanced `material_filter` 5 slots to planks / 4 to sticks --
    matching `createFilters()`'s own balancing-formula arithmetic worked out by hand from its ported algorithm
    (`64*1/3` vs `64*1/2` and so on), and `powerStored` correctly fell back to 0 afterward rather than
    accumulating for a next craft, since a pickaxe's own max stack size of 1 makes `canFullyAccept` correctly
    refuse a second craft into an already-occupied result slot. Zero exceptions in either server's log across
    both scenarios on both platforms; this is also the RCON scenario that surfaced both real bugs documented
    above (the `CraftingInput` trimming crash, first hit by this exact sticks scenario on 26.x; the recipe-JSON
    ingredient-format crash, hit on 26.x world creation before any RCON command could even run).
  - **Verified with forced `--no-build-cache clean` rebuilds on both platforms, the full 25-test suite, real
    dedicated-server boots with zero exceptions on both targets (before *and* after both bug fixes above), and a
    real `:neoforge-26x:runClient` boot reaching texture-atlas stitching -- well past mod construction and
    `RegisterMenuScreensEvent` registration -- with no `IllegalStateException`/`ClassNotFoundError`/
    `NoClassDefFoundError` naming `GuiAutoCraftItems`/`BCFactoryClientRegistries`/`ContainerAutoCraftItems`
    anywhere in the log, confirming the dist-isolation guard is correctly wired.** **Not independently verified:
    the actual on-screen GUI itself** -- this environment has no mouse/keyboard input automation, so the phantom-
    slot drag-and-drop interaction, the progress-bar fill, and the background texture's on-screen placement can
    only be confirmed by a human opening a real client and right-clicking a placed auto-workbench, the same
    caveat already on file for `BlockFloodGate`'s wrench gesture, the creative-tab fix, and client-side rendering
    generally. What *is* verified live, end to end, is every piece of logic the GUI would otherwise only be a
    thin skin over: blueprint matching, exact-stack requirement checking, material consumption, filter
    auto-derivation, MJ accumulation/spend, and result production -- all directly, via RCON, bypassing the GUI
    entirely.

- **`buildcraft.energy` — `TileEngineStone`/`BlockEngineStone` (both platforms), the Stirling Engine, and with
  it the first real fuel-burning machine ported (`buildcraft.core`'s `TileEngineWood`/`TileEngineCreative` are
  constant/redstone-driven engines, not fuel-burning ones). Reuses both foundations landed earlier this port
  without change: `buildcraft.lib.engine.TileEngineBase` (the `burn()`/`engineUpdate()`/`isBurning()`/
  `createConnector()`/`getMaxPower()`/`maxPowerReceived()`/`maxPowerExtracted()`/`explosionRange()`/
  `getCurrentOutput()` extension points were already there, unused, waiting for exactly this machine) and the
  auto-workbench batch's `ContainerBCTile`/`SlotBase` GUI foundation -- a single real fuel slot, one `DataSlot`
  for the flame-level indicator, no phantom slots at all, much smaller than `ContainerAutoCraftItems`.
  - **New files, both platforms**: `buildcraft.energy.tile.TileEngineStone`, `buildcraft.energy.block.
    BlockEngineStone`, `buildcraft.energy.container.ContainerEngineStone`, `buildcraft.energy.gui.
    GuiEngineStone`, `buildcraft.energy.client.BCEnergyClientRegistries`, and a new top-level
    `buildcraft.BCEnergyRegistries` (mirroring `BCFactoryRegistries` exactly -- its own private `BCRegistry`,
    `addBlockAndItem`/`addBlockEntity`/`addMenu`, a `registerCapabilities` on 26.x since `TileEngineStone`
    exposes both its fuel slot and its MJ connector as capabilities). `BuildCraft`'s constructor (both
    platforms) grew one additive line each for `BCEnergyRegistries.register(modBus)` and, behind the same
    dist-isolation guard `BCFactoryClientRegistries` already established, `BCEnergyClientRegistries::
    registerScreens`. The `getCurrentOutput()` PID-like smoothing (`esum`/`clamp`/`MAX_OUTPUT`/`MIN_OUTPUT`/
    `kp`/`ki`/`eLimit`) is a direct, unchanged port of the original's arithmetic on both platforms -- no
    redesign needed, exactly as the task brief for this batch already anticipated.
  - **The fuel slot's `isForceInserting` mechanic is a real, deliberate 1.12.2 quirk, ported faithfully, not
    incidental behaviour "fixed" along the way.** While a fuel item's leftover container item (e.g. the empty
    bucket a burning lava bucket leaves behind) is sitting in the fuel slot, the slot's insertion checker accepts
    *any* item, not just fuel -- `isForceInserting` is set immediately before the container item is force-written
    with `setStackInSlot` (bypassing the checker entirely) and cleared again only once the player empties the
    slot (`onSlotChange`). This narrow window where an unrelated item could technically also be piped in is
    exactly what 1.12.2 itself did; see `TileFloodGate`'s own progress entry above for this port's established
    precedent of preserving a genuine upstream oddity rather than "fixing" it unasked.
  - **`explosionRange()` returns `2` (the original constant) and is never called anywhere in the already-ported
    `TileEngineBase` on either platform -- confirmed, not assumed, by re-reading 1.12.2's own
    `TileEngineBase_BC8` (~line 469): the one call site (`worldObj.createExplosion(...)`) is commented out in
    the original source itself.** 1.12.2's own engines never actually explode on overheat despite the plumbing
    being there. No working explosion mechanic was added; the constant is returned faithfully and stays unused,
    matching upstream's own dead code exactly.
  - **Fuel burn-time lookup is a genuine, confirmed-via-decompile platform divergence on 26.x, and the task
    brief's own starting research needed two real corrections once actually verified.** 1.12.2's
    `TileEntityFurnace.getItemBurnTime(stack)` has no direct equivalent: fuel value is now the
    `net.minecraft.world.item.component.CookingFuel` data component (`burnTime()`/`speedMultiplier()`, each a
    `ResolvableInt`/`ResolvableFloat` needing a `LootContext`), reached the way vanilla's own
    `AbstractFurnaceBlockEntity#getBurnDuration` does, confirmed by decompiling that class from this target's
    own sources jar. Since `TileEngineStone` isn't a `Container`/`BaseContainerBlockEntity`, it can't inherit
    `BaseContainerBlockEntity#getLootContext` and builds its own `LootContext` in `getItemBurnTime`. **First
    correction**: `LootContextParamSets.CONTAINER_PROCESS` does *not* leave `BLOCK_STATE`/`ORIGIN` as "at least"
    required with the rest optional, as the task brief's own starting guess put it -- reading its actual
    registration shows all four of `BLOCK_STATE`, `BLOCK_ENTITY`, `CONTAINER` and `ORIGIN` are `.required(...)`,
    only `NeoForgeLootContextParams.QUERIED_STACK` is `.optional(...)`, and `LootParams.Builder#create`
    validates every required key eagerly (`ContextMap.Builder#buildAndValidate`) -- omitting one throws
    immediately rather than silently defaulting. `LootContextParams.CONTAINER` is typed `ContextKey<SlotProvider>`
    (a single `getSlot(int)` method), not `Container` as the name suggests, so `TileEngineStone` supplies a
    trivial no-op `slot -> null` stub -- nothing about a plain burn-time lookup ever queries it. **Second
    correction**: `ResolvableInt` (and `ResolvableFloat`) live under
    `net.minecraft.world.level.storage.loot.providers.number.ints` (`.floats` for the latter), not
    `net.minecraft.util.valueproviders` as the task brief's own starting guess had it -- confirmed by extracting
    and reading the real class from this target's sources jar. `ResolvableInt.getFromItem(stack,
    DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0)` is the actual one-line resolution call,
    correctly returning `0` for a stack with no `COOKING_FUEL` component at all.
  - **The container-item lookup (the empty bucket a burning lava bucket leaves behind) is also a genuine,
    confirmed divergence on 26.x.** 1.12.2's `fuel.getItem().getContainerItem(fuel)` becomes
    `ItemStack#getCraftingRemainder()` -- a NeoForge extension default method (`ItemInstanceExtension`, mixed
    into `ItemStack` via the `ItemInstance` interface) that returns a **nullable `ItemStackTemplate`**, not an
    `ItemStack` directly; `ItemStackTemplate#create()` produces the real stack. Confirmed by decompiling this
    target's own `AbstractFurnaceBlockEntity#consumeFuel`, which does exactly this for a burning lava bucket.
  - **A real defect was found and fixed on 1.20.1 during RCON verification, not by inspection.** The first
    version of `getItemBurnTime` called `stack.getBurnTime(null)` directly -- the `IForgeItemStack` default
    method mixed into `ItemStack` itself, which looks like the natural one-line equivalent and compiles fine.
    It is *not* the resolved lookup: it is the per-item override hook, returning `-1` as a sentinel meaning
    "this item doesn't override its own burn time, fall back to vanilla's `FurnaceBlockEntity.getFuel()` map" --
    confirmed by decompiling `net.minecraftforge.common.ForgeHooks#getBurnTime`, which is what actually resolves
    that sentinel (`int ret = stack.getBurnTime(recipeType); return ForgeEventFactory.getItemBurnTime(stack, ret
    == -1 ? VANILLA_BURNS.getOrDefault(...) : ret, recipeType)`) and fires the burn-time event. Calling
    `ItemStack#getBurnTime` directly made every plain vanilla fuel item, coal included, resolve to `-1` --
    `isValidFuel` never went true (`-1 > 0` is false), so a freshly-placed engine silently refused to accept any
    fuel at all. Caught on the very first RCON test against a real dedicated server: a coal item written into the
    fuel slot via `/data modify block ... set value` stayed at `burnTime: -1, totalBurnTime: -1` and the item
    count never dropped, where the 26.x server (tested moments earlier) had immediately shown the correct
    `totalBurnTime: 1600`. Fixed by calling `net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null)`
    instead, which already handles both the `-1` fallback and the empty-stack case (`0`) itself. Recompiled,
    rebooted, and re-verified with the identical RCON scenario afterward -- see below.
  - **The recipe is portable and ported, unlike `TilePump`/`TileTank`/`TileFloodGate`'s skipped ones** -- all
    four ingredients (cobblestone, glass, `buildcraft:gear_stone`, vanilla piston) already exist in this port or
    are vanilla. Pattern (`www` / `" g "` / `GpG`) and glass/piston handling directly mirror the already-shipped,
    near-identical `engine_wood.json` recipe (`www` / `" g "` / `GpG` too, just planks/gear_wood instead of
    cobblestone/gear_stone) -- `g`/`p` are plain item references (`minecraft:glass`, `minecraft:piston` on 26.x;
    `{"item": ...}` on 1.20.1, matching that same file), and `G` is `buildcraft:gear_stone` the same bare-string
    way `engine_wood.json` already references `buildcraft:gear_wood`. Cobblestone reuses the exact tag reference
    `gear_stone.json`'s own recipe already established for this port: `#c:cobblestones` on 26.x,
    `{"tag": "forge:cobblestone"}` on 1.20.1 -- note the genuine per-platform tag-id divergence (plural
    `c:cobblestones` vs singular `forge:cobblestone`), already on file in that recipe, not a new finding.
  - **Dropped from the original GUI, out of scope for this pass, matching the auto-workbench batch's own
    precedent for dropping pure polish.** 1.12.2's `GuiEngineStone_BC8` wired up the in-GUI help/tooltip
    framework (`LedgerEngine`, `DummyHelpElement`, two `ElementHelpInfo` fields) -- not ported anywhere in this
    port yet, on either platform, and out of scope here; noted in `GuiEngineStone`'s own javadoc. 1.12.2's own
    `deltaFuelLeft`/`DeltaInt`/`deltaManager` GUI-sync mechanism is dropped entirely (not partially ported) in
    favour of a single container `DataSlot` for the flame-level percentage (`TileEngineStone#
    getFuelPercentForSync`/`#setFuelPercentForSync`), exactly the `TileAutoWorkbenchBase#getPowerStoredForSync`
    idiom already established -- no partial-tick interpolation, the flame indicator just reads the last-synced
    value directly.
  - **Right-click always opens the GUI**, matching `BlockAutoWorkbenchItems`'s own precedent exactly (re-verified,
    not just trusted from that entry's own note): `useWithoutItem` on 26.x, the still-unified `use` opening
    through `NetworkHooks.openScreen` on 1.20.1.
  - **In-game verification, both platforms, via RCON, against a real dedicated server each, not a clean-boot-only
    check.** A Stirling Engine was placed with a chute (an already-ported MJ receiver) directly above it (its
    default `currentDirection` is `UP`) and a redstone block on an adjacent side; 20 coal was written into the
    fuel slot via a real NBT round trip read back off `/data get block` first, not guessed (`fuel.stacks[0]` on
    26.x -- the `ValueIOSerializable` shape, a bare list of `{id, count}`, empty slots encoding to `{}`;
    `inv_manager.fuel.items[0]` on 1.20.1 -- classic `{id, Count}` item NBT nested one level under `inv_manager`,
    matching `ItemHandlerManager#serializeNBT`'s own per-handler key). On both platforms, watched entirely
    through real elapsed time (no state was hand-set to "already burning" to skip the natural cycle): `burnTime`/
    `totalBurnTime` immediately resolved to `1600` (real coal, real vanilla burn time, confirming the platform-
    specific lookup on both targets independently), the coal stack count dropped from 20 to 19 the instant
    `burn()` ran, `esum`/`power`/`heat` climbed tick over tick exactly as the PID-like loop and heat-toward-
    `IDEAL_HEAT` formula predict, `progressPart`/`progress` engaged (the piston-pump state machine inherited from
    `TileEngineBase`), and — watched across the full cycle, not just its start — burnTime reached the end of the
    first coal's 1600-tick run and the engine auto-refuelled from the same slot with no external help, the stack
    count dropping to 18 with a fresh `burnTime: 1600`, confirming the "zero-gap refuel" behaviour inherited
    unchanged from `TileEngineBase#serverTick`'s call order (`engineUpdate()` before `burn()` each tick). The
    neighbouring chute's own MJ battery filled to its own capacity over the same window, confirming power
    actually left the engine and reached a real neighbour, not just accumulated locally. Zero exceptions in
    either server's log across the whole session, before and after the 1.20.1 `getBurnTime` fix above.
  - **Verified with forced `--no-build-cache clean` rebuilds on both platforms (before and after the 1.20.1 bug
    fix), the full 25-test suite, real dedicated-server boots with zero exceptions on both targets, and a real
    `:neoforge-26x:runClient` boot that reached full texture-atlas stitching and a loaded resource manager
    (`mod/buildcraft` included) with no `IllegalStateException`/`ClassNotFoundError`/`NoClassDefFoundError`/
    `BootstrapMethodError` naming `GuiEngineStone`/`ContainerEngineStone`/`BCEnergyClientRegistries` anywhere in
    the log.** **Not independently verified: the actual on-screen GUI itself** -- this environment has no mouse/
    keyboard input automation, so the fuel slot's click-to-insert interaction and the flame indicator's on-screen
    fill cannot be exercised live, the same caveat already on file for every prior GUI-touching entry in this
    file. What *is* verified live, end to end, is every piece of logic the GUI is a thin skin over: fuel
    consumption, burn-time countdown, heat/power-stage progression, the PID-like output smoothing, and real MJ
    delivery to a neighbour -- all directly, via RCON, bypassing the GUI entirely.

- **`buildcraft.core` -- `ItemPaintbrush`, the Paintbrush**, a right-click-on-block tool that recolours a
  vanilla dyeable block (wool, concrete, terracotta, stained glass, ...) to a chosen `DyeColor`, or -- held as
  the plain/colourless variant -- attempts to strip an existing colour back to plain. The hard part was already
  done by an earlier batch: `buildcraft.api.blocks.CustomPaintHelper`/`DyedBlockVariants` (both platforms)
  already implement the actual "find the colour-family a block belongs to and swap it" logic, including the
  `<colour>_<suffix>` registry-naming fallback that covers most vanilla dyeable blocks for free. This batch is
  only the item wrapping that call, tracking its own durability.
  - **New files, both platforms**: `buildcraft.core.item.ItemPaintbrush` -- one shared class, not 17, taking a
    `@Nullable DyeColor` constructor argument (`null` for the colourless variant). `BCCoreRegistries` grew one
    `PAINTBRUSH` field (the colourless variant) plus a `PAINTBRUSHES` field, an `EnumMap<DyeColor,
    DeferredItem<ItemPaintbrush>>`/`EnumMap<DyeColor, RegistryObject<ItemPaintbrush>>` populated by a real
    runtime loop over `DyeColor.values()`, registered as `paintbrush_<colour>` (e.g. `paintbrush_white`) using
    `DyeColor#getSerializedName()` -- the task's own suggested shape, and consistent with `DyedBlockVariants`'
    own `<colour>_<suffix>` naming convention. No new module holder needed; `buildcraft.core` already has
    `BCCoreRegistries`, unlike the auto-workbench/Stirling-engine batches which needed brand-new
    `BCFactoryRegistries`/`BCEnergyRegistries` holders. 17 sets of item-definition JSON (26.x only)/model JSON/
    texture PNG were added under `assets/buildcraft/{items,models/item,textures/item}/paintbrush*`, copied from
    `buildcraft_resources/assets/buildcraftcore/textures/items/paintbrush/` (the modern `DyeColor`-matching
    filenames -- `light_blue.png`/`light_gray.png` -- not the legacy `lightblue.png`/`silver.png` duplicates
    sitting alongside them from an old pre-1.12 dye-colour naming scheme), and one shared lang entry,
    `item.buildcraft.paintbrush = "Paintbrush"`, added to both platforms' `en_us.json`.
  - **17 metadata sub-items become 17 separate registry entries, following `BlockDecoration`'s own precedent for
    the identical "several looks, one 1.12.2 class" situation, just items instead of blocks.** 1.12.2's
    `ItemPaintbrush_BC8` packed `usesLeft`/`colour` into a metadata value plus a "damage" NBT byte, with
    hand-rolled `getDamage`/`setDamage`/`isDamaged`/`showDurabilityBar`/`getDurabilityForDisplay` overrides
    simulating a vanilla durability bar on top -- metadata was the only way 1.12.2 had to give "the same item,
    several looks". Confirmed via `javap` against both platforms' real jars that `ItemStack#isDamageableItem()`/
    `getDamageValue()`/`setDamageValue(int)`/`getMaxDamage()` are identical, stable, pre-data-component vanilla
    API on both targets (durability predates data components entirely; 1.20.1 has no
    `net.minecraft.core.component.DataComponentType` at all), so each of the 17 entries is a plain
    `Item.Properties().durability(64).stacksTo(1)` damageable tool needing none of 1.12.2's own hand-rolled
    overrides -- `stack.hurtAndBreak(...)` replaces `usesLeft--` directly.
  - **The colourless variant is a real, distinct tool, not a placeholder, ported faithfully guard and all.**
    1.12.2's own guard, `if (colour != null && usesLeft <= 0) return false;`, short-circuits false and is
    skipped entirely when `colour == null` -- the colourless brush is never blocked by its own uses-left count.
    Reproduced exactly in `ItemPaintbrush#useOn`. Durability is still spent identically for every variant,
    including the colourless one, on every successful paint -- matching a genuine 1.12.2 quirk (the counter was
    decremented even though the colourless guard above never consulted it) rather than "fixing" it, the same
    precedent `TileFloodGate`'s and the Stirling Engine's own entries already established for preserving a
    genuine upstream oddity unasked.
  - **The colourless brush's "clear paint" action is currently a no-op against every plain vanilla block, and
    this is honest, current platform behaviour inherited from an earlier batch, not a defect in this one.**
    `CustomPaintHelper.INSTANCE.attemptPaintBlock(..., null)` reaches a block-specific `ICustomPaintHandler` if
    one is registered for that block (none are, yet, for any vanilla block), but its generic
    `defaultAttemptPaint` fallback returns `FAIL` immediately for `paint == null` ("Clearing paint has no
    generic form: there is no way to know which colour is the 'plain' one") -- already-existing, already-ported
    behaviour this batch did not touch and is not responsible for changing.
  - **One real, deliberate behavioural difference from 1.12.2, a direct and unavoidable consequence of the
    17-separate-items redesign, not of any change to the painting logic itself.** 1.12.2's single
    shared-metadata item downgraded a spent coloured brush in place to the colourless variant (metadata 0)
    rather than destroying it, since metadata was just a rewritable value on the same `ItemStack`. With 17
    separate registry entries there is no equivalent in-place "downgrade" -- `hurtAndBreak` instead runs
    vanilla's own tool-break behaviour: a coloured brush that reaches its 64th successful use is consumed like
    any other vanilla tool (confirmed live via RCON below: `damage=0/0 consumed=true name=Air`), rather than
    turning into a fresh colourless one. This is the natural, expected consequence of the durability-based
    design this task's own brief specified, not something silently changed along the way.
  - **Dropped: `ParticleUtil.showChangeColour`.** `ParticleUtil` is not ported to either platform (client-side
    particle rendering is deferred generally, matching every other client-rendering caveat already on file in
    this document), and porting it just for this single call would be new client-rendering infrastructure out of
    scope for this batch. `SoundUtil.playChangeColour(Level, BlockPos, DyeColor)`, already ported identically on
    both targets from an earlier batch, is kept and called on every successful paint.
  - **Tooltip/display name uses `ColourUtil#getTextFullTooltip(DyeColor)`, not `#getTextFullTooltipSpecial`,
    which 1.12.2 used for the same purpose.** `getTextFullTooltipSpecial` emits a private escape sequence meant
    for `SpecialColourFontRenderer`, which is not ported on either platform (deferred with the rest of client
    rendering) -- using it here would have embedded unrenderable characters in the item's name for every colour
    except `BLACK`/`BLUE` (that method's own special-cased pair). `getTextFullTooltip` is the safe, already-
    ported equivalent that only ever emits standard `ChatFormatting` codes. Confirmed via `javap` that
    `Item#getDescriptionId()` is `final` on 26.x, so the shared base name (`item.buildcraft.paintbrush`, one lang
    entry for all 17 variants, per the task's own spec) is built directly in `ItemPaintbrush#getName(ItemStack)`
    rather than through a per-registry-entry description id override.
  - **The modern item-use-on-block hook, confirmed via `javap` against `Item` on both platforms' real jars, is
    identical in name and signature on both targets**: `InteractionResult useOn(UseOnContext)` -- the same hook
    `ItemWrench#useOn` already established a precedent for. `UseOnContext#getClickLocation()` already returns
    the absolute world-space hit position 1.12.2 built by hand (`VecUtil.add(new Vec3d(hitX, hitY, hitZ), pos)`),
    so no equivalent helper call is needed. The one real per-platform divergence inside the method body is
    `InteractionResult` itself (already on file as API-migration-reference item 14, re-verified fresh for this
    class): a success check is `instanceof InteractionResult.Success` on 26.x, `== InteractionResult.SUCCESS` on
    1.20.1. `ItemStack#hurtAndBreak` also has a narrower overload set on 1.20.1 -- confirmed via `javap` that only
    the generic `<T extends LivingEntity> hurtAndBreak(int, T, Consumer<T>)` form exists there (26.x additionally
    has a direct `hurtAndBreak(int, LivingEntity, InteractionHand)` convenience overload), so 1.20.1's callback
    calls `LivingEntity#broadcastBreakEvent(InteractionHand)` itself.
  - **In-game verification, both platforms, via a real dedicated server each, RCON, plus a temporary throwaway
    debug entry point -- and an honest account of what that does and does not prove.** There is no input
    automation in this environment to right-click as a real player, and a real dedicated server has no `Player`
    entity at all until a client actually connects (confirmed: `/give`, `/execute as @a`, and every other
    player-targeted command fail with "No player was found" against zero connected clients; the vanilla command
    surface on both targets was checked via `/help` and has no `/player`-style fake-interaction command on either
    release). Rather than settle for only re-confirming `CustomPaintHelper` (already known-good from an earlier
    batch), a temporary `RegisterCommandsEvent` debug command was added to each platform's `BuildCraft.java`
    (`net.neoforged.neoforge.common.util.FakePlayerFactory`/`net.minecraftforge.common.util.FakePlayerFactory`
    -- both present on the real loader jars, confirmed via `javap`/jar listing -- construct a genuine, if fake,
    `ServerPlayer`), which built a real `UseOnContext` around that fake player and called
    `stack.getItem().useOn(context)` directly -- i.e. it called `ItemPaintbrush#useOn` itself, the actual new
    code this batch added, not a re-test of already-known-good infrastructure underneath it. Both platforms were
    exercised identically and gave identical results: a red paintbrush on `white_wool` recoloured it to
    `red_wool` (`result=SUCCESS`/`Success[...]`, `damage=1/64`); the same brush against the now-red wool failed
    without spending a use (`result=FAIL`, `damage=0/64`, matching `CustomPaintHelper`'s own "already this
    colour" `FAIL`-not-`PASS` behaviour); the colourless brush against `blue_concrete` failed cleanly
    (`result=FAIL`), confirming the "no-op against plain vanilla blocks" note above live, not just by reading the
    code; a creative-mode fake player painted `blue_concrete` to `green_concrete` without spending durability
    (`damage=0/64` unchanged); and a brush pre-set to `damage=63` (its last use) painted successfully once more
    and was then genuinely consumed (`damage=0/0 consumed=true name=Air` -- `stack.getItem()` on an emptied stack
    resolves to `Items.AIR`), confirming the deliberate tool-break-at-max-damage behaviour documented above
    actually happens rather than throwing or leaving a broken-but-present stack. The same scenario set was also
    run against `white_terracotta` to confirm the recolour path isn't wool-specific. The temporary debug command
    and its `BuildCraft.java` hook were fully removed before this batch finished -- confirmed by `git status`
    showing no diff against either `BuildCraft.java` afterward -- and every verification step below (clean
    rebuild, tests, server boots, `runClient`) was re-run *after* that removal, against the final code, not
    against the code with the debug command still present. **Honest limitation, same as every prior
    RCON-verified batch in this file: the real right-click interaction itself, end to end through a real
    connected client's input, remains unverified** -- what is verified, directly, is that `ItemPaintbrush#useOn`
    itself (not a stand-in, not just the painting logic underneath it) dispatches correctly, decrements
    durability correctly, respects creative mode correctly, and breaks the tool correctly, on both platforms
    independently.
  - **Verified with forced `--no-build-cache clean` rebuilds on both platforms, the full 25-test suite, real
    dedicated-server boots with zero exceptions on both targets (both worlds deleted and rebooted fresh after
    the debug command's removal), and a real `:neoforge-26x:runClient` boot that reached full texture-atlas
    stitching (including `textures/atlas/items.png-atlas`) and a loaded resource manager (`mod/buildcraft`
    included) with no missing-model/missing-sprite warnings for any `paintbrush*` entry and no
    `IllegalStateException`/`ClassNotFoundError`/`NoClassDefFoundError`/`BootstrapMethodError` anywhere in the
    log.** Not independently verified: the on-screen appearance of the 17 item textures/models themselves --
    same rendering caveat as every other entry in this file -- and the real right-click interaction, per the
    paragraph above.

- **`buildcraft.transport` -- the first slice of pipes**, BuildCraft's signature feature and its own large
  subsystem (124 files in the original). This batch is deliberately scoped to a straight run of the simplest
  possible item pipe: a cobblestone pipe that moves an item from a source inventory into a destination
  inventory, with real server-side simulation and every other pipe feature (colours, wires, gates, pluggables,
  fluid/power flow, every non-cobblestone material) explicitly deferred. The entire pipe *API* was already
  ported by an earlier pass (confirmed by diffing the original `BuildCraftAPI/api/buildcraft/api/transport` file
  list against both platforms plus `modules/shared`); this batch is the first real *implementation* against it.
  - **New files, both platforms**: `buildcraft.transport.pipe.{PipeRegistry,Pipe,PipeEventBus,
    DefaultPipeConnection}`, `buildcraft.transport.pipe.behaviour.{PipeBehaviourSeparate,PipeBehaviourCobble}`,
    `buildcraft.transport.pipe.flow.{PipeFlowItems,TravellingItem}`, `buildcraft.transport.tile.
    {TilePipeHolder,SimplePipeWireManager}`, `buildcraft.transport.block.BlockPipeHolder`,
    `buildcraft.transport.item.ItemPipeHolder`, and a new top-level `buildcraft.BCTransportRegistries`
    (mirroring `BCFactoryRegistries`/`BCEnergyRegistries` exactly). `BuildCraft`'s constructor (both platforms)
    grew one additive line, `BCTransportRegistries.register(modBus)`.
  - **The one-block-many-items architecture is the real 1.12.2 design, reproduced deliberately, not a
    simplification invented for this batch.** Re-reading `common/buildcraft/transport/pipe/{PipeRegistry,
    Pipe}.java` directly (not assumed) confirms 1.12.2 already used a single shared block/tile pair
    (`BlockPipeHolder`/`TilePipeHolder`) for every pipe material, with a `PipeDefinition` (id, behaviour
    constructor, flow type) stamped onto the tile's `Pipe` object by whichever `ItemPipeHolder` instance placed
    it -- the same shape the paintbrush batch's 17-separate-items design was explicitly *not* an example of
    (that was for metadata-variant items with no shared runtime state; pipes are the opposite case). This batch
    needed `BlockPipeHolder`/`TilePipeHolder` written exactly once; a second pipe material in a future batch is
    only a second `PipeDefinition` + a second `ItemPipeHolder` instance registered in `BCTransportRegistries`,
    no new block/tile code at all -- confirmed live: `BCTransportRegistries#registerCapabilities` never
    references the specific `PIPE_COBBLESTONE` definition, only the shared block entity type.
  - **A real, load-bearing defect was found and fixed on both platforms during RCON verification, not by
    inspection: adjacent pipe segments failed to detect each other as pipes at all.** The first version of
    `BCTransportRegistries#registerCapabilities` (26.x) only registered `Capabilities.Item.BLOCK` for
    `TilePipeHolder`; it never registered `PipeApi.CAP_PIPE_HOLDER`/`CAP_PIPE`/`CAP_PLUG` themselves, the three
    tokens that expose the tile/pipe/pluggable objects *as objects* (as opposed to arbitrary capabilities the
    behaviour/flow choose to expose) -- 1.12.2's own `TilePipeHolder` constructor wires exactly these three via
    `caps.addCapabilityInstance(CAP_PIPE_HOLDER, this, ...)`/`addCapability(CAP_PIPE, this::getPipe, ...)`/
    `addCapability(CAP_PLUG, this::getPluggable, ...)`, which this batch had ported everywhere *except* that one
    constructor-time registration. Without it, `IPipeHolder#getNeighbourPipe` (which resolves
    `Level#getCapability(PipeApi.CAP_PIPE, pos, side)`) always returned null for a real neighbouring pipe tile,
    so `Pipe#updateConnections` could never tell "the block next to me is another pipe" from "the block next to
    me is a plain inventory" -- two adjacent cobblestone pipe segments connected to each other as
    `ConnectedType.TILE` (falling through to `DefaultPipeConnection`) instead of `ConnectedType.PIPE`, and
    `Pipe#canPipesConnect`/`canBehavioursConnect`/`canFlowsConnect` never ran between them at all. Caught live:
    the first RCON test placed two pipe segments next to each other and read back `con: 0` on the second segment
    (no connections recorded at all, since its first tick ran before the first segment had a chance to be
    detected either) and, after a partial fix attempt, `con` values that decoded to TILE-TILE instead of the
    expected PIPE-PIPE, plus a plain-air `con` bit that could only be explained by `getNeighbourPipe` resolving
    null. Fixed on both platforms: 26.x now additionally calls `event.registerBlockEntity(PipeApi.CAP_PIPE_HOLDER,
    ..., (tile, side) -> tile)` / `(..., PipeApi.CAP_PIPE, (tile, side) -> tile.getPipe())` / `(...,
    PipeApi.CAP_PLUG, (tile, side) -> tile.getPluggable(side))` in `BCTransportRegistries`; 1.20.1's
    `TilePipeHolder#getCapability` (which -- unlike 26.x -- already exposes its own capabilities directly, per
    `TileChute`'s own precedent on that target) now additionally checks for and answers those same three tokens
    before falling through to the pipe's own `getCapability`. Re-verified afterwards: `con` bitmasks on both
    platforms decoded correctly (`WEST=TILE` towards the hopper, `EAST=PIPE` towards the neighbouring segment, and
    the mirror image on the far segment), confirmed by hand-decoding the two-bits-per-face encoding documented in
    `Pipe#writeToNbt`'s own comment, not just by trusting that no exception was thrown.
  - **`Pipe.java` is a close, direct port of 1.12.2's own `Pipe.java`, with the one deliberate simplification the
    task brief itself anticipated**: no `writePayload`/`readPayload`/network constructor/`getModel()`/
    `PipeModelKey` -- nothing client-side observes a pipe's connection state or behaviour data yet, since this
    batch has no client rendering at all. Connection tracking (`connected`/`types` maps,
    `updateConnections()`, `canPipesConnect`/`canBehavioursConnect`/`canFlowsConnect`) is real, unabridged,
    server-side logic, ported unchanged in structure. `DefaultPipeConnection` (consulted when a neighbour block
    implements neither `ICustomPipeConnection` nor has a `PipeConnectionAPI` registration) is byte-identical on
    both platforms: confirmed via `javap` that `BlockBehaviour$BlockStateBase#getCollisionShape(BlockGetter,
    BlockPos)` (the modern replacement for 1.12.2's `IBlockState#getCollisionBoundingBox(World, BlockPos)`) is
    unchanged in shape and still returns a shape in the neighbour's own *local* block space, not world space --
    the same "no re-offsetting needed" property the 1.12.2 box had.
  - **`PipeEventBus` needed only its one Minecraft-only dependency removed, not a redesign**, confirmed by
    re-reading 1.12.2's own 193-line file directly: the reflection-based `@PipeEventHandler` dispatch
    (`MethodHandle`/`Modifier`/`Parameter`, all plain `java.lang.reflect`/`java.lang.invoke` API) is completely
    untouched by the port and copied over structurally unchanged. The only thing dropped is
    `BCDebugging.shouldDebugLog`-gated state-validation calls around `fireEvent` -- no debug-flag system exists
    anywhere else in this port either, and those checks were opt-in diagnostics, not behaviour. This file is
    byte-identical on both platforms (nothing in it touches a Minecraft type), the same "worth knowing before
    duplicating a platform class" category `VecUtil`/`RotationUtil`/`MjEffects` already established. Proven
    working live, not just by inspection: `PipeBehaviourCobble#modifySpeed`, a real `@PipeEventHandler` static
    method, fires on every item reaching a pipe's centre -- confirmed via RCON by watching queued
    `TravellingItem`s consistently carry `speed: 0.01d` (the method's own target), not the default `0.05`.
  - **`buildcraft.transport.pipe.flow.PipeFlowItems`/`TravellingItem` are the real logic this whole batch exists
    to prove works, ported closely from `common/buildcraft/transport/pipe/flow/{PipeFlowItems,TravellingItem}
    .java` (698 + 220 lines)**, with client-rendering members dropped (`clientItemLink`/`stackSize`/
    `interpolatePosition`/`getRenderPosition`/`getRenderDirection`/`isVisible`/`getAllItemsForRender`,
    `sendItemDataToClient`/the network constructor/`readPayload`/`writePayload`) and the delay-bucket item queue
    (1.12.2's `buildcraft.lib.misc.data.DelayedList<E>`) folded in as a small private reimplementation rather
    than ported under its own name, since nothing else in this batch's scope needs a generic delayed queue and
    it was not itself part of this batch's file list. `addTriggers` (registering
    `BCTransportStatements.TRIGGER_ITEMS_TRAVERSING`) is dropped outright -- gates/statements are out of scope
    and `BCTransportStatements` is not ported.
  - **This is the single largest genuine per-platform divergence in the whole batch, and it lives entirely
    inside `PipeFlowItems`/the capability-exposure it needs, confirmed by reading each target's own already-
    ported `IFlowItems`/`IInjectable` interface rather than assumed:** 1.12.2's `injectItem(ItemStack stack,
    boolean doAdd, ...)` took a whole stack and a simulate flag and handed back the leftover stack. **1.20.1
    keeps that shape completely unchanged** (`ItemStack`/`boolean doAdd`, no transaction type at all -- 1.20.1
    has no transfer API), so that platform's `PipeFlowItems`/`TilePipeHolder`/`Pipe` read almost as a literal
    transliteration of the original, and the vanilla-interop capability is the classic `IItemHandler` under
    `ForgeCapabilities.ITEM_HANDLER`, exposed via `TilePipeHolder`'s own `getCapability(Capability<T>, Direction)`
    override (matching `TileChute`'s established 1.20.1 precedent of exposing capabilities directly rather than
    through a `RegisterCapabilitiesEvent` listener). **26.x reshapes the same method entirely** around the
    transfer API: an `ItemResource` plus a plain `int` count and a `TransactionContext` this method must never
    commit itself -- the caller (typically a real vanilla hopper's own transaction, reached through
    `Capabilities.Item.BLOCK`) decides whether the effect sticks. That makes `PipeFlowItems`'s own mutation of
    its item queue genuinely transaction-unsafe unless handled: if a caller's transaction rolls back after
    `injectItem` returns a non-zero accepted count, the item must not silently stay queued while the source
    inventory also gets its stack back. `PipeFlowItems` (26.x) carries a small, self-contained
    `net.neoforged.neoforge.transfer.transaction.SnapshotJournal` over its own delay-bucket queue for exactly
    this -- the same mechanism NeoForge's own `ItemStacksResourceHandler` uses internally for its slot array,
    hand-rolled here because this flow's state is a moving queue, not a fixed array -- called via
    `journal.updateSnapshots(transaction)` immediately before every mutating entry point
    (`injectItem`, `tryExtractItems` when not simulating). The vanilla-interop capability on 26.x is
    `Capabilities.Item.BLOCK` (`BlockCapability<ResourceHandler<ItemResource>, Direction>`), wrapped by a small
    private `PipeItemResourceHandler` adapter inside `PipeFlowItems` itself (one virtual slot, `insert` forwards
    straight to `injectItem`, extraction unsupported) and registered per block entity type in
    `BCTransportRegistries#registerCapabilities`, following `BCFactoryRegistries#CHUTE`'s own precedent for
    exposing `ItemHandlerManager` the same way.
  - **Two of 1.12.2's own `IItemTransactor`/`ItemTransactorHelper` steps are dropped on both platforms, and this
    is a genuine, deliberate simplification of the *original's* own two-phase ejection logic, not a divergence
    invented by either platform's own porting pass.** 1.12.2's `onItemReachEnd`'s `TILE` branch tried
    `IInjectable` first (in case the "tile" neighbour was actually a foreign pipe mod's block exposing that
    interface directly) and only fell back to a plain `IItemTransactor` insert if that failed. Both platforms'
    `ItemTransactorHelper` already dropped `getInjectable`/`wrapInjectable` before this batch even started
    (their own javadoc says so explicitly: "belongs to the entirely unported transport module"), anticipating
    exactly this batch's own needs; since `updateConnections()`'s own logic never assigns `ConnectedType.TILE`
    to a neighbour that is itself a real `IPipe` (that always resolves to `ConnectedType.PIPE` instead, handled
    by a separate branch that calls `injectItem` directly), the `IInjectable`-first step could only ever have
    mattered for a hypothetical third-party pipe mod that isn't a BuildCraft `IPipe` -- none exists in this
    ecosystem, so both platforms eject into a `TILE`-connected neighbour with a single, direct
    `IItemTransactor#insert`/classic-`IItemHandler#insertItem` call.
  - **A genuine, deliberate scope choice, not a faithful copy of the original's own value, and confirmed rather
    than guessed: `canBeColoured` is `false` on the cobblestone `PipeDefinition`, even though the real 1.12.2
    cobblestone pipe is colourable.** Re-reading `common/buildcraft/transport/BCTransportPipes.java` directly
    shows `builder.builder.enableColouring()` is called once, right after the structure pipe is defined, and
    that flag then persists on the shared builder for every pipe defined afterwards -- wood, stone, cobblestone,
    quartz, gold, and so on. This batch disables it anyway: colouring a pipe means applying a dye, which needs
    interaction/GUI plumbing this batch does not add (dye colours on a pipe are explicitly out of scope), so
    leaving `canBeColoured` true would advertise `IPipe#setColour` as a working feature nothing in this batch
    can legitimately trigger. The id/texture-prefix naming (`"cobblestone"`, not 1.12.2's own
    `"cobblestone_item"`) likewise deliberately diverges: the `_item` suffix existed only to stay unique
    alongside sibling `cobblestone_fluid`/`cobblestone_power`/`cobblestone_rf` definitions this batch does not
    register.
  - **`IWireManager` is implemented for real, not stubbed, the same "implement it properly even though nothing
    yet exercises it live" standard the Stirling Engine's own `explosionRange()` entry established.**
    `buildcraft.transport.tile.SimplePipeWireManager` is new to the port (not a port of 1.12.2's own 
    `buildcraft.transport.wire.WireManager`, which additionally builds and rebuilds cross-pipe "wire system"
    graphs -- entirely out of scope, since no wire item, gate, or `IWireEmitter` exists anywhere in this port
    yet): a genuine `EnumMap<EnumWirePart, DyeColor>` backs `addPart`/`removePart`/`getColorOfPart`/
    `hasPartOfColor`, with real NBT persistence. `isPowered`/`isAnyPowered` are honestly `false` always --
    correct, not faked, since no `IWireEmitter` could ever legitimately power one -- and `updateBetweens` is a
    no-op for the same reason (no cross-pipe wire graph exists to update).
  - **`getRedstoneInput`/`setRedstoneOutput` (`IRedstoneStatementContainer`, part of `IPipeHolder`'s own
    `extends`) follow `TileEngineBase#isRedstonePowered`'s own already-ported precedent exactly, confirmed
    identical on both platforms via direct comparison of that class's own two copies**: `level.getSignal(pos
    .relative(side), side)` (the modern replacement for `world.getRedstonePower(pos.offset(side), side)`) for a
    specific side, `level.getBestNeighborSignal(pos)` for the "any side" case. `setRedstoneOutput` genuinely
    no-ops (`return false`) on both platforms -- no gate/statement system exists anywhere in this batch that
    could ever call it with something real to output.
  - **Everything pluggable-related is a stub, exactly as scoped, not an oversight.** `getPluggable` always
    returns `null` on both platforms; `PluggableHolder` is not ported; no `PipePluggable` type is registered
    anywhere. `canPlayerInteract`/`onPlayerOpen`/`onPlayerClose` are minimal (no GUI exists for this pipe in
    this batch): `canPlayerInteract` checks the tile is still validly placed and the caller is within reach,
    matching `Container.stillValidBlockEntity`-style precedent; the open/close hooks are plain no-ops.
    `scheduleRenderUpdate`/`scheduleNetworkUpdate`/`scheduleNetworkGuiUpdate`/`sendMessage`/`sendGuiMessage` are
    likewise plain no-ops -- there is no renderer and no client sync to schedule anything for.
  - **The recipe is skipped, not invented, matching `TilePump`/`TileTank`/`TileFloodGate`'s own established
    precedent for a genuinely non-portable source, not the "missing ingredient" case those three actually hit.**
    Checked directly: `buildcraft_resources/assets/buildcrafttransport/recipes/` holds no JSON recipe for any
    material pipe (only `_factories.json`/a handful of unrelated plug/sealant recipes), confirming the original
    generates every material pipe's recipe through a Java code-driven factory system rather than plain
    per-recipe JSON -- and `BCTransportRecipes` (the file that would contain that factory) is explicitly out of
    this batch's own scope list. Porting the recipe would mean porting that factory system first, which this
    batch does not do.
  - **Textures/models/blockstate/loot table reuse the exact real cobblestone-pipe texture, following
    `BlockTank`/`BlockPump`'s own "plain full-cube, no renderer yet" precedent exactly.**
    `buildcraft_resources/assets/buildcrafttransport/textures/pipes/cobblestone_item.png` (confirmed 16x16 via
    `file`, a plain flat texture, not a texture-atlas sheet needing UV cropping) is reused unchanged as both the
    block's `cube_all` texture and the item's icon on both platforms. Block registered via `REGISTRY.addBlock`
    (no auto `BlockItem`, since this block's item is the pipe-specific `ItemPipeHolder`, not a generic one --
    the same `addBlock`-without-`addBlockAndItem` shape `BlockTube` already established a precedent for, for a
    different reason). Loot table drops a plain `buildcraft:pipe_item_cobblestone` on survival, matching every
    other block in this port; the genuine `loot_table`-singular-vs-`loot_tables`-plural directory-name split
    between 26.x and 1.20.1 (already on file in PORTING.md's build-gotchas section) applies here too.
  - **In-game verification, both platforms, via RCON against a real dedicated server each -- a genuine,
    end-to-end, tick-by-tick simulation, not hand-set NBT state, and an honest account of the mechanism used.**
    The rig: a real vanilla hopper (facing sideways) fed by a real vanilla chest sitting directly above it, a
    straight run of two cobblestone pipe segments, and a second real vanilla chest as the destination -- no
    fluid/dropped-item trick, no debug insertion into the pipe's own flow. **What *does* need a temporary debug
    mechanism, and why**: a dedicated server started for RCON testing has no connected player at all (confirmed:
    every player-targeted command fails with "No player was found" against zero connected clients, the same
    finding already on file from the paintbrush batch's own verification entry), so there is no real click to
    place a pipe item with, and 1.12.2's own placement logic (`TilePipeHolder#onPlacedBy`) is only ever invoked
    through that click -- `/setblock buildcraft:pipe_holder` alone was confirmed, live, to *not* call it (the
    resulting tile's NBT had no `pipe` key at all: the block existed but held no `Pipe` object, so it could
    never connect to anything). A temporary `RegisterCommandsEvent` debug command was added to each platform's
    `BuildCraft.java` (`net.neoforged.neoforge.common.util.FakePlayerFactory`/`net.minecraftforge.common.util.
    FakePlayerFactory`, the same fake-player construction the paintbrush batch's own debug command already used)
    that sets the block directly and then calls `TilePipeHolder#onPlacedBy(fakePlayer, stack)` on it directly --
    the actual new placement logic this batch added, not a re-test of already-known-good `BlockItem`/
    `Item#useOn` scaffolding underneath it (the same "call the interesting method directly" precedent the
    paintbrush batch's own debug command established, applied here to the placement-logic equivalent of
    `useOn`). **What the seed step does *not* need a debug mechanism for, and this is the actual proof of this
    batch's own goal**: getting an item *into* the pipe network from a source inventory is not seeded by the
    debug command at all -- a real cobblestone stack was written into the source chest via `/data merge block`
    (verified round-trip first via `/data get block` before trusting the format on each platform: 26.x's modern
    lowercase `{id, count}` item shape vs. 1.20.1's classic `{Slot, id, Count}` shape, confirmed different by
    direct comparison, not assumed), and the real vanilla hopper -- driven entirely by vanilla game logic, with
    no BuildCraft code involved on the extraction side at all -- pulled from the chest and pushed into the
    pipe's `Capabilities.Item.BLOCK`/`ForgeCapabilities.ITEM_HANDLER` capability on its own, on its own normal
    8-tick cooldown, confirmed live by watching a pipe's own NBT accumulate ten separately-timed queued
    `TravellingItem`s (`tickStarted` values 8 ticks apart, each with `timeToDest: 75` matching the
    `side`-to-centre distance divided by `PipeBehaviourCobble`'s own `0.01` target speed) purely from repeated
    real hopper pushes, not a single seeded call. Watched entirely through real elapsed game ticks after that
    (`time query gametime` advancing between checks, never hand-set): the source chest's `Items` list went from
    5 cobblestone to empty, both pipe segments' `pipe.flow.items` lists (real internal queue state, not a
    display value) filled and then drained back to empty as the item(s) travelled centre-to-centre and
    end-to-end across the two segments, and the destination chest's `Items` list ended with exactly
    `{count: 5, id: "minecraft:cobblestone"}` (26.x) / `{Count: 5b, id: "minecraft:cobblestone"}` (1.20.1) --
    confirmed on both platforms independently, in separate RCON sessions against separate fresh worlds. Pipe
    connection bitmasks (`con`, the two-bits-per-face encoding `Pipe#writeToNbt` documents) were hand-decoded
    and cross-checked against the actual rig layout on both platforms, not just trusted to be non-zero -- see
    the connection-capability defect entry above, which this same verification pass is what caught it. The
    temporary debug command and its `BuildCraft.java` hook were fully removed before this batch finished on both
    platforms -- confirmed by `git diff` showing each `BuildCraft.java` reduced to exactly the one intended
    additive `BCTransportRegistries.register(modBus)` line -- and every verification step below (clean rebuild,
    tests, server boots, `runClient`) was re-run *after* that removal, against the final code. **Honest
    limitation**: the real right-click placement interaction itself, end to end through a real connected
    client's input, remains unverified, the same caveat already on file for every prior RCON-verified batch in
    this document -- what is verified directly is that `TilePipeHolder#onPlacedBy` itself dispatches correctly
    (constructs the right `Pipe`, registers the right event handlers, fires `PipeEventPlaced`), and that every
    other new class in this batch behaves correctly under real, un-coached, tick-driven gameplay once a pipe
    exists.
  - **Verified with forced `--no-build-cache clean` rebuilds on both platforms (before and after the connection-
    capability bug fix, and again after the debug command's removal), the full 25-test suite, real dedicated-
    server boots with zero exceptions on both targets (fresh worlds, deleted and rebooted after the debug
    command's removal), and a real `:neoforge-26x:runClient` boot that reached full texture-atlas stitching
    (including `textures/atlas/items.png-atlas`/`blocks.png-atlas`) and a loaded resource manager
    (`mod/buildcraft` included) with no missing-model/missing-sprite warnings for `pipe_holder`/
    `pipe_item_cobblestone` and no `IllegalStateException`/`ClassNotFoundError`/`NoClassDefFoundError`/
    `BootstrapMethodError` anywhere in the log.** Not independently verified: the on-screen appearance of the
    pipe block/item, any pipe connection shape, and the item-travelling-through-pipe visual -- no client
    rendering exists in this batch at all, per its own scope (see above), the same caveat already on file for
    every prior entry in this document.

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
| `buildcraft.core` | 84 | Gears (done), wrench, markers, engines, paintbrush (done), map location. |
| `buildcraft.transport` | 124 | Pipes. The largest single feature. Straight-run cobblestone item pipe (done). Every other material, colours, wires, gates, pluggables, fluid/power flow still to come. |
| `buildcraft.builders` | 121 | Quarry, builder, architect, filler, schematics. |
| `buildcraft.silicon` | 79 | Laser, assembly table, gates/wires. |
| `buildcraft.factory` | 46 | Chute, mining well + tube, pump, tank, flood gate, auto workbench (items half, done). Auto workbench (fluids half) still to come. |
| `buildcraft.energy` | 41 | Stirling engine (done). Iron/RF combustion engines, oil, fuel still to come. |
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
| Fluid viscosity (1.12.2's `Fluid#getViscosity()`) | `net.neoforged.neoforge.fluids.FluidType#getViscosity(FluidState, BlockAndLightGetter, BlockPos)`, reached via `fluid.getType().getFluidType()` | `net.minecraftforge.fluids.FluidType#getViscosity(FluidState, BlockAndTintGetter, BlockPos)`, same shape, reached the same way -- only the package and the `BlockGetter` supertype name differ |
| Fluid tank base class | none -- `net.neoforged.neoforge.fluids.FluidTank` doesn't exist (confirmed via `javap`: class not found); build on `net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler` instead, the fluid counterpart of `ItemHandlerSimple`'s own `ItemStacksResourceHandler` base | `net.minecraftforge.fluids.capability.templates.FluidTank` -- 1.12.2's own `net.minecraftforge.fluids.FluidTank`, moved under `capability.templates`, same `fill`/`drain`/`isFluidValid` surface |
| Vanilla-interop fluid capability | `Capabilities.Fluid.BLOCK` -- `BlockCapability<ResourceHandler<FluidResource>, Direction>`, NeoForge's own token, needs no BuildCraft-native counterpart since `Tank` already *is* a `ResourceHandler<FluidResource>` | `ForgeCapabilities.FLUID_HANDLER` -- `Capability<IFluidHandler>`, needs no BuildCraft-native counterpart either, since `Tank` already *is* an `IFluidHandler` by extending `FluidTank` |
| Draining a world fluid block (no capability-bearing block entity involved, e.g. a plain water/lava lake) | `BucketPickup#pickupBlock(LivingEntity, LevelAccessor, BlockPos, BlockState)` directly -- NeoForge's own generic `FluidUtil#tryPickupFluid` was rejected; its own doc admits it can mutate the world even when its `Transaction` is never committed | `BucketPickup#pickupBlock(LevelAccessor, BlockPos, BlockState)` directly (no `LivingEntity` parameter at all on this target) -- Forge's own `FluidUtil#getFluidHandler(Level, BlockPos, Direction)` was rejected too, confirmed via decompile to only resolve through a neighbouring `BlockEntity`, which a plain fluid lake never has |

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
- **On 26.x, `PayloadRegistrar#playBidirectional` has two overloads, and the 3-argument one silently drops
  the client-side handler.** `playBidirectional(type, codec, serverHandler, clientHandler)` (4-arg) registers
  both directions; `playBidirectional(type, codec, serverHandler)` (3-arg) registers **only** the server-bound
  handler and leaves the client handler `null` — per its own javadoc, the client side then has to be
  registered separately via `RegisterClientPayloadHandlersEvent`, which nothing in this port does. `BCNetwork`
  called the 3-arg overload for `MessageUpdateTile` (the generic tile-sync envelope, sent both ways), so the
  jar compiled and booted a dedicated server fine (server-to-server never needs the missing half) but crashed
  every real client on startup with `IllegalStateException: Some clientbound payloads are missing client-side
  handlers: [buildcraft:update_tile]`, thrown from `ClientModLoader.finish()` before the game window even
  opens. This is exactly the failure mode the "not verified: client-side rendering" caveat above was flagging
  as a gap — a dedicated-server boot alone cannot catch it. Found only once real jars were deployed to actual
  Prism Launcher instances and launched as a client; confirmed the fix (switching to the 4-arg overload,
  passing `MessageUpdateTile::handle` for both directions, since the handler already reads `ctx.player()`/
  `level()` generically rather than assuming a side) by running `:neoforge-26x:runClient` directly afterward —
  mod loading now completes, the integrated server starts, and a world loads and renders with no crash report.
  Worth checking for on any future bidirectional message: grep for `playBidirectional(` calls with exactly
  three arguments.

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
