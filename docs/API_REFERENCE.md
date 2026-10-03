# Economy API reference

This is a practical catalog of the supported addon-facing API. The canonical signatures remain the Java sources for the selected Economy version. Unless documented otherwise, only the top-level `com.nstut.economy.api` package is covered by the compatibility policy. The `com.nstut.economy.api.internal` subpackage is implementation detail and is not part of the supported addon surface.

Read [Getting Started](GETTING_STARTED.md) for integration setup and [Extending Economy](EXTENDING_ECONOMY.md) for extension contracts and persistence rules.

## Threading, handle lifetime, and synchronous callbacks

Call account-manager, order-book, storage, and world operations on the server thread. Thread-safe extension registries and internal account locks do not make those services or Minecraft world access safe for asynchronous mutation. Schedule asynchronous work back onto the server thread before using these services.

Raw account access is a trusted addon capability. Addons must check permissions before exposing credit, debit, transfers, or arbitrary account creation to players.

Built-in player/team account handles are retired when deleted or replaced by an account reload. A retired handle retains its last readable balance/history but rejects credit, debit, and transfers, including incoming transfers. Reacquire handles after deletion/recreation or reload. Built-in direct admin balance writes also reject retired handles. Server/tax handles are manager-owned and retained across reloads; do not retain service handles across server-runtime replacement.

New player accounts are installed before starting-balance events fire. A listener may look up the same handle or initialize a different account; the starting-credit attempt occurs once per creation. A pre-balance veto leaves the installed account at zero. Reentrant mutation of accounts participating in credit/debit/transfer is rejected. Deleting a participating account returns false; account reload during a mutation throws `IllegalStateException` before changing the loaded view.

Settlement guards both orders in an order-book match through payment, storage delivery/refunds, event callbacks, and quantity accounting. Cancellation/editing of participating orders returns false, and recursive execution fails, while that guard is held. To veto payment, cancel `EconomyEvents.TransferPre` or `BalanceChangePre`; retry order mutations after the settlement call returns. Unrelated accounts and orders can still be used on the server thread. Internal concrete `Order` setters and mutable escrow collections are not supported addon mutation APIs; use `IOrderManager`.

## Service entry point

### `EconomyApi`

Stable static facade for runtime services and extension registries.

- `boolean isReady()` — whether a running server has bound all runtime services.
- `IAccountManager accounts()` — active account service; throws if Economy is not ready.
- `IOrderManager orders()` — active order-book service; throws if Economy is not ready.
- `IMarketDataService marketData()` — active read-only market analytics service; throws if Economy is not ready.
- `CommodityTypeRegistry commodityTypes()` — process-level registry for commodity type handlers/codecs.
- `StorageProviderRegistry storage()` — process-level registry for market storage providers.
- `TeamEconomyRegistry teamEconomy()` — process-level optional team provider and shared-wallet policy.
- `Optional<ServerLevel> serverLevel()` — currently bound server overworld, when available.

Runtime binding/unbinding is owned entirely by `com.nstut.economy.api.internal`; the stable `EconomyApi` facade exposes read-only service access and lifecycle visibility only.

## Identifiers

### `EconomyId`

Minecraft-version-neutral namespaced identifier.

```java
EconomyId id = EconomyId.of("myaddon", "salary");
EconomyId parsed = EconomyId.parse("minecraft:iron_ingot");
```

- `of(namespace, path)` — constructs a validated ID.
- `parse(value)` — parses `namespace:path`; values without a namespace default to `minecraft`.
- `namespace()` / `path()` — record accessors.
- `compareTo(EconomyId)` — namespace-first, then path lexical ordering.
- `toString()` — canonical `namespace:path` representation.

Namespaces accept `[a-z0-9_.-]+`; paths accept `[a-z0-9/._-]+`. Use your own addon namespace for extension IDs.

### `MarketIdentity`

Typed attribution for market actions and orders:

- `AccountRef principal()` - economic owner whose balance is debited or credited.
- `UUID actor()` - player who placed or performed the action.
- `UUID storageOwner()` - persisted UUID projection of the physical storage owner.
- `AccountRef storageAccount()` - typed physical-storage principal used for Vault/Tank/provider lookup.

These values may intentionally differ for team orders. Authorization, self-trade checks, and economic auditing should use `principal()` (or the complete `MarketIdentity`) rather than comparing raw UUIDs. For new Team orders, `principal()` and `storageAccount()` are the same `TEAM:<uuid>` account while `actor()` remains the human player. The UUID constructor retains legacy PLAYER storage; use `new MarketIdentity(principal, actor, storageAccount)` for typed storage. Saves without `StorageAccount` migrate to PLAYER storage, including player/team UUID collisions.

### `CommodityKey`

Full stable market identity: commodity type plus commodity ID.

- `EconomyId commodityTypeId()`
- `EconomyId commodityId()`
- `static CommodityKey of(ICommodity commodity)`
- `boolean matches(ICommodity commodity)`

Use `CommodityKey` for analytics and persistent keys where two commodity types could expose the same commodity ID.

## Accounts

### `AccountRef` / `AccountKind`

Typed economic identity. `AccountKind` contains `PLAYER`, `TEAM`, `SERVER`, and `TAX`. The kind participates in identity, so `PLAYER:<uuid>` and `TEAM:<same uuid>` are distinct accounts.

### `IAccountManager`

Central account service.

- `Optional<IBankAccount> getAccount(AccountRef account)`
- `IBankAccount getOrCreateAccount(AccountRef account)`
- `Optional<IBankAccount> getTeamAccount(UUID team)`
- `IBankAccount getOrCreateTeamAccount(UUID team)`
- `boolean hasAccount(AccountRef account)`
- `boolean deleteAccount(AccountRef account)`
- `boolean transfer(AccountRef source, AccountRef target, BigDecimal amount, ITransactionContext context)`
- `Optional<IBankAccount> getPlayerAccount(UUID player)`
- `IBankAccount getOrCreatePlayerAccount(UUID player)`
- `boolean hasAccount(UUID player)`
- `IBankAccount getServerAccount()`
- `IBankAccount getTaxAccount()`
- `boolean deleteAccount(UUID player)`
- `boolean transfer(IBankAccount source, IBankAccount target, BigDecimal amount, ITransactionContext context)`
- `boolean transfer(UUID sourcePlayer, UUID targetPlayer, BigDecimal amount, ITransactionContext context)`

`IAccountManager.getInstance()` is deprecated. New code should use `EconomyApi.accounts()`.

Transfers must preserve atomicity: a failed/rejected target credit must not leave the source debited.

### `IBankAccount`

Virtual currency account.

- `UUID getOwner()` — compatibility raw UUID.
- `AccountRef getAccountRef()` — preferred typed identity; defaults to `PLAYER` for legacy third-party implementations.
- `BigDecimal getBalance()`
- `boolean credit(BigDecimal amount, ITransactionContext context)`
- `boolean debit(BigDecimal amount, ITransactionContext context)`
- `boolean transferTo(IBankAccount target, BigDecimal amount, ITransactionContext context)`
- `List<ITransactionRecord> getRecentTransactions(int count)`
- `boolean hasSufficientFunds(BigDecimal amount)`

New code should provide a non-null transaction context with a namespaced cause.

## Team economy

### `TeamEconomyProvider`

Neutral external-team bridge:

- `EconomyId providerId()` - stable namespaced identity for the external Team domain. This id is persisted with Team wallet lifecycle state and **must not change** for the same provider across restarts or addon versions. Different providers must use different ids.
- `Optional<TeamRef> resolveTeam(UUID playerId)`
- `Optional<TeamRef> getTeam(UUID teamId)`
- `TeamRole getRole(UUID playerId, UUID teamId)`
- `boolean isMember(UUID playerId, UUID teamId)`
- `Collection<UUID> getMembers(UUID teamId)` - complete current membership for deterministic Team-closure settlement. Providers that support Team wallets should override this and return every member, including the owner. The default returns an empty collection to mean enumeration is unsupported/unavailable; Economy then preserves the Team wallet and blocks closure settlement rather than guessing recipients.
- `boolean isTeamDeleted(UUID teamId)` - authoritative deletion signal; must return false on lookup/provider failure.
- `boolean isAvailable()`

Economy's built-in FTB Teams bridge is optional and maps only party teams. FTB personal teams and server teams are excluded. It is registered as an internal fallback: an addon-owned provider registered through `registerProvider(...)` takes precedence, and unregistering that custom provider exposes the FTB fallback again. FTB lifecycle events are ignored while a non-FTB provider is active.

`providerId()` and `getMembers(...)` are lifecycle-safety contracts, not cosmetic metadata. Economy persists provider provenance with each Team wallet. Only the currently active provider whose `providerId()` matches that persisted owner may authorize, refresh, delete, or settle the wallet. If the owning provider is missing and another provider (including the FTB fallback) becomes active, the wallet, Team storage, and durable Team orders remain preserved fail-closed. Pre-provenance development data is only claimed after a provider positively resolves the Team; a negative lookup never becomes a deletion. Equal-share disband settlement uses the last authoritative complete member snapshot. An empty/failed member enumeration never degrades to owner-only payout; settlement remains blocked until a valid snapshot exists.

### `TeamEconomyRegistry`

Reached through `EconomyApi.teamEconomy()`.

- `provider()` / `registerProvider(...)` / `unregisterProvider(...)` - one addon-owned provider may be active; Economy-owned fallback providers do not occupy that addon slot.
- `mode()` / `setMode(TeamEconomyMode)`
- `resolveTeam(UUID)`
- `teamPrincipal(UUID)`
- `defaultPrincipal(UUID)`
- `teamAccount(IAccountManager, UUID)`
- `walletSnapshot(IAccountManager, UUID)` — fresh server-authoritative Personal/Team balance, team identity, role, and permission state for UI/network sync.
- `roleFor(UUID player, UUID team)`
- `canView`, `canDeposit`, `canSpend`, `canPayout`, `canAdmin`
- `depositFromPlayer(...)` — permission-checked personal → team transfer.
- `spendFromTeam(...)` — permission-checked team → typed target transfer.
- configurable minimum `TeamRole` thresholds for those actions.

Server configuration uses `enabled=true` by default (optional FTB Teams support), or `enabled=false` to disable team economy. Without a provider, only personal accounts are exposed. The compatible Java `TeamEconomyMode` API retains `PERSONAL_ONLY`, `HYBRID`, and `TEAM_PRIMARY`; the server toggle maps to HYBRID/PERSONAL_ONLY. Authorization performs fresh provider lookups rather than caching membership/ranks.

See [Team Economy](TEAM_ECONOMY.md) for policy and FTB Teams mapping.

## Transaction context and history

### `ITransactionContext`

Describes why a balance operation occurred.

- `UUID getTransactionId()`
- `Instant getTimestamp()`
- `String getDescription()`
- `String getSource()`
- `EconomyId getCauseId()` — preferred stable cause identifier.
- `Map<String, String> getMetadata()` — immutable structured metadata.
- `TransactionType getType()` — deprecated legacy classification.

`TransactionType` contains `CREDIT`, `DEBIT`, `TRANSFER`, `TRADE`, `TAX`, `ADMIN_GIVE`, `ADMIN_TAKE`, `STARTING_BALANCE`, and `CUSTOM`.

### `TransactionContexts`

Public immutable context factories for addon code. Use these instead of implementation classes from `com.nstut.economy.core`.

- `transfer(String description, UUID actor)` - standard transfer context attributed to the acting player.
- `of(EconomyId causeId, String description, String source)` - context with a stable namespaced cause.
- `of(EconomyId causeId, String description, String source, Map<String,String> metadata)` - context with immutable structured metadata.

### `TransactionCauses`

Built-in cause IDs:

- `economy:credit`
- `economy:debit`
- `economy:transfer`
- `economy:trade`
- `economy:tax`
- `economy:admin_give`
- `economy:admin_take`
- `economy:starting_balance`
- `economy:custom`

Also provides `fromLegacy(TransactionType)` and `toLegacy(EconomyId)` for compatibility mapping. Addons should define their own cause IDs instead of extending the legacy enum.

### `ITransactionRecord`

Read-only view of a completed balance transaction.

- `UUID getTransactionId()`
- `Instant getTimestamp()`
- `ITransactionContext.TransactionType getType()` — deprecated legacy classification.
- `EconomyId getCauseId()` — preferred namespaced cause.
- `Map<String, String> getMetadata()` — immutable transaction metadata.
- `BigDecimal getAmount()`
- `BigDecimal getResultingBalance()`
- `AccountRef getCounterpartyRef()` - typed counterparty, or `null` for a standalone credit/debit. Legacy implementations default to `PLAYER`.
- `UUID getCounterparty()` - legacy UUID projection; use the typed accessor for authorization or auditing.
- `String getDescription()`

Use records returned by `IBankAccount#getRecentTransactions`; do not depend on concrete transaction record implementations.

## Events

### `EconomyEvents`

Loader-neutral synchronous event bus.

```java
EconomyEvents.Subscription sub = EconomyEvents.listen(
    EconomyEvents.TransferCompleted.class,
    event -> handle(event)
);
```

- `listen(Class<E>, Consumer<E>)` — register an exact event-class listener and receive a closeable subscription.
- `post(E)` — publishes synchronously; primarily used by Economy and public extension registries.
- `Subscription.close()` — unregister listener.

Listeners are matched by the event's exact runtime class; registering for a base event interface does not subscribe to every subtype. Retain the returned `Subscription` and call `close()` when your own listener should be removed. `EconomyEvents.clearListeners()` remains as a **deprecated 0.0.13 binary-compatibility shim** because it shipped in the stable top-level API; it clears listeners globally and must not be used by new addons. The implementation delegates to internal lifecycle/test machinery and the public symbol is reserved for removal only in a documented breaking API release.

Account events:

- `BalanceChangePre` — cancellable; `accountRef()`, legacy `owner()`, `previousBalance()`, `delta()`, `resultingBalance()`, `context()`.
- `BalanceChanged` - committed `accountRef()`, `previousBalance()`, `balance()`, `delta()`, and `context()`; `owner()` retains the legacy UUID projection.
- `TransferPre` - cancellable; `sourceRef()`, `targetRef()`, `amount()`, `context()`; `source()`/`target()` retain legacy UUID projections.
- `TransferCompleted` - committed `sourceRef()`, `targetRef()`, `amount()`, and `context()`; `source()`/`target()` retain legacy UUID projections.

All four balance/transfer events carry typed identities: `accountRef()` on balance events and `sourceRef()` / `targetRef()` on transfer events. UUID constructors still create `PLAYER` identities, and UUID accessors remain available. Listeners that authorize or audit mutations must use the typed accessors: `PLAYER:<uuid>` and `TEAM:<same uuid>` are distinct principals. Transaction history preserves the typed counterparty on both built-in transfer legs and on the outgoing leg of transfers to third-party accounts via `IBankAccount.getAccountRef()`.


### `MarketEvents`

Market event payloads published through `EconomyEvents`.

- `OrderCreatePre` - cancellable proposal with preferred typed `identity()`, plus `commodity()`, `type()`, `quantity()`, and `pricePerUnit()`; posted before Economy creates new provider escrow. `owner()` is only the legacy actor-UUID projection.
- `OrderCreated` - `order`, `requestedQuantity`, `filledQuantity`; inspect `order.getIdentity()` / `getPrincipal()` for team-aware attribution.
- `OrderEdited` - resulting `order`; inspect the typed order identity rather than `getOwner()` for authorization or auditing.
- `OrderCancelled` - `orderId`, preferred typed `identity()`; `owner()` is only the legacy actor-UUID projection.
- `TradeCompleted` - immutable `TradeView trade`.

For market authorization, self-trade detection, and audit attribution, addons should use `MarketIdentity` / `AccountRef`. Legacy `owner()` and `IOrder#getOwner()` values are UUID projections kept for source compatibility and are not sufficient to distinguish `PLAYER:<uuid>` from `TEAM:<uuid>` or to represent principal/actor/storage-owner separation.

Storage-provider registry changes are also events:

- `StorageProviderRegistry.StorageProviderRegistered` — `providerId`.
- `StorageProviderRegistry.StorageProviderUnregistered` — `providerId`.

Event delivery is synchronous; listeners should return quickly.

## Commodities

### `ICommodity`

Tradeable product contract.

- `EconomyId getId()` — stable product ID inside its commodity type.
- `CommodityType getType()` — broad legacy category.
- `EconomyId getTypeId()` — namespaced handler/codec type.
- `Component getDisplayName()`
- `BigDecimal getBasePrice()`
- `boolean hasDynamicPricing()`

Built-in type IDs:

- `ICommodity.ITEM_TYPE` = `economy:item`
- `ICommodity.FLUID_TYPE` = `economy:fluid`
- `ICommodity.ENERGY_TYPE` = `economy:energy`

`CommodityType` contains `ITEM`, `FLUID`, `ENERGY`, and `CUSTOM`.

The direct nested `ICommodity.IStorage` marker and the `canExtractFrom`, `canInsertInto`, `extractFrom`, and `insertInto` methods are legacy compatibility hooks. New storage integrations should use `IStorageProvider`.

### `ICommodityTypeHandler`

Behavior and persistence codec for a namespaced commodity type.

- `EconomyId id()`
- `int currentSchemaVersion()`
- `boolean supports(ICommodity commodity)`
- `CommodityPayload encode(ICommodity commodity)`
- `ICommodity decode(EconomyId commodityId, CommodityPayload payload)`
- `boolean fluidLike()` — optional display hint; defaults to `false`.

### `CommodityPayload`

Versioned immutable codec payload:

```java
new CommodityPayload(1, Map.of("key", "value"));
```

- `int version()` — must be at least 1.
- `Map<String, String> values()` — immutable map.
- `empty(version)` — convenience factory.

### `CommodityTypeRegistry`

Registry reached through `EconomyApi.commodityTypes()`.

- `register(ICommodityTypeHandler handler)`
- `boolean unregister(EconomyId id)`
- `Optional<ICommodityTypeHandler> handler(EconomyId id)`
- `ICommodityTypeHandler require(EconomyId id)`
- `ICommodityTypeHandler handlerFor(ICommodity commodity)`
- `CommodityPayload encode(ICommodity commodity)`
- `ICommodity decode(EconomyId typeId, EconomyId commodityId, int version, Map<String, String> values)`
- `boolean fluidLike(ICommodity commodity)`
- `List<ICommodityTypeHandler> handlers()`

Registration is namespaced and duplicate IDs owned by different handler instances are rejected. `handlers()` is returned in stable ID order.

## Orders

### `IOrderManager`

Supported order-book service.

Creation:

- `OrderCreateResult createBuyOrder(UUID owner, ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `OrderCreateResult createSellOrder(UUID owner, ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `OrderCreateResult createBuyOrder(MarketIdentity identity, ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `OrderCreateResult createSellOrder(MarketIdentity identity, ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `IOrder createServerBuyOrder(ICommodity commodity, int quantity, BigDecimal pricePerUnit)`
- `IOrder createServerSellOrder(ICommodity commodity, int quantity, BigDecimal pricePerUnit)`

Player order creation always returns an `OrderCreateResult`. Server-order creation uses the compatibility return shape and may return `null` when domain validation rejects creation, so addon callers must check the result before dereferencing it.

Queries:

- `Optional<? extends IOrder> getOrder(UUID orderId)`
- `List<? extends IOrder> getAllOrders()`
- `List<? extends IOrder> getOrders(ICommodity commodity)`
- `List<? extends IOrder> getPlayerOrders(UUID player)`
- `List<? extends IOrder> getBuyOrders(ICommodity commodity)`
- `List<? extends IOrder> getSellOrders(ICommodity commodity)`

Mutation:

- `boolean cancelOrder(UUID orderId, UUID requester)`
- `boolean editOrder(UUID orderId, UUID requester, int newQuantity, BigDecimal newPrice, boolean infinite)`

Use the manager for mutation rather than concrete order implementations.

### `OrderCreateResult`

Stable result of submitting a player order.

Fields:

- `Status status()` — `POSTED`, `PARTIALLY_FILLED`, `FILLED`, or `REJECTED`.
- `IOrder remainingOrder()` — nullable remaining book order.
- `int requestedQuantity()`
- `int filledQuantity()`
- `String errorKey()` — may be `null` for accepted results.
- `List<String> errorArgs()` — immutable; empty when no error arguments exist.

Helpers:

- `Optional<IOrder> order()`
- `boolean accepted()`

A fully filled accepted order has no `remainingOrder()` even though `accepted()` is true.

### `IOrder`

Read/operation contract for one order.

- `UUID getOrderId()`
- `UUID getOwner()` - legacy storage-owner UUID projection.
- `MarketIdentity getIdentity()` - preferred principal/actor/storage-owner identity.
- `AccountRef getPrincipal()`
- `UUID getActor()`
- `UUID getStorageOwner()` - legacy/persisted UUID projection.
- `AccountRef getStorageAccount()` - preferred typed physical-storage principal.
- `ICommodity getCommodity()`
- `int getQuantity()`
- `BigDecimal getPricePerUnit()`
- `BigDecimal getTotalPrice()`
- `OrderType getType()` — `BUY` or `SELL`.
- `Instant getCreatedAt()`
- `Instant getExpiresAt()`
- `boolean isValid()`
- `boolean canExecute(UUID trader)` - legacy Personal/server compatibility path.
- `boolean canExecute(MarketIdentity trader)` - typed principal-aware execution check.
- `TransactionResult execute(UUID trader, ServerLevel level)` / `execute(UUID trader)` - legacy compatibility paths.
- `TransactionResult execute(MarketIdentity trader, ServerLevel level)` / `execute(MarketIdentity trader)` - typed execution paths.

For team-aware code, `getIdentity()` is the canonical attribution tuple. `getPrincipal()` identifies the economic account, `getActor()` identifies the human player who placed the order, and `getStorageAccount()` identifies the typed storage principal used for goods. `getStorageOwner()` and `getOwner()` are UUID projections retained for persistence/source compatibility and must not be used as account-authorization keys.

`cancel` remains on the compatibility interface, but addon code should prefer `IOrderManager` for cancellation/editing so world resolution, current team authorization, escrow restoration, and order-book invariants stay centralized.

## Market data

### `IMarketDataService`

Read-only analytics surface. Commodity-specific queries require the full `CommodityKey` identity.

- `List<TradeView> recentTrades(int limit)`
- `List<TradeView> recentTrades(CommodityKey commodity, int limit)`
- `Optional<BigDecimal> lastTradePrice(CommodityKey commodity)`
- `long tradedVolume(CommodityKey commodity)`
- `int activeOrderCount(CommodityKey commodity)`

### `TradeView`

Immutable completed-trade view:

- `EconomyId commodityId()`
- `EconomyId commodityTypeId()`
- `BigDecimal pricePerUnit()`
- `int quantity()`
- `UUID buyer()` / `UUID seller()` - legacy actor UUID projections.
- `MarketIdentity buyerIdentity()` / `MarketIdentity sellerIdentity()` - persisted typed economic/human/storage attribution.
- `Instant timestamp()`

## Built-in Tank configuration

`config/economy-storage.properties` controls built-in storage behavior. `tankCapacity` is the internal capacity in mB for newly created Tanks (default `128000`, minimum `1000`); persisted Tanks retain their saved per-block capacity. `allowExternalAutomation` controls direct loader fluid-capability exposure. Built-in Tanks expose two inventory slots: input accepts fluid containers and output receives the resulting container, allowing stackable container workflows without replacing the input stack in place.

## Storage integration

### `IStorageProvider`

Owner-scoped market storage backend.

Identification:

- `EconomyId id()`
- `int priority()` — defaults to 0.
- `boolean supports(ICommodity commodity)`

Side-effect-free simulation:

- `int available(ServerLevel level, UUID owner, ICommodity commodity)` - legacy Personal-owner path.
- `int receivable(ServerLevel level, UUID owner, ICommodity commodity, int requestedAmount)` - legacy Personal-owner path.
- `int available(ServerLevel level, AccountRef owner, ICommodity commodity)` - typed owner path.
- `int receivable(ServerLevel level, AccountRef owner, ICommodity commodity, int requestedAmount)` - typed owner path.

Reservation lifecycle:

- `Optional<StorageReservation> reserve(ServerLevel level, UUID owner, ICommodity commodity, int amount)` - legacy Personal-owner path.
- `Optional<StorageReservation> reserve(ServerLevel level, AccountRef owner, ICommodity commodity, int amount)` - typed owner path.
- `StorageDeliveryResult deliverReserved(ServerLevel level, StorageReservation reservation, UUID receiver, int amount)` - legacy Personal receiver.
- `StorageDeliveryResult deliverReserved(ServerLevel level, StorageReservation reservation, AccountRef receiver, int amount)` - typed receiver.
- `boolean release(ServerLevel level, StorageReservation reservation)`

`reserve` is atomic: empty means no mutation. `deliverReserved` is one provider-owned transition and must return the exact remaining reservation after the actual delivery. `release` is all-or-nothing: `false` means the entire input reservation must still be treated as escrowed. Existing providers remain compatible because the typed defaults delegate `PLAYER` accounts to the UUID methods; a provider must override the typed methods to support `TEAM` storage.

Diagnostics:

- `String describe(ServerLevel level, UUID owner)` — defaults to the provider ID string.

See [Extending Economy](EXTENDING_ECONOMY.md) for the atomicity, exact-state, and losslessness requirements.

### `StorageReservation`

Durable opaque reservation persisted by Economy.

- `EconomyId providerId()`
- `EconomyId commodityId()`
- `int amount()` — positive.
- `String token()` — provider-owned durable token.
- `Map<String, String> metadata()` — small immutable optional metadata.
- `CompoundTag providerState()` — defensive copy of structured provider-owned durable state.

Use `providerState` for exact inventories/components or potentially large state. Do not pack arbitrary escrow into one Base64/SNBT metadata string.

### `StorageDeliveryResult`

Atomic result of a provider delivery.

- `int deliveredAmount()`
- `Optional<StorageReservation> remainingReservation()`
- `static unchanged(StorageReservation)`
- `static complete(int deliveredAmount)`
- `static partial(int deliveredAmount, StorageReservation remainingReservation)`
- `validateAgainst(StorageReservation before, int requestedAmount)` — returns the same result after validating request bounds, delivered amount, total accounting, provider identity, and commodity identity; invalid provider results throw instead of being silently accepted.

Economy expects:

```text
deliveredAmount + remainingReservation.amount = previousReservation.amount
```

The remainder must describe the exact state that still belongs to escrow, not a reconstruction from a delivered count.

### `StorageProviderRegistry`

Registry/dispatcher reached through `EconomyApi.storage()`.

- `register(IStorageProvider provider)`
- `boolean unregister(EconomyId id)`
- `Optional<IStorageProvider> provider(EconomyId id)`
- `List<IStorageProvider> providers()` — priority-descending, then ID.
- `int available(...)`
- `int receivable(...)`
- `Optional<StorageReservation> reserve(...)`
- `StorageDeliveryResult deliver(...)`
- `boolean release(...)`

Dispatcher semantics are intentionally asymmetric:

- `available(...)` returns the largest amount offered by any single supporting provider because one reservation is never split across providers.
- `reserve(...)` walks providers in priority order and succeeds only when one provider can atomically reserve the full requested amount.
- `receivable(...)` may aggregate receiving capacity across supporting providers, capped at the requested amount.
- `deliver(...)` and `release(...)` route strictly to the provider ID stored in the reservation. A missing provider therefore leaves delivery unchanged and makes release fail rather than guessing another backend.

Registration changes emit `StorageProviderRegistered` and `StorageProviderUnregistered` through `EconomyEvents`.

## Compatibility and lifecycle checklist

- Depend only on the top-level `com.nstut.economy.api` package for stable addon integration; never depend on `com.nstut.economy.api.internal`.
- Use `EconomyApi`, not singleton holders or concrete managers.
- Do not cache runtime service instances across server restarts.
- Register commodity handlers and storage providers once during mod initialization.
- Use namespaced `EconomyId` values owned by your addon.
- Use `CommodityKey` for commodity-specific market analytics.
- Prefer `getCauseId()` over deprecated transaction enums.
- Keep event listeners fast and close short-lived subscriptions.
- Make storage simulation side-effect free.
- Make reservation creation atomic and assume a reservation will never span multiple providers.
- Return exact provider-owned remainder state from every partial delivery.
- Make failed release perform zero externally visible mutation.
- Persist structured/large escrow in `providerState`, not one string.
- Keep old commodity payload schema decoders when released worlds may still contain them.
- Compile/run against the matching Minecraft and loader artifact.

### Team treasury and recovery compatibility

`TeamWalletSnapshot` exposes separate `canPayout()` and `payoutRole()` state for direct Team-to-other-player payments. The previous constructor and `personalOnly` factory remain available; legacy snapshots conservatively report no payout permission. Ordinary Team-to-Personal withdrawal is intentionally absent. Server mutations always check current permissions independently.

Economy persists its Team-closing member snapshot and per-recipient settlement progress as internal state. That persistence type lives under `com.nstut.economy.api.internal` and is deliberately outside the supported addon contract. Equal-share settlement remains retry-safe across crashes/restarts; Vault/Tank custody moves to the last recorded owner only after cash settlement completes.

`IOrderManager.preserveProviderReservation(MarketIdentity, ...)` retains typed recovery attribution. Its default supports Personal identities through the legacy UUID hook; custom order managers must override it for Team recovery. Storage-registry validation failures have no human actor argument, so their quarantine uses the storage owner's UUID as the attribution placeholder while retaining its explicit account kind. Recovery initiated from an order preserves the original human actor.
